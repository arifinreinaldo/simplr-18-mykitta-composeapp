package com.simplr.mykitta2.data.net.api

import com.simplr.mykitta2.data.net.dto.LoginOtpRequest
import com.simplr.mykitta2.data.net.dto.VerifyLoginOtpRequest
import com.simplr.mykitta2.data.net.dto.VerifyLoginOtpResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.takeFrom

interface AuthApi {
    /**
     * @param baseUrl absolute backend root for the call (e.g. `http://ph.api/`).
     *   Tolerates trailing slash either way.
     */
    suspend fun loginOtp(baseUrl: String, request: LoginOtpRequest)

    /**
     * Verifies the OTP sent by [loginOtp]. Returns the full session envelope —
     * token (used by [com.simplr.mykitta2.data.prefs.TokenStore]) and user profile
     * (used by [com.simplr.mykitta2.data.prefs.SessionStore]).
     */
    suspend fun verifyLoginOtp(baseUrl: String, request: VerifyLoginOtpRequest): VerifyLoginOtpResponse
}

class KtorAuthApi(private val client: HttpClient) : AuthApi {
    override suspend fun loginOtp(baseUrl: String, request: LoginOtpRequest) {
        val url = URLBuilder().takeFrom(baseUrl).appendPathSegments("Account", "LoginOTP").build()
        client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun verifyLoginOtp(
        baseUrl: String,
        request: VerifyLoginOtpRequest,
    ): VerifyLoginOtpResponse {
        val url = URLBuilder().takeFrom(baseUrl).appendPathSegments("Account", "VerifyLoginOTP").build()
        return client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }
}
