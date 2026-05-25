package com.simplr.mykitta2.data

import com.russhwolf.settings.MapSettings
import com.simplr.mykitta2.core.error.AppError
import com.simplr.mykitta2.core.result.Outcome
import com.simplr.mykitta2.data.net.api.KtorCatalogApi
import com.simplr.mykitta2.data.prefs.SettingsCountryStore
import com.simplr.mykitta2.data.prefs.SettingsSessionStore
import com.simplr.mykitta2.data.repo.DefaultHomeRepository
import com.simplr.mykitta2.domain.Banner
import com.simplr.mykitta2.domain.CategoryRail
import com.simplr.mykitta2.domain.Country
import com.simplr.mykitta2.domain.Item
import com.simplr.mykitta2.domain.Session
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
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HomeRepositoryTest {

    private val jsonHeaders =
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private val emptyBanners = """
        {"getObjectResult":{"errorData":{"code":0,"description":""},"hasMoreRecords":0,"objectData":[[]]}}
    """.trimIndent()

    private val twoRails = """
        {"getObjectResult":{"errorData":{"code":0,"description":""},"hasMoreRecords":0,"objectData":[[
          {"SystemValue":"GetMostBuy","Description":"Most Buy","DisplayNo":2},
          {"SystemValue":"GetLastOrder","Description":"Last Buy","DisplayNo":1}
        ]]}}
    """.trimIndent()

    private val twoItems = """
        {"getObjectResult":{"errorData":{"code":0,"description":""},"hasMoreRecords":0,"objectData":[[
          {"productId":"P1","productDesc":"Soap","productLong":"","principalId":"1",
           "productUrl":"","unitPrice":"1","basicPrice":"1","baseUOM":"PCS","salesUOM":"PCS","InvQty":5},
          {"productId":"P2","productDesc":"Tea","productLong":"","principalId":"1",
           "productUrl":"","unitPrice":"2","basicPrice":"2","baseUOM":"PCS","salesUOM":"PCS","InvQty":0}
        ]]}}
    """.trimIndent()

    private val notifCount7 = """
        {"getObjectResult":{"errorData":{"code":0,"description":""},"hasMoreRecords":0,"objectData":[[{"count":7}]]}}
    """.trimIndent()

    private data class Harness(
        val repo: DefaultHomeRepository,
        val captured: List<HttpRequestData>,
    )

    /**
     * Suspend builder so the MapSettings-backed stores can be primed via the
     * real `suspend write(...)` surface. Called from inside `runTest{}` so the
     * test dispatcher is in scope; multiplatform-safe (no `runBlocking`).
     */
    private suspend fun harness(
        session: Session? = Session(userName = "u", supervisorCode = "S1", isSupervisor = true),
        country: Country? = Country.PH,
        handler: MockRequestHandler,
    ): Harness {
        val settings = MapSettings()
        val sessionStore = SettingsSessionStore(settings)
        val countryStore = SettingsCountryStore(settings)
        if (session != null) sessionStore.write(session)
        if (country != null) countryStore.write(country)
        val captured = mutableListOf<HttpRequestData>()
        val client = HttpClient(MockEngine { request ->
            captured += request
            handler(this, request)
        }) {
            expectSuccess = true
            install(ContentNegotiation) { json() }
        }
        val repo = DefaultHomeRepository(KtorCatalogApi(client), sessionStore, countryStore)
        return Harness(repo, captured)
    }

    private fun bodyAsString(request: HttpRequestData): String =
        (request.body as TextContent).text

    // ---- loadBanners ----

    @Test fun loadBanners_postsToUserGetObjectWithGetBannerFunction() = runTest {
        val (repo, captured) = harness { respond(emptyBanners, HttpStatusCode.OK, jsonHeaders) }
        val outcome = repo.loadBanners()
        assertIs<Outcome.Success<*>>(outcome)

        val sent = captured.single()
        assertTrue(sent.url.encodedPath.endsWith("User/GetObject"), "path: ${sent.url.encodedPath}")
        val body = bodyAsString(sent)
        assertTrue(body.contains("\"functionName\":\"GetBanner\""), body)
        assertTrue(body.contains("\"user\":\"S1\""), body)
        assertTrue(body.contains("\"recordsize\":15"), body)
    }

    @Test fun loadBanners_fallsBackToM1WhenNoSession() = runTest {
        val (repo, captured) = harness(session = null) {
            respond(emptyBanners, HttpStatusCode.OK, jsonHeaders)
        }
        repo.loadBanners()
        val body = bodyAsString(captured.single())
        assertTrue(body.contains("\"user\":\"M1\""), body)
    }

    @Test fun loadBanners_mapsBannerDtos() = runTest {
        val withOne = """
            {"getObjectResult":{"errorData":{"code":0,"description":""},"hasMoreRecords":0,"objectData":[[
              {"bannerId":"B1","BannerName":"FRUIT TREE","bannerImg":"http://x/f.jpg","PrincipalId":"1"}
            ]]}}
        """.trimIndent()
        val (repo, _) = harness { respond(withOne, HttpStatusCode.OK, jsonHeaders) }
        val outcome = repo.loadBanners()
        assertIs<Outcome.Success<List<Banner>>>(outcome)
        val banners = outcome.value
        assertEquals(1, banners.size)
        assertEquals("FRUIT TREE", banners.first().bannerName)
    }

    // ---- loadConfigRails ----

    @Test fun loadConfigRails_returnsRailsSortedByDisplayNo() = runTest {
        val (repo, _) = harness { respond(twoRails, HttpStatusCode.OK, jsonHeaders) }
        val outcome = repo.loadConfigRails()
        assertIs<Outcome.Success<List<CategoryRail>>>(outcome)
        val rails = outcome.value
        // Server returned DisplayNo 2 then 1 — repo sorts so Last Buy (1) comes first.
        assertEquals(listOf("Last Buy", "Most Buy"), rails.map { it.title })
        assertEquals(listOf("GetLastOrder", "GetMostBuy"), rails.map { it.functionName })
        assertTrue(rails.all { it.loading }, "all rails start in loading state")
        assertTrue(rails.all { it.items.isEmpty() }, "rails start with no items")
    }

    @Test fun loadConfigRails_postsGetMultiListConfig() = runTest {
        val (repo, captured) = harness { respond(twoRails, HttpStatusCode.OK, jsonHeaders) }
        repo.loadConfigRails()
        val body = bodyAsString(captured.single())
        assertTrue(body.contains("\"functionName\":\"GetMultiListConfig\""), body)
    }

    // ---- loadRailItems ----

    @Test fun loadRailItems_postsSuppliedFunctionName() = runTest {
        val (repo, captured) = harness { respond(twoItems, HttpStatusCode.OK, jsonHeaders) }
        repo.loadRailItems("GetMostBuy")
        val body = bodyAsString(captured.single())
        assertTrue(body.contains("\"functionName\":\"GetMostBuy\""), body)
    }

    @Test fun loadRailItems_mapsItemDtos() = runTest {
        val (repo, _) = harness { respond(twoItems, HttpStatusCode.OK, jsonHeaders) }
        val outcome = repo.loadRailItems("GetMostBuy")
        assertIs<Outcome.Success<List<Item>>>(outcome)
        val items = outcome.value
        assertEquals(2, items.size)
        assertEquals("Soap", items[0].productDesc)
        assertEquals(true, items[1].isSoldOut)
    }

    // ---- loadNotificationCount ----

    @Test fun loadNotificationCount_extractsCount() = runTest {
        val (repo, captured) = harness { respond(notifCount7, HttpStatusCode.OK, jsonHeaders) }
        val outcome = repo.loadNotificationCount()
        assertEquals(Outcome.Success(7), outcome)
        val body = bodyAsString(captured.single())
        assertTrue(body.contains("\"functionName\":\"GetNotificationCount\""), body)
    }

    // ---- Error mapping ----

    @Test fun loadBanners_401MapsToUnauthorized() = runTest {
        val (repo, _) = harness { respondError(HttpStatusCode.Unauthorized) }
        val outcome = repo.loadBanners()
        assertIs<Outcome.Failure>(outcome)
        assertEquals(AppError.Unauthorized, outcome.error)
    }

    @Test fun loadBanners_500MapsToHttpError() = runTest {
        val (repo, _) = harness { respondError(HttpStatusCode.InternalServerError) }
        val outcome = repo.loadBanners()
        assertIs<Outcome.Failure>(outcome)
        assertIs<AppError.Http>(outcome.error)
        assertEquals(500, (outcome.error as AppError.Http).status)
    }

    @Test fun sequentialCallsAreStateless() = runTest {
        // Repo holds no per-call state — a failure between two successes must
        // not poison the next call's path / body.
        val responses = ArrayDeque(listOf<MockRequestHandler>(
            { respond(twoItems, HttpStatusCode.OK, jsonHeaders) },
            { respondError(HttpStatusCode.InternalServerError) },
            { respond(twoItems, HttpStatusCode.OK, jsonHeaders) },
        ))
        val settings = MapSettings()
        val sessionStore = SettingsSessionStore(settings)
        val countryStore = SettingsCountryStore(settings)
        sessionStore.write(Session("u", "S1", false))
        countryStore.write(Country.PH)
        val client = HttpClient(MockEngine { req -> responses.removeFirst().invoke(this, req) }) {
            expectSuccess = true
            install(ContentNegotiation) { json() }
        }
        val repo = DefaultHomeRepository(KtorCatalogApi(client), sessionStore, countryStore)
        assertIs<Outcome.Success<*>>(repo.loadRailItems("A"))
        assertIs<Outcome.Failure>(repo.loadRailItems("B"))
        assertIs<Outcome.Success<*>>(repo.loadRailItems("C"))
    }
}
