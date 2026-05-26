package com.simplr.mykitta2.data

import com.russhwolf.settings.MapSettings
import com.simplr.mykitta2.core.error.AppError
import com.simplr.mykitta2.core.result.Outcome
import com.simplr.mykitta2.data.net.api.KtorCatalogApi
import com.simplr.mykitta2.data.prefs.ProfileCacheStore
import com.simplr.mykitta2.data.prefs.SettingsCountryStore
import com.simplr.mykitta2.data.prefs.SettingsProfileCacheStore
import com.simplr.mykitta2.data.prefs.SettingsSessionStore
import com.simplr.mykitta2.data.repo.DefaultProfileRepository
import com.simplr.mykitta2.domain.Country
import com.simplr.mykitta2.domain.Profile
import com.simplr.mykitta2.domain.Session
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

/**
 * Covers the cache-vs-network contract of [DefaultProfileRepository]:
 *  - cache hit within TTL → no network call
 *  - cache miss → network fetch + cache write
 *  - cache stale + network fail → stale cache returned as Success
 *  - cache absent + network fail → Failure
 *  - refresh() always hits the network regardless of cache
 */
class ProfileRepositoryTest {

    private val jsonHeaders =
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    /** Verbatim shape of the live `User/GetObject` response with
     *  `functionName=GetProfile` — including the backend's habit of returning
     *  the same record three times inside `objectData[0]`. The repository must
     *  pick the first and ignore the dupes. */
    private val profileResponseBody = """
        {
          "getObjectResult": {
            "errorData": { "code": 0, "description": "" },
            "hasMoreRecords": 0,
            "objectData": [[
              {"CustName":"Demo outlet","Phone":"1111111111","email":"demoemail@demo.com","ICPartner":"Demo reg n","GSTNo":"Demo tax no"},
              {"CustName":"Demo outlet","Phone":"1111111111","email":"demoemail@demo.com","ICPartner":"Demo reg n","GSTNo":"Demo tax no"},
              {"CustName":"Demo outlet","Phone":"1111111111","email":"demoemail@demo.com","ICPartner":"Demo reg n","GSTNo":"Demo tax no"}
            ]]
          }
        }
    """.trimIndent()

