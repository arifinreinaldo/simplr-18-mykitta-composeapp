package com.simplr.mykitta2.data.net.api

import com.simplr.mykitta2.data.net.dto.LoginOtpRequest
import com.simplr.mykitta2.data.net.dto.VerifyLoginOtpRequest
import io.ktor.client.HttpClient
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
     * Verifies the OTP sent by [loginOtp]. Backend response envelope isn't
     * documented in the Postman collection — when confirmed, parse and return
     * the token here instead of [Unit].
     */
    suspend fun verifyLoginOtp(baseUrl: String, request: VerifyLoginOtpRequest)
}

class KtorAuthApi(private val client: HttpClient) : AuthApi {
    override suspend fun loginOtp(baseUrl: String, request: LoginOtpRequest) {
        val url = URLBuilder().takeFrom(baseUrl).appendPathSegments("Account", "LoginOTP").build()
        client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun verifyLoginOtp(baseUrl: String, request: VerifyLoginOtpRequest) {
        val url = URLBuilder().takeFrom(baseUrl).appendPathSegments("Account", "VerifyLoginOTP").build()
        client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }
}
