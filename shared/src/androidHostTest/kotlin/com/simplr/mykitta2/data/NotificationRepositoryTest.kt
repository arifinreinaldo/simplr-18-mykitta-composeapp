package com.simplr.mykitta2.data

import com.russhwolf.settings.MapSettings
import com.simplr.mykitta2.core.result.Outcome
import com.simplr.mykitta2.data.net.api.KtorCatalogApi
import com.simplr.mykitta2.data.prefs.SettingsCountryStore
import com.simplr.mykitta2.data.prefs.SettingsSessionStore
import com.simplr.mykitta2.data.repo.DefaultNotificationRepository
import com.simplr.mykitta2.domain.Country
import com.simplr.mykitta2.domain.Session
import com.simplr.mykitta2.test.makeInMemoryDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NotificationRepositoryTest {

    private val jsonHeaders =
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    @Test fun refreshCount_success_updatesFlow_andReturnsCount() = runTest {
        val captured = mutableListOf<HttpRequestData>()
        val repo = harness { req ->
            captured += req
            respond(
                """{"getObjectResult":{"errorData":{"code":0,"description":""},
                    "hasMoreRecords":0,"objectData":[[{"count":7}]]}}""".trimIndent(),
                HttpStatusCode.OK, jsonHeaders,
            )
        }
        val outcome = repo.refreshCount()
        assertIs<Outcome.Success<Int>>(outcome)
        assertEquals(7, outcome.value)
        assertEquals(7, repo.unreadCount.value)
        assertTrue(captured.single().url.toString().endsWith("/User/GetObject"))
    }

    @Test fun refreshCount_failure_keepsFlow_unchanged() = runTest {
        val repo = harness { respondError(HttpStatusCode.InternalServerError) }
        val outcome = repo.refreshCount()
        assertIs<Outcome.Failure>(outcome)
        assertEquals(0, repo.unreadCount.value)  // never moved off the default
    }

    @Test fun loadPage_offset0_fullPage_returnsHasMoreTrue() = runTest {
        val twentyItems = buildItemsJson(count = 20, startId = 1)
        val repo = harness { respond(twentyItems, HttpStatusCode.OK, jsonHeaders) }
        val outcome = repo.loadPage(offset = 0)
        assertIs<Outcome.Success<com.simplr.mykitta2.data.repo.NotificationPage>>(outcome)
        val page = outcome.value
        assertEquals(20, page.items.size)
        assertTrue(page.hasMore)
        assertEquals(false, page.fromCache)
    }

    @Test fun loadPage_offset0_shortPage_returnsHasMoreFalse() = runTest {
        val sevenItems = buildItemsJson(count = 7, startId = 1)
        val repo = harness { respond(sevenItems, HttpStatusCode.OK, jsonHeaders) }
        val outcome = repo.loadPage(0)
        assertIs<Outcome.Success<com.simplr.mykitta2.data.repo.NotificationPage>>(outcome)
        assertEquals(7, outcome.value.items.size)
        assertEquals(false, outcome.value.hasMore)
    }

    @Test fun loadPage_ignoresServerHasMoreRecords() = runTest {
        // 5 items but server claims hasMoreRecords=1 — repo MUST NOT trust the server field
        val fiveButServerSaysMore = buildItemsJson(count = 5, startId = 1, hasMoreRecords = 1)
        val repo = harness { respond(fiveButServerSaysMore, HttpStatusCode.OK, jsonHeaders) }
        val outcome = repo.loadPage(0)
        assertIs<Outcome.Success<com.simplr.mykitta2.data.repo.NotificationPage>>(outcome)
        assertEquals(false, outcome.value.hasMore, "size<PAGE_SIZE must win over server's hasMoreRecords")
    }

    /**
     * Build a `User/GetObject` JSON envelope for `GetNotificationData` with
     * `count` rows. `startId` lets multi-page tests vary item IDs.
     * `hasMoreRecords` flag is included so the "ignores server" test can lie to us.
     */
    private fun buildItemsJson(count: Int, startId: Int, hasMoreRecords: Int = 0): String {
        val items = (0 until count).joinToString(",") { i ->
            val id = startId + i
            """{"Id":"N$id","Title":"T$id","Description":"D$id","Type":"Order",
               "Payload":"{}","IsRead":0,"CreatedAt":"2026-05-${10 + (i % 20)}T00:00:00Z"}"""
        }
        return """{"getObjectResult":{"errorData":{"code":0,"description":""},
            "hasMoreRecords":$hasMoreRecords,"objectData":[[$items]]}}""".trimIndent()
    }

    @Test fun loadPage_offset0_networkFails_withEmptyCache_propagatesFailure() = runTest {
        val repo = harness { respondError(HttpStatusCode.InternalServerError) }
        val outcome = repo.loadPage(0)
        assertIs<Outcome.Failure>(outcome)
    }

    @Test fun loadPage_offset0_networkFails_withCachedRows_returnsCache_withFromCacheTrue() = runTest {
        // Seed cache via a successful first call, then make the next call fail.
        val responses = mutableListOf<MockRequestHandler>(
            { respond(buildItemsJson(count = 3, startId = 100), HttpStatusCode.OK, jsonHeaders) },
            { respondError(HttpStatusCode.InternalServerError) },
        )
        val repo = harnessSequence(responses)

        val first = repo.loadPage(0)
        assertIs<Outcome.Success<com.simplr.mykitta2.data.repo.NotificationPage>>(first)

        val second = repo.loadPage(0)
        assertIs<Outcome.Success<com.simplr.mykitta2.data.repo.NotificationPage>>(second)
        assertEquals(3, second.value.items.size)
        assertEquals(true, second.value.fromCache)
        assertEquals(false, second.value.hasMore)
    }

    @Test fun loadPage_deepOffset_networkFails_doesNotFallBack() = runTest {
        val repo = harness { respondError(HttpStatusCode.InternalServerError) }
        val outcome = repo.loadPage(offset = 20)
        assertIs<Outcome.Failure>(outcome)
    }

    @Test fun loadPage_deepOffset_success_doesNotUpsertCache() = runTest {
        val sevenAtOffset20 = buildItemsJson(count = 7, startId = 50)
        val responses = mutableListOf<MockRequestHandler>(
            { respond(sevenAtOffset20, HttpStatusCode.OK, jsonHeaders) },
            { respondError(HttpStatusCode.InternalServerError) },
        )
        val repo = harnessSequence(responses)

        val first = repo.loadPage(offset = 20)   // success but deep — should NOT cache
        assertIs<Outcome.Success<com.simplr.mykitta2.data.repo.NotificationPage>>(first)

        // Try the offline fallback for offset=0 — cache must still be empty
        val second = repo.loadPage(offset = 0)
        assertIs<Outcome.Failure>(second)   // no cache to fall back on
    }

    /**
     * Build a repo wired to a MockEngine. Session + country are pre-seeded so
     * `baseUrl()` and `supervisorRequest()` resolve cleanly.
     */
    private suspend fun harness(handler: MockRequestHandler): DefaultNotificationRepository {
        val settings = MapSettings()
        val sessionStore = SettingsSessionStore(settings)
        val countryStore = SettingsCountryStore(settings)
        sessionStore.write(Session(userName = "u", supervisorCode = "S1", isSupervisor = true))
        countryStore.write(Country.PH)
        val client = HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) { json() }
        }
        return DefaultNotificationRepository(
            catalogApi = KtorCatalogApi(client),
            sessionStore = sessionStore,
            countryStore = countryStore,
            db = makeInMemoryDatabase(),
        )
    }

    /**
     * Harness that returns a different response per request, in sequence. Pops the
     * head off the list each call. Tests pass enough handlers to cover their calls.
     */
    private suspend fun harnessSequence(
        handlers: MutableList<MockRequestHandler>,
    ): DefaultNotificationRepository {
        val settings = MapSettings()
        val sessionStore = SettingsSessionStore(settings)
        val countryStore = SettingsCountryStore(settings)
        sessionStore.write(Session(userName = "u", supervisorCode = "S1", isSupervisor = true))
        countryStore.write(Country.PH)
        val client = HttpClient(MockEngine { req ->
            val next = handlers.removeAt(0)
            next(this, req)
        }) { install(ContentNegotiation) { json() } }
        return DefaultNotificationRepository(
            catalogApi = KtorCatalogApi(client),
            sessionStore = sessionStore,
            countryStore = countryStore,
            db = makeInMemoryDatabase(),
        )
    }
}
