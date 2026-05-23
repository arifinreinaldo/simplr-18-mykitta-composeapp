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
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.URLBuilder
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AuthRepositoryTest {

    private fun client(handler: io.ktor.client.engine.mock.MockRequestHandler) =
        HttpClient(MockEngine(handler)) {
            expectSuccess = true
            install(ContentNegotiation) { json() }
            defaultRequest { url("https://test.local/api/") }
        }

    @Test
    fun successReturnsSuccess() = runTest {
        val capturedPath = mutableListOf<String>()
        val c = client { request ->
            capturedPath += request.url.encodedPath
            respond(
                content = "{\"success\":true,\"message\":\"sent\"}",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val repo = DefaultAuthRepository(KtorAuthApi(c))
        val outcome = repo.loginOtp("+639171234567", Country.PH)
        assertEquals(Outcome.Success(Unit), outcome)
        assertTrue(capturedPath.single().endsWith("login/otp"))
    }

    @Test
    fun http422ReturnsHttpFailure() = runTest {
        val c = client { respondError(HttpStatusCode.UnprocessableEntity) }
        val outcome = DefaultAuthRepository(KtorAuthApi(c)).loginOtp("+6512345678", Country.SG)
        assertIs<Outcome.Failure>(outcome)
        assertIs<AppError.Http>(outcome.error)
        assertEquals(422, (outcome.error as AppError.Http).status)
    }

    @Test
    fun http401ReturnsUnauthorized() = runTest {
        val c = client { respondError(HttpStatusCode.Unauthorized) }
        val outcome = DefaultAuthRepository(KtorAuthApi(c)).loginOtp("+6512345678", Country.SG)
        assertIs<Outcome.Failure>(outcome)
        assertEquals(AppError.Unauthorized, outcome.error)
    }

    @Test
    fun http500ReturnsHttpFailure() = runTest {
        val c = client { respondError(HttpStatusCode.InternalServerError) }
        val outcome = DefaultAuthRepository(KtorAuthApi(c)).loginOtp("+6512345678", Country.SG)
        assertIs<Outcome.Failure>(outcome)
        assertIs<AppError.Http>(outcome.error)
        assertEquals(500, (outcome.error as AppError.Http).status)
    }

    @Test
    fun malformedJsonReturnsParse() = runTest {
        val c = client {
            respond(
                content = "not-json",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val outcome = DefaultAuthRepository(KtorAuthApi(c)).loginOtp("+6512345678", Country.SG)
        assertIs<Outcome.Failure>(outcome)
        assertIs<AppError.Parse>(outcome.error)
    }
}
