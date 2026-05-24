package com.simplr.mykitta2.data.net.dto

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The `VerifyLoginOTP` response envelope has two backend quirks the DTO has
 * to preserve verbatim:
 *  - `validaty` is misspelled in the backend payload and must be parsed under
 *    that exact key (a "fix" to "validity" would silently drop the value).
 *  - `isSupervisor` arrives as a JSON string `"True"`/`"False"`, NOT a boolean.
 *
 * These tests pin both, so a future "clean-up" PR can't quietly drift away.
 */
class LoginOtpDtosTest {

    // Mirrors the production ContentNegotiation config (ignoreUnknownKeys = true).
    private val json = Json { ignoreUnknownKeys = true; isLenient = false }

    @Test fun verifyLoginOtpResponse_parsesValidatyTypoVerbatim() {
        val body = """
            {
              "token":"t","refreshToken":"r","expiredTime":"x","validaty":"v",
              "guidId":"g","id":"1","userName":"u","isSupervisor":"True","supervisorCode":"S1"
            }
        """.trimIndent()
        val parsed = json.decodeFromString<VerifyLoginOtpResponse>(body)
        assertEquals("v", parsed.validaty)
    }

    @Test fun verifyLoginOtpResponse_parseIsSupervisorTrueString() {
        val body = baseBody(isSupervisor = "True")
        val parsed = json.decodeFromString<VerifyLoginOtpResponse>(body)
        assertEquals(true, parsed.parseIsSupervisor())
    }

    @Test fun verifyLoginOtpResponse_parseIsSupervisorFalseString() {
        val body = baseBody(isSupervisor = "False")
        val parsed = json.decodeFromString<VerifyLoginOtpResponse>(body)
        assertEquals(false, parsed.parseIsSupervisor())
    }

    @Test fun verifyLoginOtpResponse_parseIsSupervisorCaseInsensitive() {
        val body = baseBody(isSupervisor = "TRUE")
        val parsed = json.decodeFromString<VerifyLoginOtpResponse>(body)
        assertEquals(true, parsed.parseIsSupervisor())
    }

    @Test fun verifyLoginOtpResponse_parseIsSupervisorUnknownStringFalse() {
        val body = baseBody(isSupervisor = "Maybe")
        val parsed = json.decodeFromString<VerifyLoginOtpResponse>(body)
        assertEquals(false, parsed.parseIsSupervisor())
    }

    @Test fun verifyLoginOtpResponse_refreshTokenNullableOmittedField() {
        val body = """
            {
              "token":"t","expiredTime":"x","validaty":"v",
              "guidId":"g","id":"1","userName":"u","isSupervisor":"True","supervisorCode":"S1"
            }
        """.trimIndent()
        val parsed = json.decodeFromString<VerifyLoginOtpResponse>(body)
        assertEquals(null, parsed.refreshToken)
    }

    @Test fun loginOtpRequest_serializesUserIdAndCountry() {
        val req = LoginOtpRequest(userId = "9171234567", country = "63")
        val encoded = json.encodeToString(LoginOtpRequest.serializer(), req)
        assertTrue(encoded.contains("\"userId\":\"9171234567\""))
        assertTrue(encoded.contains("\"country\":\"63\""))
    }

    @Test fun verifyLoginOtpRequest_emitsFirebaseTokenSnakeCaseField() {
        // The backend keys the field as `firebase_token` (underscore). The DTO
        // SerialName MUST stay snake_case even though the Kotlin property is
        // firebaseToken — backend rejects the request otherwise.
        val req = VerifyLoginOtpRequest(userId = "u", otp = "1234")
        val encoded = json.encodeToString(VerifyLoginOtpRequest.serializer(), req)
        assertTrue(encoded.contains("\"firebase_token\":\"\""), "encoded was: $encoded")
    }

    private fun baseBody(isSupervisor: String) = """
        {
          "token":"t","expiredTime":"x","validaty":"v",
          "guidId":"g","id":"1","userName":"u",
          "isSupervisor":"$isSupervisor","supervisorCode":"S1"
        }
    """.trimIndent()
}
