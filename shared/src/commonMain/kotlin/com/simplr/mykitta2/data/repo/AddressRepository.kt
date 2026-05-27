package com.simplr.mykitta2.data.repo

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.simplr.mykitta2.core.env.BuildEnv
import com.simplr.mykitta2.core.error.ErrorMapper
import com.simplr.mykitta2.core.result.Outcome
import com.simplr.mykitta2.data.net.api.CatalogApi
import com.simplr.mykitta2.data.net.dto.AddressDto
import com.simplr.mykitta2.data.net.dto.AddressRequest
import com.simplr.mykitta2.data.net.dto.GetRequest
import com.simplr.mykitta2.data.prefs.CountryStore
import com.simplr.mykitta2.data.prefs.SessionStore
import com.simplr.mykitta2.domain.Address
import com.simplr.mykitta2.shared.db.Address as AddressRow
import com.simplr.mykitta2.shared.db.MyKittaDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime

/**
 * Shipment-address book — list (`GetShipmentAddress`) and write
 * (`User/AddAddress`). Single source of truth is the SQLDelight `Address`
 * table; the list screen observes via [observe], and pull-to-refresh +
 * post-save use [refresh].
 *
 * Cache TTL is 24h. Addresses change rarely, and any user-initiated edit
 * already triggers a force-refresh via [save] — so the only TTL-driven
 * refetch happens on cold start of the list screen after a day away.
 */
interface AddressRepository {
    /** Continuous stream of the user's saved addresses. Sorted with the
     *  local-default row first, then case-insensitive by name. */
    fun observe(): Flow<List<Address>>

    /**
     * Refresh the local cache from `GetShipmentAddress`. TTL-gated: if any
     * row exists and `now - oldestFetchedAt <= [ttl]`, skips the network.
     * [force] = true (pull-to-refresh, post-save) bypasses the gate.
     *
     * On a real network hit the cache is transactionally wiped-and-rewritten
     * — the server is source of truth for *address content*. The locally-
     * chosen default ([setAsDefault]) is re-applied after the wipe **iff
     * the previously-selected row still exists in the new server snapshot**;
     * otherwise the default is cleared.
     */
    suspend fun refresh(force: Boolean = false, ttl: Duration = DEFAULT_TTL): Outcome<Unit>

    /**
     * Insert or update an address via `User/AddAddress`. Empty
     * [AddressRequest.customerAddressId] = new row; populated = edit.
     * The backend returns only a status message, so we [refresh] after a
     * successful save to discover the server-assigned id.
     */
    suspend fun save(request: AddressRequest): Outcome<Unit>

    /**
     * Mark [customerAddressId] as the user's default shipment address.
     * Local-only — no network call (backend mechanism not surfaced in v1).
     * Atomically clears any prior default and sets the new one inside a
     * single SQLDelight transaction so the "exactly one default" invariant
     * always holds.
     */
    suspend fun setAsDefault(customerAddressId: String): Outcome<Unit>

    /** Synchronous cache lookup — used by the form screen to prefill an
     *  edit. `null` if the address isn't cached. */
    suspend fun findById(customerAddressId: String): Address?

    companion object {
        val DEFAULT_TTL: Duration = 24.hours
        const val PAGE_SIZE: Int = 100
        const val FALLBACK_USER: String = "M1"
    }
}

