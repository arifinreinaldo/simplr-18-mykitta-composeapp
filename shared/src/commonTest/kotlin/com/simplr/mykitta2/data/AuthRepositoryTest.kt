package com.simplr.mykitta2.data

import com.simplr.mykitta2.core.error.AppError
import com.simplr.mykitta2.core.result.Outcome
import com.simplr.mykitta2.data.net.api.KtorAuthApi
import com.simplr.mykitta2.data.repo.DefaultAuthRepository
import com.simplr.mykitta2.domain.Country
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

    private fun client(
        captured: MutableList<HttpRequestData> = mutableListOf(),
        handler: io.ktor.client.engine.mock.MockRequestHandler,
    ) = HttpClient(MockEngine { request ->
        captured += request
        handler(this, request)
    }) {
        expectSuccess = true
        install(ContentNegotiation) { json() }
    }

    private fun bodyAsString(request: HttpRequestData): String =
        (request.body as TextContent).text

    // --- loginOtp ---

    @Test fun phRequestUsesCountryCode63() = runTest {
        val captured = mutableListOf<HttpRequestData>()
        val c = client(captured) {
            respond(
                content = "",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val outcome = DefaultAuthRepository(KtorAuthApi(c)).loginOtp("9171234567", Country.PH)
        assertEquals(Outcome.Success(Unit), outcome)
        val sent = captured.single()
        assertTrue(sent.url.encodedPath.endsWith("Account/LoginOTP"), "path was ${sent.url.encodedPath}")
        val body = bodyAsString(sent)
        assertTrue(body.contains("\"userId\":\"9171234567\""), "body was: $body")
        assertTrue(body.contains("\"country\":\"63\""), "body was: $body")
    }

    @Test fun sgRequestUsesCountryCode65() = runTest {
        val captured = mutableListOf<HttpRequestData>()
        val c = client(captured) {
            respond(content = "", status = HttpStatusCode.OK)
        }
        DefaultAuthRepository(KtorAuthApi(c)).loginOtp("81234567", Country.SG)
        val body = bodyAsString(captured.single())
        assertTrue(body.contains("\"userId\":\"81234567\""))
        assertTrue(body.contains("\"country\":\"65\""))
    }

    @Test fun loginOtp_http422ReturnsHttpFailure() = runTest {
        val c = client { respondError(HttpStatusCode.UnprocessableEntity) }
        val outcome = DefaultAuthRepository(KtorAuthApi(c)).loginOtp("81234567", Country.SG)
        assertIs<Outcome.Failure>(outcome)
        assertIs<AppError.Http>(outcome.error)
        assertEquals(422, (outcome.error as AppError.Http).status)
    }

    @Test fun loginOtp_http401ReturnsUnauthorized() = runTest {
        val c = client { respondError(HttpStatusCode.Unauthorized) }
        val outcome = DefaultAuthRepository(KtorAuthApi(c)).loginOtp("81234567", Country.SG)
        assertIs<Outcome.Failure>(outcome)
        assertEquals(AppError.Unauthorized, outcome.error)
    }

    @Test fun loginOtp_http500ReturnsHttpFailure() = runTest {
        val c = client { respondError(HttpStatusCode.InternalServerError) }
        val outcome = DefaultAuthRepository(KtorAuthApi(c)).loginOtp("81234567", Country.SG)
        assertIs<Outcome.Failure>(outcome)
        assertIs<AppError.Http>(outcome.error)
        assertEquals(500, (outcome.error as AppError.Http).status)
    }

    // --- verifyLoginOtp ---

    @Test fun verifyLoginOtp_postsToVerifyEndpointWithFullBody() = runTest {
        val captured = mutableListOf<HttpRequestData>()
        val c = client(captured) {
            respond(content = "", status = HttpStatusCode.OK)
        }
        val outcome = DefaultAuthRepository(KtorAuthApi(c)).verifyLoginOtp(
            userIdDigits = "9171234567",
            otp = "1234",
            country = Country.PH,
        )
        assertEquals(Outcome.Success(Unit), outcome)
        val sent = captured.single()
        assertTrue(sent.url.encodedPath.endsWith("Account/VerifyLoginOTP"), "path was ${sent.url.encodedPath}")
        val body = bodyAsString(sent)
        assertTrue(body.contains("\"userId\":\"9171234567\""), "body was: $body")
        assertTrue(body.contains("\"otp\":\"1234\""), "body was: $body")
        assertTrue(body.contains("\"firebase_token\":\"\""), "body was: $body")
    }

    @Test fun verifyLoginOtp_http422ReturnsHttpFailure() = runTest {
        val c = client { respondError(HttpStatusCode.UnprocessableEntity) }
        val outcome = DefaultAuthRepository(KtorAuthApi(c))
            .verifyLoginOtp("9171234567", "9999", Country.PH)
        assertIs<Outcome.Failure>(outcome)
        assertIs<AppError.Http>(outcome.error)
        assertEquals(422, (outcome.error as AppError.Http).status)
    }
}
