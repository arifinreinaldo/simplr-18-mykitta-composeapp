package com.simplr.mykitta2.data

import com.russhwolf.settings.MapSettings
import com.simplr.mykitta2.core.error.AppError
import com.simplr.mykitta2.core.result.Outcome
import com.simplr.mykitta2.data.net.api.KtorAuthApi
import com.simplr.mykitta2.data.prefs.CountryStore
import com.simplr.mykitta2.data.prefs.ProfileCacheStore
import com.simplr.mykitta2.data.prefs.SettingsCountryStore
import com.simplr.mykitta2.data.prefs.SettingsProfileCacheStore
import com.simplr.mykitta2.data.prefs.SettingsSessionStore
import com.simplr.mykitta2.data.prefs.SettingsTokenStore
import com.simplr.mykitta2.data.prefs.TokenStore
import com.simplr.mykitta2.data.prefs.SessionStore
import com.simplr.mykitta2.data.repo.DefaultAuthRepository
import com.simplr.mykitta2.data.repo.LocalDataWiper
import com.simplr.mykitta2.domain.Country
import com.simplr.mykitta2.domain.Session
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The repository reads the per-country backend URL straight from `BuildEnv` (no DI seam),
 * so tests rely on whatever `FlavorConfig` was generated from `.env` at build time. The
 * MockEngine still intercepts every request regardless of URL, so the assertions focus on
 *  - the path (`/Account/LoginOTP` and `/Account/VerifyLoginOTP`),
 *  - the body shape (`userId`, `country` dial-code, `otp`, `firebase_token`),
 *  - and the error-mapping branches.
 */
class AuthRepositoryTest {

    private val jsonHeaders =
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    /** Backend `VerifyLoginOTP` envelope (legacy `LoginServerResponse`). Misspelled
     * `validaty` is preserved on purpose — the DTO uses it verbatim. */
    private val verifySuccessBody = """
        {
          "token": "jwt.access.token",
          "refreshToken": "jwt.refresh.token",
          "expiredTime": "Apr Tue 19 2022 09:04:26 AM",
          "validaty": "valid",
          "guidId": "g-1",
          "id": "1",
          "userName": "9171234567",
          "isSupervisor": "True",
          "supervisorCode": "S1"
        }
    """.trimIndent()

    private data class Stores(
        val token: TokenStore,
        val session: SessionStore,
        val country: CountryStore,
        val profileCache: ProfileCacheStore,
    )

    private fun stores(): Stores {
        val settings = MapSettings()
        return Stores(
            token = SettingsTokenStore(settings),
            session = SettingsSessionStore(settings),
            country = SettingsCountryStore(settings),
            profileCache = SettingsProfileCacheStore(settings),
        )
    }

    /** Default wiper for tests that don't exercise logout — a no-op is fine
     *  because the login/verify paths never call it. */
    private val noopWiper = LocalDataWiper { /* no-op */ }

    private fun repo(
        wiper: LocalDataWiper = noopWiper,
        handler: io.ktor.client.engine.mock.MockRequestHandler,
    ): Pair<DefaultAuthRepository, MutableList<HttpRequestData>> {
        val captured = mutableListOf<HttpRequestData>()
        val client = HttpClient(MockEngine { request ->
            captured += request
            handler(this, request)
        }) {
            expectSuccess = true
            install(ContentNegotiation) { json() }
        }
        val s = stores()
        return DefaultAuthRepository(
            api = KtorAuthApi(client),
            tokenStore = s.token,
            sessionStore = s.session,
            countryStore = s.country,
            profileCacheStore = s.profileCache,
            localDataWiper = wiper,
        ) to captured
    }

    private fun bodyAsString(request: HttpRequestData): String =
        (request.body as TextContent).text

    // --- loginOtp ---

