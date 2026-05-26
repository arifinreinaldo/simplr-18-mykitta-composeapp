package com.simplr.mykitta2.data.repo

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.simplr.mykitta2.core.env.BuildEnv
import com.simplr.mykitta2.core.error.ErrorMapper
import com.simplr.mykitta2.core.result.Outcome
import com.simplr.mykitta2.data.net.api.CatalogApi
import com.simplr.mykitta2.data.net.dto.GetRequest
import com.simplr.mykitta2.data.prefs.CountryStore
import com.simplr.mykitta2.data.prefs.SessionStore
import com.simplr.mykitta2.domain.Country
import com.simplr.mykitta2.domain.Order
import com.simplr.mykitta2.domain.OrderStatus
import com.simplr.mykitta2.shared.db.History as HistoryRow
import com.simplr.mykitta2.shared.db.MyKittaDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

interface HistoryRepository {
    /** Continuous stream of cached rows for [status]. UI subscribes once and
     *  reacts to every upsert/delete. Survives across screen rotations. */
    fun observe(status: OrderStatus): Flow<List<Order>>

    /**
     * Page-0 refresh. TTL-gated: if cached rows exist and
     * `now - oldestFetchedAt <= [ttl]`, skips the network entirely and returns
     * `HasMore(true)` (we can't tell from the cache whether more pages exist;
     * pagination stays available — `loadMore` discovers the truth).
     *
     * When [force] is true (pull-to-refresh), the TTL gate is bypassed.
     *
     * On a real network hit the cache for that status is transactionally
     * wiped-and-rewritten with `fetchedAt = now`.
     */
    suspend fun refresh(
        status: OrderStatus,
        force: Boolean = false,
        ttl: Duration = DEFAULT_TTL,
    ): Outcome<HasMore>

    /**
     * Append next page. Caller passes the current row count for this status as
     * the offset. Rows are upserted (not delete-then-insert) so prior pages
     * remain. `fetchedAt` is stamped on every new row.
     */
    suspend fun loadMore(status: OrderStatus, currentCount: Int): Outcome<HasMore>

    companion object {
        val DEFAULT_TTL: Duration = 5.minutes
        const val PAGE_SIZE: Int = 15
        const val FALLBACK_USER: String = "M1"
    }
}

data class HasMore(val hasMore: Boolean)

@OptIn(ExperimentalTime::class)
class DefaultHistoryRepository(
    private val catalogApi: CatalogApi,
    private val database: MyKittaDatabase,
    private val sessionStore: SessionStore,
    private val countryStore: CountryStore,
    /** Injectable for tests; defaults to the real wall clock. */
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : HistoryRepository {

    // --- Public surface -----------------------------------------------------

    override fun observe(status: OrderStatus): Flow<List<Order>> =
        database.historyQueries.selectByStatus(status.wire)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.mapNotNull { it.toDomain() } }

    override suspend fun refresh(
        status: OrderStatus,
        force: Boolean,
        ttl: Duration,
    ): Outcome<HasMore> = catching {
        if (!force && isFresh(status, ttl)) {
            // Cache fresh; skip network. Pagination stays available — `loadMore`
            // is the only way to learn whether more rows exist on the server.
            return@catching HasMore(hasMore = true)
        }
        val country = countryStore.read() ?: Country.PH
        val response = catalogApi.getHistory(
            baseUrl = BuildEnv.baseUrlFor(country),
            request = historyRequest(status, offset = 0),
        )
        val rows = response.rows(currency = currencyFor(country))
        database.historyQueries.transaction {
            database.historyQueries.deleteByStatus(status.wire)
            insertAll(rows, status, now())
        }
        HasMore(hasMore = response.hasMore())
    }

    override suspend fun loadMore(
        status: OrderStatus,
        currentCount: Int,
    ): Outcome<HasMore> = catching {
        val country = countryStore.read() ?: Country.PH
        val response = catalogApi.getHistory(
            baseUrl = BuildEnv.baseUrlFor(country),
            request = historyRequest(status, offset = currentCount),
        )
        val rows = response.rows(currency = currencyFor(country))
        database.historyQueries.transaction {
            insertAll(rows, status, now())
        }
        HasMore(hasMore = response.hasMore())
    }

    // --- Internals ---------------------------------------------------------

    private suspend inline fun <T> catching(block: () -> T): Outcome<T> = try {
        Outcome.Success(block())
    } catch (t: Throwable) {
        if (t is CancellationException) throw t
        Outcome.Failure(ErrorMapper.from(t))
    }

    private fun isFresh(status: OrderStatus, ttl: Duration): Boolean {
        val count = database.historyQueries.countByStatus(status.wire).executeAsOne()
        if (count == 0L) return false
        // SQLDelight wraps `SELECT MIN(...)` as a nullable column on a generated
        // row type. The column is named `MIN` (the SQL function); resolve via
        // `executeAsOne().MIN` — null when the table is empty (handled above).
        val oldest = database.historyQueries.oldestFetchedAtByStatus(status.wire)
            .executeAsOne()
            .MIN ?: return false
        val ageMillis = now() - oldest
        return ageMillis <= ttl.inWholeMilliseconds
    }

    private fun insertAll(rows: List<Order>, status: OrderStatus, stamp: Long) {
        rows.forEach { domain ->
            // Defensive: backend should already filter by the requested status,
            // but drop any row that doesn't match the tab we fetched for.
            if (domain.status != status) return@forEach
            database.historyQueries.upsert(
                invNo = domain.invNo,
                invDate = domain.invDate,
                status = status.wire,
                principalName = domain.principalName,
                total = domain.total,
                currency = domain.currency,
                itemCount = domain.itemCount.toLong(),
                firstProductName = domain.firstProduct?.name ?: "",
                firstProductImageUrl = domain.firstProduct?.imageUrl ?: "",
                firstProductQty = (domain.firstProduct?.qty ?: 0).toLong(),
                fetchedAt = stamp,
            )
        }
    }

    private fun currencyFor(country: Country): String = when (country) {
        Country.PH -> "PHP"
        Country.SG -> "SGD"
    }

    private suspend fun historyRequest(status: OrderStatus, offset: Int) = GetRequest(
        functionName = "GetHistory",
        offset = offset,
        recordsize = HistoryRepository.PAGE_SIZE,
        // Single-status filter. Legacy `getHistoryList` joins List<Pair> with
        // ",", but we only need one filter here.
        search = "status=${status.wire}",
        sort = "0",
        user = sessionStore.read()?.supervisorCode ?: HistoryRepository.FALLBACK_USER,
    )

    private fun HistoryRow.toDomain(): Order? {
        val parsed = OrderStatus.fromWire(status) ?: return null
        val preview = if (firstProductName.isNotEmpty()) {
            com.simplr.mykitta2.domain.OrderItemPreview(
                name = firstProductName,
                imageUrl = firstProductImageUrl,
                qty = firstProductQty.toInt(),
            )
        } else null
        return Order(
            invNo = invNo,
            invDate = invDate,
            status = parsed,
            principalName = principalName,
            total = total,
            currency = currency,
            itemCount = itemCount.toInt(),
            firstProduct = preview,
        )
    }
}
