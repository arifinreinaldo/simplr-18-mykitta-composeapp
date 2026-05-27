package com.simplr.mykitta2.data.repo

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.russhwolf.settings.MapSettings
import com.simplr.mykitta2.core.error.AppError
import com.simplr.mykitta2.core.result.Outcome
import com.simplr.mykitta2.data.net.api.KtorCatalogApi
import com.simplr.mykitta2.data.net.dto.AddressRequest
import com.simplr.mykitta2.data.prefs.SettingsCountryStore
import com.simplr.mykitta2.data.prefs.SettingsSessionStore
import com.simplr.mykitta2.domain.Country
import com.simplr.mykitta2.domain.Session
import com.simplr.mykitta2.shared.db.MyKittaDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Real SQLite + MockEngine coverage for the shipment-address book.
 *
 * Mirrors HistoryRepositoryTest's structure: in-memory JDBC SQLite, MapSettings
 * for session/country, MockEngine for the Ktor client. The repository's
 * 24h TTL is verified at the boundaries; force=true bypass is verified too.
 */
class AddressRepositoryTest {

    private val jsonHeaders =
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    /** PascalCase wire format per live backend. The default address is signalled
     *  by `CustomerAddressID == "Default"`, not a separate boolean. */
    private fun listBody(ids: List<String>): String {
        val rows = ids.joinToString(",") { id ->
            """{"CustNo":"X","CustomerAddressID":"$id","Name":"Home-$id","Address1":"1 St","Address2":"","Zipcode":"1000","City":"Manila","Phone":"0900","Contact":"Juan","Barangay":"","Province":"","Subdivision":""}"""
        }
        return """{"getObjectResult":{"errorData":{"code":0,"description":""},"hasMoreRecords":0,"objectData":[[$rows]]}}"""
    }

    private fun messageBody(): String =
        """{"resultCode":0,"resultMsg":"ok"}"""