    @Test fun phRequestUsesCountryCode63() = runTest {
        val (r, captured) = repo {
            respond(content = "", status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val outcome = r.loginOtp("9171234567", Country.PH)
        assertEquals(Outcome.Success(Unit), outcome)
        val sent = captured.single()
        assertTrue(sent.url.encodedPath.endsWith("Account/LoginOTP"), "path was ${sent.url.encodedPath}")
        val body = bodyAsString(sent)
        assertTrue(body.contains("\"userId\":\"9171234567\""), "body was: $body")
        assertTrue(body.contains("\"country\":\"63\""), "body was: $body")
    }

    @Test fun sgRequestUsesCountryCode65() = runTest {
        val (r, captured) = repo { respond(content = "", status = HttpStatusCode.OK) }
        r.loginOtp("81234567", Country.SG)
        val body = bodyAsString(captured.single())
        assertTrue(body.contains("\"userId\":\"81234567\""))
        assertTrue(body.contains("\"country\":\"65\""))
    }

    @Test fun loginOtp_http422ReturnsHttpFailure() = runTest {
        val (r, _) = repo { respondError(HttpStatusCode.UnprocessableEntity) }
        val outcome = r.loginOtp("81234567", Country.SG)
        assertIs<Outcome.Failure>(outcome)
        assertIs<AppError.Http>(outcome.error)
        assertEquals(422, (outcome.error as AppError.Http).status)
    }

    @Test fun loginOtp_http401ReturnsUnauthorized() = runTest {
        val (r, _) = repo { respondError(HttpStatusCode.Unauthorized) }
        val outcome = r.loginOtp("81234567", Country.SG)
        assertIs<Outcome.Failure>(outcome)
        assertEquals(AppError.Unauthorized, outcome.error)
    }

    @Test fun loginOtp_http500ReturnsHttpFailure() = runTest {
        val (r, _) = repo { respondError(HttpStatusCode.InternalServerError) }
        val outcome = r.loginOtp("81234567", Country.SG)
        assertIs<Outcome.Failure>(outcome)
        assertIs<AppError.Http>(outcome.error)
        assertEquals(500, (outcome.error as AppError.Http).status)
    }

    // --- verifyLoginOtp ---

    @Test fun verifyLoginOtp_postsToVerifyEndpointWithFullBody() = runTest {
        val (r, captured) = repo {
            respond(content = verifySuccessBody, status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val outcome = r.verifyLoginOtp(
            userIdDigits = "9171234567",
            otp = "1234",
            country = Country.PH,
        )
        assertIs<Outcome.Success<Session>>(outcome)
        assertEquals("S1", outcome.value.supervisorCode)
        assertEquals("9171234567", outcome.value.userName)
        assertEquals(true, outcome.value.isSupervisor)

        val sent = captured.single()
        assertTrue(sent.url.encodedPath.endsWith("Account/VerifyLoginOTP"), "path was ${sent.url.encodedPath}")
        val body = bodyAsString(sent)
        assertTrue(body.contains("\"userId\":\"9171234567\""), "body was: $body")
        assertTrue(body.contains("\"otp\":\"1234\""), "body was: $body")
        assertTrue(body.contains("\"firebase_token\":\"\""), "body was: $body")
    }

    @Test fun verifyLoginOtp_success_persistsTokenAndSession() = runTest {
        val settings = MapSettings()
        val tokenStore = SettingsTokenStore(settings)
        val sessionStore = SettingsSessionStore(settings)
        val countryStore = SettingsCountryStore(settings)
        val profileCacheStore = SettingsProfileCacheStore(settings)
        val client = HttpClient(MockEngine {
            respond(content = verifySuccessBody, status = HttpStatusCode.OK, headers = jsonHeaders)
        }) {
            expectSuccess = true
            install(ContentNegotiation) { json() }
        }
        val r = DefaultAuthRepository(
            api = KtorAuthApi(client),
            tokenStore = tokenStore,
            sessionStore = sessionStore,
            countryStore = countryStore,
            profileCacheStore = profileCacheStore,
            localDataWiper = noopWiper,
        )

        val outcome = r.verifyLoginOtp("9171234567", "1234", Country.PH)
        assertIs<Outcome.Success<Session>>(outcome)

        // TokenStore got the JWT
        val pair = tokenStore.read()
        assertNotNull(pair)
        assertEquals("jwt.access.token", pair.access)
        assertEquals("jwt.refresh.token", pair.refresh)

        // SessionStore got the user profile
        val session = sessionStore.read()
        assertNotNull(session)
        assertEquals("9171234567", session.userName)
        assertEquals("S1", session.supervisorCode)
        assertEquals(true, session.isSupervisor)
    }

    // --- logout ---

    @Test fun logout_clearsTokenSessionCountryAndCallsWiper() = runTest {
        val settings = MapSettings()
        val tokenStore = SettingsTokenStore(settings).also {
            it.write(
                com.simplr.mykitta2.data.prefs.TokenPair(
                    access = "a", refresh = "r",
                    expiresAt = kotlin.time.Clock.System.now(),
                )
            )
        }
        val sessionStore = SettingsSessionStore(settings).also {
            it.write(Session(userName = "u", supervisorCode = "s", isSupervisor = true))
        }
        val countryStore = SettingsCountryStore(settings).also { it.write(Country.PH) }
        val profileCacheStore = SettingsProfileCacheStore(settings).also {
            it.write(com.simplr.mykitta2.domain.Profile(custName = "cached-outlet"))
        }
        var wipeCount = 0
        val wiper = LocalDataWiper { wipeCount += 1 }

        val r = DefaultAuthRepository(
            api = KtorAuthApi(HttpClient(MockEngine { respond("", HttpStatusCode.OK) })),
            tokenStore = tokenStore,
            sessionStore = sessionStore,
            countryStore = countryStore,
            profileCacheStore = profileCacheStore,
            localDataWiper = wiper,
        )

        val outcome = r.logout()

        assertEquals(Outcome.Success(Unit), outcome)
        assertNull(tokenStore.read(), "token should be cleared")
        assertNull(sessionStore.read(), "session should be cleared")
        assertNull(countryStore.read(), "country should be cleared")
        assertNull(profileCacheStore.read(), "profile cache should be cleared")
        assertEquals(1, wipeCount, "wiper should be called exactly once")
    }

    @Test fun logout_isIdempotentOnAlreadyEmptyState() = runTest {
        // No prior writes — all stores start empty. Logout must not throw.
        val s = stores()
        val r = DefaultAuthRepository(
            api = KtorAuthApi(HttpClient(MockEngine { respond("", HttpStatusCode.OK) })),
            tokenStore = s.token,
            sessionStore = s.session,
            countryStore = s.country,
            profileCacheStore = s.profileCache,
            localDataWiper = noopWiper,
        )
        assertEquals(Outcome.Success(Unit), r.logout())
    }

    @Test fun logout_wiperThrows_returnsFailureButTokensStillCleared() = runTest {
        // Token is cleared first by design (de-authenticate ASAP); even when the
        // wiper later blows up, the user is signed out at the HTTP boundary.
        val s = stores()
        s.token.write(
            com.simplr.mykitta2.data.prefs.TokenPair(
                access = "a", refresh = "r",
                expiresAt = kotlin.time.Clock.System.now(),
            )
        )
        val r = DefaultAuthRepository(
            api = KtorAuthApi(HttpClient(MockEngine { respond("", HttpStatusCode.OK) })),
            tokenStore = s.token,
            sessionStore = s.session,
            countryStore = s.country,
            profileCacheStore = s.profileCache,
            localDataWiper = LocalDataWiper { error("boom") },
        )
        val outcome = r.logout()
        assertIs<Outcome.Failure>(outcome)
        assertNull(s.token.read(), "token must be cleared even when wiper fails")
    }

    @Test fun verifyLoginOtp_http422ReturnsHttpFailure() = runTest {
        val (r, _) = repo { respondError(HttpStatusCode.UnprocessableEntity) }
        val outcome = r.verifyLoginOtp("9171234567", "9999", Country.PH)
        assertIs<Outcome.Failure>(outcome)
        assertIs<AppError.Http>(outcome.error)
        assertEquals(422, (outcome.error as AppError.Http).status)
    }
}