@OptIn(ExperimentalTime::class)
class DefaultAddressRepository(
    private val catalogApi: CatalogApi,
    private val database: MyKittaDatabase,
    private val sessionStore: SessionStore,
    private val countryStore: CountryStore,
    /** Injectable for tests; defaults to the real wall clock. */
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : AddressRepository {

    override fun observe(): Flow<List<Address>> =
        database.addressQueries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun refresh(force: Boolean, ttl: Duration): Outcome<Unit> = catching {
        if (!force && isFresh(ttl)) return@catching Unit
        val response = catalogApi.getShipmentAddresses(baseUrl(), listRequest())
        val dtos = response.getObjectResult.objectData.firstOrNull().orEmpty()
        val stamp = now()
        // Capture the locally-chosen default BEFORE the wipe so we can re-apply
        // it after upsert — but only if the row still exists in the new
        // server snapshot. Otherwise the default silently clears (the row was
        // deleted server-side; the user re-picks).
        // selectedId() returns Query<String> — single-column scalar, no row wrapper.
        val previousDefaultId: String? =
            database.addressQueries.selectedId().executeAsOneOrNull()
        database.addressQueries.transaction {
            database.addressQueries.deleteAll()
            dtos.forEachIndexed { index, dto ->
                val domain = dto.toAddress(index)
                database.addressQueries.upsert(
                    customerAddressId = domain.customerAddressId,
                    name = domain.name,
                    address1 = domain.address1,
                    address2 = domain.address2,
                    zipcode = domain.zipcode,
                    city = domain.city,
                    phone = domain.phone,
                    contact = domain.contact,
                    barangay = domain.barangay,
                    province = domain.province,
                    subdivision = domain.subdivision,
                    // isSelected is local-only state; new rows start at 0.
                    isSelected = 0L,
                    fetchedAt = stamp,
                )
            }
            if (previousDefaultId != null) {
                // Re-apply only if the row survived the refresh.
                val stillExists = database.addressQueries
                    .selectById(previousDefaultId)
                    .executeAsOneOrNull() != null
                if (stillExists) {
                    database.addressQueries.markAsDefault(previousDefaultId)
                }
            }
        }
    }

    override suspend fun setAsDefault(customerAddressId: String): Outcome<Unit> = catching {
        database.addressQueries.transaction {
            // Defensive: don't mark non-existent rows. A stale UI tap on a row
            // that's been refreshed away would otherwise leave the cache with
            // no rows marked but the table count > 0 (silent UX bug).
            val exists = database.addressQueries.selectById(customerAddressId)
                .executeAsOneOrNull() != null
            if (!exists) return@transaction
            database.addressQueries.clearAllDefaults()
            database.addressQueries.markAsDefault(customerAddressId)
        }
    }

    override suspend fun save(request: AddressRequest): Outcome<Unit> = try {
        catalogApi.saveAddress(baseUrl(), request)
        // Backend doesn't return the new id; re-list to discover it.
        refresh(force = true)
    } catch (t: Throwable) {
        if (t is CancellationException) throw t
        Outcome.Failure(ErrorMapper.from(t))
    }

    override suspend fun findById(customerAddressId: String): Address? =
        database.addressQueries.selectById(customerAddressId)
            .executeAsOneOrNull()
            ?.toDomain()

    // --- Internals ---------------------------------------------------------

    private suspend inline fun <T> catching(block: () -> T): Outcome<T> = try {
        Outcome.Success(block())
    } catch (t: Throwable) {
        if (t is CancellationException) throw t
        Outcome.Failure(ErrorMapper.from(t))
    }

    private fun isFresh(ttl: Duration): Boolean {
        val count = database.addressQueries.countAll().executeAsOne()
        if (count == 0L) return false
        val oldest = database.addressQueries.oldestFetchedAt().executeAsOne().MIN ?: return false
        val ageMillis = now() - oldest
        return ageMillis <= ttl.inWholeMilliseconds
    }

    private suspend fun baseUrl(): String =
        BuildEnv.baseUrlFor(countryStore.read() ?: com.simplr.mykitta2.domain.Country.PH)

    private suspend fun listRequest() = GetRequest(
        functionName = "GetShipmentAddress",
        offset = 0,
        recordsize = AddressRepository.PAGE_SIZE,
        search = "all",
        sort = "0",
        user = sessionStore.read()?.supervisorCode ?: AddressRepository.FALLBACK_USER,
    )

    private fun AddressRow.toDomain() = Address(
        customerAddressId = customerAddressId,
        name = name,
        address1 = address1,
        address2 = address2,
        zipcode = zipcode,
        city = city,
        phone = phone,
        contact = contact,
        barangay = barangay,
        province = province,
        subdivision = subdivision,
        isSelected = isSelected == 1L,
    )

    /**
     * Map a wire DTO onto the local domain row.
     *
     * A blank `CustomerAddressID` comes back for the user's "anchor" address
     * (a server-side default-filled row). It carries real data but no id — so
     * we synthesize a stable local key ([PRIMARY_KEY_PLACEHOLDER]). The form
     * translates this back to an empty string when calling `User/AddAddress`,
     * which is the wire sentinel for that anchor row.
     *
     * [index] disambiguates pathological responses that send multiple blank
     * ids — extras get bumped to `__primary__1__`, `__primary__2__`, … so the
     * PRIMARY KEY constraint doesn't collapse them in the cache.
     *
     * `isSelected` is **not** derived from the wire here — it's local-only
     * state ([setAsDefault]). The wire's `CustomerAddressID == "Default"`
     * marker is preserved as the id itself (so the form can edit that row),
     * but the star badge is driven entirely by the local flag.
     */
    private fun AddressDto.toAddress(index: Int): Address {
        val rawId = customerAddressId.orEmpty()
        val localId = when {
            rawId.isNotEmpty() -> rawId
            index == 0 -> PRIMARY_KEY_PLACEHOLDER
            else -> "${PRIMARY_KEY_PLACEHOLDER}${index}__"
        }
        return Address(
            customerAddressId = localId,
            name = name.orEmpty(),
            address1 = address1.orEmpty(),
            address2 = address2.orEmpty(),
            zipcode = zipcode.orEmpty(),
            city = city.orEmpty(),
            phone = phone.orEmpty(),
            contact = contact.orEmpty(),
            barangay = barangay.orEmpty(),
            province = province.orEmpty(),
            subdivision = subdivision.orEmpty(),
            isSelected = false,
        )
    }

    private companion object {
        /** Local-cache stand-in for a wire row whose `CustomerAddressID` is blank. */
        const val PRIMARY_KEY_PLACEHOLDER = "__primary__"
    }
}