    private fun freshDb(): MyKittaDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MyKittaDatabase.Schema.create(driver)
        return MyKittaDatabase(driver)
    }

    private class FakeClock(var nowMillis: Long = 1_000_000L) {
        fun advance(d: Duration) { nowMillis += d.inWholeMilliseconds }
        val provider: () -> Long get() = { nowMillis }
    }

    private fun mockClient(
        handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): Pair<HttpClient, () -> Int> {
        var calls = 0
        val client = HttpClient(MockEngine { request ->
            calls += 1
            handler(this, request)
        }) {
            expectSuccess = true
            install(ContentNegotiation) { json() }
        }
        return client to { calls }
    }

    private suspend fun repo(
        client: HttpClient,
        clock: FakeClock = FakeClock(),
    ): DefaultAddressRepository {
        val db = freshDb()
        val settings = MapSettings()
        val sessionStore = SettingsSessionStore(settings)
        val countryStore = SettingsCountryStore(settings)
        sessionStore.write(Session(userName = "u", supervisorCode = "S-7", isSupervisor = true))
        countryStore.write(Country.PH)
        return DefaultAddressRepository(
            catalogApi = KtorCatalogApi(client),
            database = db,
            sessionStore = sessionStore,
            countryStore = countryStore,
            now = clock.provider,
        )
    }

    // --- Refresh ------------------------------------------------------------

    @Test fun refresh_populatesCache_andObserveEmits() = runTest {
        val (client, callCount) = mockClient {
            respond(content = listBody(listOf("A-1", "A-2")),
                    status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val r = repo(client)

        val outcome = r.refresh()

        assertIs<Outcome.Success<Unit>>(outcome)
        assertEquals(1, callCount())
        val rows = r.observe().first()
        assertEquals(2, rows.size)
        // isSelected is local-only and starts cleared — no row from a fresh
        // refresh is marked default. The user explicitly taps to pick one.
        assertEquals(false, rows.any { it.isSelected })
    }

    @Test fun refresh_doesNotInferDefaultFromWire_DefaultSentinel() = runTest {
        // Backend's CustomerAddressID="Default" id used to drive isSelected;
        // now it doesn't — the flag is purely local. The literal id is
        // preserved as-is so the form can still edit that row.
        val (client, _) = mockClient {
            respond(content = listBody(listOf("Default", "A-2")),
                    status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val r = repo(client)
        r.refresh()

        val rows = r.observe().first()
        assertTrue(rows.none { it.isSelected }, "wire sentinel must not auto-mark default")
        assertTrue(rows.any { it.customerAddressId == "Default" })
    }

    @Test fun refresh_blankIdRow_isCachedUnderSyntheticPrimaryKey() = runTest {
        // Live backend returns a row with `CustomerAddressID = ""` as the
        // user's anchor address. We must NOT drop it (legacy parity); instead
        // it's persisted under a synthetic key so the SQLDelight PRIMARY KEY
        // constraint accepts it.
        val (client, _) = mockClient {
            respond(content = listBody(listOf("", "Default")),
                    status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val r = repo(client)

        r.refresh()

        val rows = r.observe().first().map { it.customerAddressId }.toSet()
        // 2 rows in, 2 rows out — anchor wasn't dropped.
        assertEquals(2, rows.size)
        assertTrue(rows.contains("Default"))
        assertTrue(rows.any { it.startsWith("__primary__") })
    }

    @Test fun refresh_withinTtl_skipsNetwork() = runTest {
        val clock = FakeClock()
        val (client, callCount) = mockClient {
            respond(content = listBody(listOf("A-1")), status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val r = repo(client, clock)

        r.refresh()
        clock.advance(1.hours)
        val second = r.refresh()

        assertIs<Outcome.Success<Unit>>(second)
        assertEquals(1, callCount(), "24h TTL must short-circuit the network")
    }

    @Test fun refresh_pastTtl_refetches() = runTest {
        val clock = FakeClock()
        val (client, callCount) = mockClient {
            respond(content = listBody(listOf("A-1")), status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val r = repo(client, clock)

        r.refresh()
        clock.advance(25.hours)
        r.refresh()

        assertEquals(2, callCount(), "stale cache must trigger a refetch")
    }

    @Test fun refresh_force_bypassesTtl() = runTest {
        val clock = FakeClock()
        val (client, callCount) = mockClient {
            respond(content = listBody(listOf("A-1")), status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val r = repo(client, clock)

        r.refresh()
        clock.advance(1.minutes)
        r.refresh(force = true)

        assertEquals(2, callCount(), "force=true must bypass TTL")
    }

    @Test fun refresh_replacesCacheTransactionally() = runTest {
        // Server is source of truth; an empty server response wipes the local cache.
        val responses = ArrayDeque(listOf(
            listBody(listOf("A-1", "A-2")),
            listBody(emptyList()),
        ))
        val (client, _) = mockClient {
            respond(content = responses.removeFirst(),
                    status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val r = repo(client)

        r.refresh()
        assertEquals(2, r.observe().first().size)

        r.refresh(force = true)
        assertEquals(0, r.observe().first().size)
    }

    // --- setAsDefault (local-only) ----------------------------------------

    @Test fun setAsDefault_marksOnlyTheChosenRow() = runTest {
        val (client, _) = mockClient {
            respond(content = listBody(listOf("A-1", "A-2", "A-3")),
                    status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val r = repo(client)
        r.refresh()

        val outcome = r.setAsDefault("A-2")

        assertIs<Outcome.Success<Unit>>(outcome)
        val rows = r.observe().first()
        assertEquals(1, rows.count { it.isSelected }, "exactly one row marked default")
        assertEquals("A-2", rows.first { it.isSelected }.customerAddressId)
    }

    @Test fun setAsDefault_repointsExistingDefault() = runTest {
        val (client, _) = mockClient {
            respond(content = listBody(listOf("A-1", "A-2")),
                    status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val r = repo(client)
        r.refresh()
        r.setAsDefault("A-1")
        assertEquals("A-1", r.observe().first().first { it.isSelected }.customerAddressId)

        r.setAsDefault("A-2")

        val rows = r.observe().first()
        assertEquals(1, rows.count { it.isSelected })
        assertEquals("A-2", rows.first { it.isSelected }.customerAddressId)
    }

    @Test fun setAsDefault_unknownId_isNoOp() = runTest {
        // Defensive: a stale UI tap on a row that got refreshed away must
        // not leave the cache in a no-default state if there was a prior one.
        val (client, _) = mockClient {
            respond(content = listBody(listOf("A-1")),
                    status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val r = repo(client)
        r.refresh()
        r.setAsDefault("A-1")

        r.setAsDefault("MISSING")

        val rows = r.observe().first()
        assertEquals(1, rows.count { it.isSelected })
        assertEquals("A-1", rows.first { it.isSelected }.customerAddressId)
    }

    @Test fun refresh_preservesLocalDefault_whenRowStillExists() = runTest {
        val responses = ArrayDeque(listOf(
            listBody(listOf("A-1", "A-2")),
            listBody(listOf("A-1", "A-2", "A-3")),  // same plus one new
        ))
        val (client, _) = mockClient {
            respond(content = responses.removeFirst(),
                    status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val r = repo(client)
        r.refresh()
        r.setAsDefault("A-2")

        r.refresh(force = true)

        val rows = r.observe().first()
        assertEquals(3, rows.size)
        assertEquals("A-2", rows.first { it.isSelected }.customerAddressId,
            "local default must survive a force-refresh when the row still exists")
    }

    @Test fun refresh_clearsLocalDefault_whenRowDisappears() = runTest {
        val responses = ArrayDeque(listOf(
            listBody(listOf("A-1", "A-2")),
            listBody(listOf("A-1")),  // A-2 deleted server-side
        ))
        val (client, _) = mockClient {
            respond(content = responses.removeFirst(),
                    status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val r = repo(client)
        r.refresh()
        r.setAsDefault("A-2")

        r.refresh(force = true)

        val rows = r.observe().first()
        assertEquals(1, rows.size)
        assertEquals(false, rows.any { it.isSelected },
            "default must clear when the chosen row vanishes from the server snapshot")
    }

    @Test fun refresh_networkFailure_returnsAppErrorHttp() = runTest {
        val client = HttpClient(MockEngine { respondError(HttpStatusCode.InternalServerError) }) {
            expectSuccess = true
            install(ContentNegotiation) { json() }
        }
        val r = repo(client)

        val outcome = r.refresh()

        assertIs<Outcome.Failure>(outcome)
        assertIs<AppError.Http>(outcome.error)
        assertEquals(500, (outcome.error as AppError.Http).status)
    }

    // --- save (create / edit) ----------------------------------------------

    @Test fun save_callsAddAddress_thenForceRefreshes() = runTest {
        val paths = mutableListOf<String>()
        val responses = ArrayDeque(listOf(
            messageBody(),                          // POST AddAddress
            listBody(listOf("A-NEW")),              // refresh that follows
        ))
        val client = HttpClient(MockEngine { req ->
            paths += req.url.encodedPath
            respond(content = responses.removeFirst(),
                    status = HttpStatusCode.OK, headers = jsonHeaders)
        }) {
            expectSuccess = true
            install(ContentNegotiation) { json() }
        }
        val r = repo(client)

        val outcome = r.save(AddressRequest(
            customerAddressId = "",
            name = "Home", address1 = "1 St", address2 = "", zipcode = "1000",
            city = "Manila", phone = "0900", contact = "Juan",
            barangay = "", province = "", subdivision = "",
        ))

        assertIs<Outcome.Success<Unit>>(outcome)
        assertEquals(2, paths.size, "save must POST AddAddress then GET-list to discover the id")
        assertTrue(paths[0].endsWith("/User/AddAddress"))
        assertTrue(paths[1].endsWith("/User/GetObject"))
        val rows = r.observe().first()
        assertEquals(1, rows.size)
        assertEquals("A-NEW", rows[0].customerAddressId)
    }

    @Test fun save_networkFailure_returnsFailure_noLocalWrite() = runTest {
        val client = HttpClient(MockEngine { respondError(HttpStatusCode.BadRequest) }) {
            expectSuccess = true
            install(ContentNegotiation) { json() }
        }
        val r = repo(client)

        val outcome = r.save(AddressRequest(
            customerAddressId = "",
            name = "x", address1 = "x", address2 = "", zipcode = "x",
            city = "x", phone = "1234567", contact = "x",
            barangay = "", province = "", subdivision = "",
        ))

        assertIs<Outcome.Failure>(outcome)
        assertEquals(0, r.observe().first().size, "no local write on failure")
    }

    // --- findById ----------------------------------------------------------

    @Test fun findById_returnsCachedRow() = runTest {
        val (client, _) = mockClient {
            respond(content = listBody(listOf("A-1", "A-2")),
                    status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val r = repo(client)
        r.refresh()

        val found = r.findById("A-2")
        assertNotNull(found)
        assertEquals("A-2", found.customerAddressId)

        assertNull(r.findById("missing"))
    }

    // --- Request envelope --------------------------------------------------

    @Test fun listRequest_usesSupervisorAndFunctionName() = runTest {
        val sent = mutableListOf<String>()
        val client = HttpClient(MockEngine { req ->
            sent += (req.body as TextContent).text
            respond(content = listBody(emptyList()),
                    status = HttpStatusCode.OK, headers = jsonHeaders)
        }) {
            expectSuccess = true
            install(ContentNegotiation) { json() }
        }
        val r = repo(client)

        r.refresh()

        val body = sent.single()
        assertTrue(body.contains("\"functionName\":\"GetShipmentAddress\""), "wrong functionName: $body")
        assertTrue(body.contains("\"user\":\"S-7\""), "supervisorCode not propagated: $body")
    }
}
