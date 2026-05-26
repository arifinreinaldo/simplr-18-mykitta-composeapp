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
}
