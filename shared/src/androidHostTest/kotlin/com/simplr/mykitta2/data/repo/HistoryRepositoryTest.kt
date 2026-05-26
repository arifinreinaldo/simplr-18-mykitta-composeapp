package com.simplr.mykitta2.data.repo

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.russhwolf.settings.MapSettings
import com.simplr.mykitta2.core.error.AppError
import com.simplr.mykitta2.core.result.Outcome
import com.simplr.mykitta2.data.net.api.KtorCatalogApi
import com.simplr.mykitta2.data.prefs.SettingsCountryStore
import com.simplr.mykitta2.data.prefs.SettingsSessionStore
import com.simplr.mykitta2.domain.Country
import com.simplr.mykitta2.domain.Order
import com.simplr.mykitta2.domain.OrderStatus
import com.simplr.mykitta2.domain.Session
import com.simplr.mykitta2.shared.db.MyKittaDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
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
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Real SQLite + MockEngine. Tests in this file cover:
 *  - first refresh inserts page-0 rows
 *  - TTL hit short-circuits the network
 *  - TTL miss re-fetches and replaces
 *  - force=true bypasses TTL
 *  - loadMore appends without wiping
 *  - unknown InvStatus rows are dropped
 *  - network failure surfaces AppError.Http
 *  - request body uses the supervisor code from session + correct functionName
 *
 * Future tests append onto this class. This file ships the happy-path test
 * first; TTL/loadMore/filter/failure tests land in Tasks 8 and 9.
 */
class HistoryRepositoryTest {

    private val jsonHeaders =
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun pageBody(invNos: List<String>, status: String, hasMore: Int = 0): String {
        val rows = invNos.joinToString(",") { no ->
            """{"InvNo":"$no","InvDate":"2026-05-20","InvStatus":"$status","CustName":"Outlet $no","Total":100.0,"Currency":"PHP","ItemCount":1}"""
        }
        return """
            {"getObjectResult":{"errorData":{"code":0,"description":""},"hasMoreRecords":$hasMore,"objectData":[[$rows],[]]}}
        """.trimIndent()
    }

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
        handler: MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData,
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

    /**
     * Builds a [DefaultHistoryRepository] backed by a fresh in-memory DB.
     * Session and country stores are returned so callers inside [runTest] can
     * call the suspend [SettingsSessionStore.write] / [SettingsCountryStore.write]
     * before exercising the repository.
     */
    private fun repo(
        client: HttpClient,
        clock: FakeClock = FakeClock(),
    ): Triple<DefaultHistoryRepository, SettingsSessionStore, SettingsCountryStore> {
        val db = freshDb()
        val settings = MapSettings()
        val sessionStore = SettingsSessionStore(settings)
        val countryStore = SettingsCountryStore(settings)
        val r = DefaultHistoryRepository(
            catalogApi = KtorCatalogApi(client),
            database = db,
            sessionStore = sessionStore,
            countryStore = countryStore,
            now = clock.provider,
        )
        return Triple(r, sessionStore, countryStore)
    }

    // --- Happy path ---------------------------------------------------------

    @Test fun refresh_populatesCache_andObserveEmits() = runTest {
        val (client, callCount) = mockClient {
            respond(content = pageBody(listOf("INV-1", "INV-2"), "Waiting", hasMore = 1),
                    status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val (r, sessionStore, countryStore) = repo(client)
        sessionStore.write(Session(userName = "u", supervisorCode = "S-7", isSupervisor = true))
        countryStore.write(Country.PH)

        val outcome = r.refresh(OrderStatus.WAITING)

        assertIs<Outcome.Success<HasMore>>(outcome)
        assertEquals(true, outcome.value.hasMore)
        assertEquals(1, callCount())

        val rows = r.observe(OrderStatus.WAITING).first()
        assertEquals(2, rows.size)
        // selectByStatus orders by invDate DESC, invNo DESC. Since both have the same date,
        // INV-2 comes before INV-1.
        assertEquals("INV-2", rows[0].invNo)
        assertEquals(OrderStatus.WAITING, rows[0].status)
    }

    // --- TTL gate -----------------------------------------------------------

    @Test fun refresh_withinTtl_skipsNetwork() = runTest {
        val clock = FakeClock()
        val (client, callCount) = mockClient {
            respond(content = pageBody(listOf("INV-1"), "Waiting"),
                    status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val (r, sessionStore, countryStore) = repo(client, clock)
        sessionStore.write(Session(userName = "u", supervisorCode = "S-7", isSupervisor = true))
        countryStore.write(Country.PH)

        r.refresh(OrderStatus.WAITING)              // first hit populates cache
        clock.advance(1.minutes)                    // still within TTL (5 min default)
        val second = r.refresh(OrderStatus.WAITING) // should NOT hit network

        assertIs<Outcome.Success<HasMore>>(second)
        assertEquals(1, callCount(), "TTL hit must short-circuit the network")
    }

    @Test fun refresh_pastTtl_refetches() = runTest {
        val clock = FakeClock()
        val (client, callCount) = mockClient {
            respond(content = pageBody(listOf("INV-1"), "Waiting"),
                    status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val (r, sessionStore, countryStore) = repo(client, clock)
        sessionStore.write(Session(userName = "u", supervisorCode = "S-7", isSupervisor = true))
        countryStore.write(Country.PH)

        r.refresh(OrderStatus.WAITING)
        clock.advance(6.minutes)                    // past 5-min TTL
        r.refresh(OrderStatus.WAITING)

        assertEquals(2, callCount(), "stale cache must trigger a refetch")
    }

    @Test fun refresh_force_alwaysHitsNetwork() = runTest {
        val clock = FakeClock()
        val (client, callCount) = mockClient {
            respond(content = pageBody(listOf("INV-1"), "Waiting"),
                    status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val (r, sessionStore, countryStore) = repo(client, clock)
        sessionStore.write(Session(userName = "u", supervisorCode = "S-7", isSupervisor = true))
        countryStore.write(Country.PH)

        r.refresh(OrderStatus.WAITING)
        clock.advance(30.seconds)                   // way inside TTL
        r.refresh(OrderStatus.WAITING, force = true)

        assertEquals(2, callCount(), "force=true bypasses TTL")
    }

    @Test fun refresh_emptyCache_alwaysHitsNetwork_evenWithinTtl() = runTest {
        // Edge: TTL is irrelevant when there are zero rows; treat as a miss.
        val clock = FakeClock()
        val (client, callCount) = mockClient {
            respond(content = pageBody(emptyList(), "Waiting"),
                    status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val (r, sessionStore, countryStore) = repo(client, clock)
        sessionStore.write(Session(userName = "u", supervisorCode = "S-7", isSupervisor = true))
        countryStore.write(Country.PH)

        r.refresh(OrderStatus.WAITING)
        r.refresh(OrderStatus.WAITING)

        assertEquals(2, callCount())
    }
}