    private fun mockClient(handler: io.ktor.client.engine.mock.MockRequestHandler): Pair<HttpClient, () -> Int> {
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

    private fun repo(client: HttpClient): Triple<DefaultProfileRepository, ProfileCacheStore, SettingsSessionStore> {
        val settings = MapSettings()
        val cache = SettingsProfileCacheStore(settings)
        val session = SettingsSessionStore(settings)
        val country = SettingsCountryStore(settings)
        val r = DefaultProfileRepository(
            catalogApi = KtorCatalogApi(client),
            cacheStore = cache,
            sessionStore = session,
            countryStore = country,
        )
        return Triple(r, cache, session)
    }

    @Test fun firstLoad_cacheEmpty_hitsNetworkAndCachesResult() = runTest {
        val (client, callCount) = mockClient {
            respond(content = profileResponseBody, status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val (r, cache, _) = repo(client)

        val outcome = r.loadProfile()

        assertIs<Outcome.Success<Profile>>(outcome)
        assertEquals("Demo outlet", outcome.value.custName)
        assertEquals("1111111111", outcome.value.phone)
        assertEquals("Demo reg n", outcome.value.icPartner)
        assertEquals("Demo tax no", outcome.value.gstNo)
        assertEquals("demoemail@demo.com", outcome.value.email)
        assertEquals(1, callCount(), "exactly one network call on cache miss")
        assertNotNull(cache.read(), "cache should now contain the fetched profile")
        assertEquals("Demo outlet", cache.read()?.custName)
    }

    @Test fun secondLoadWithinTTL_servesFromCache_noNetworkCall() = runTest {
        val (client, callCount) = mockClient {
            respond(content = profileResponseBody, status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val (r, _, _) = repo(client)

        r.loadProfile()                 // populates cache
        val second = r.loadProfile()    // should be served from cache

        assertIs<Outcome.Success<Profile>>(second)
        assertEquals(1, callCount(), "second load must not hit the network")
    }

    @Test fun loadWithZeroTTL_alwaysRefetches() = runTest {
        val (client, callCount) = mockClient {
            respond(content = profileResponseBody, status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val (r, _, _) = repo(client)

        r.loadProfile(ttl = 24.hours)   // populates cache
        r.loadProfile(ttl = kotlin.time.Duration.ZERO)

        assertEquals(2, callCount(), "TTL=ZERO bypasses cache")
    }

    @Test fun networkFailWithStaleCache_returnsStaleAsSuccess() = runTest {
        val settings = MapSettings()
        val cache = SettingsProfileCacheStore(settings).also {
            it.write(Profile(custName = "stale-outlet", email = "stale@x"))
        }
        // Engine returns 500 unconditionally
        val client = HttpClient(MockEngine { respondError(HttpStatusCode.InternalServerError) }) {
            expectSuccess = true
            install(ContentNegotiation) { json() }
        }
        val r = DefaultProfileRepository(
            catalogApi = KtorCatalogApi(client),
            cacheStore = cache,
            sessionStore = SettingsSessionStore(settings),
            countryStore = SettingsCountryStore(settings),
        )

        val outcome = r.loadProfile(ttl = kotlin.time.Duration.ZERO)

        // We'd rather show stale data than block the UI behind an error.
        assertIs<Outcome.Success<Profile>>(outcome)
        assertEquals("stale-outlet", outcome.value.custName)
    }

    @Test fun networkFailWithEmptyCache_returnsFailure() = runTest {
        val client = HttpClient(MockEngine { respondError(HttpStatusCode.InternalServerError) }) {
            expectSuccess = true
            install(ContentNegotiation) { json() }
        }
        val (r, cache, _) = repo(client)
        assertNull(cache.read(), "precondition: cache is empty")

        val outcome = r.loadProfile()

        assertIs<Outcome.Failure>(outcome)
        assertIs<AppError.Http>(outcome.error)
        assertEquals(500, (outcome.error as AppError.Http).status)
    }

    @Test fun refresh_alwaysHitsNetwork_overwritesCache() = runTest {
        var bodyToReturn = profileResponseBody
        val (client, callCount) = mockClient {
            respond(content = bodyToReturn, status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val (r, cache, _) = repo(client)

        r.loadProfile() // populates cache with "Demo outlet"
        assertEquals("Demo outlet", cache.read()?.custName)

        // Swap the response and refresh — cache must update.
        bodyToReturn = profileResponseBody.replace("Demo outlet", "Renamed outlet")
        val refreshed = r.refresh()

        assertIs<Outcome.Success<Profile>>(refreshed)
        assertEquals("Renamed outlet", refreshed.value.custName)
        assertEquals(2, callCount(), "refresh always hits the network")
        assertEquals("Renamed outlet", cache.read()?.custName)
    }

    @Test fun request_usesSupervisorCodeFromSession() = runTest {
        val settings = MapSettings()
        val sessionStore = SettingsSessionStore(settings).also {
            it.write(Session(userName = "u", supervisorCode = "S-FROM-SESSION", isSupervisor = true))
        }
        val countryStore = SettingsCountryStore(settings).also { it.write(Country.PH) }
        val cache = SettingsProfileCacheStore(settings)

        val sent = mutableListOf<String>()
        val client = HttpClient(MockEngine { req ->
            sent += (req.body as io.ktor.http.content.TextContent).text
            respond(content = profileResponseBody, status = HttpStatusCode.OK, headers = jsonHeaders)
        }) {
            expectSuccess = true
            install(ContentNegotiation) { json() }
        }
        val r = DefaultProfileRepository(
            catalogApi = KtorCatalogApi(client),
            cacheStore = cache,
            sessionStore = sessionStore,
            countryStore = countryStore,
        )

        r.refresh()

        val body = sent.single()
        assertTrue(body.contains("\"functionName\":\"GetProfile\""), "wrong functionName: $body")
        assertTrue(body.contains("\"user\":\"S-FROM-SESSION\""), "wrong user: $body")
    }
}
