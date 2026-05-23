package com.simplr.mykitta2.data.net.api

import com.simplr.mykitta2.data.net.dto.LoginOtpRequest
import com.simplr.mykitta2.data.net.dto.LoginOtpResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

interface AuthApi {
    suspend fun loginOtp(request: LoginOtpRequest): LoginOtpResponse
}

class KtorAuthApi(private val client: HttpClient) : AuthApi {
    override suspend fun loginOtp(request: LoginOtpRequest): LoginOtpResponse =
        client.post(LOGIN_OTP_PATH) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    private companion object {
        // Placeholder per spec §10 risk #6 — revise once legacy Repository.kt path is confirmed.
        const val LOGIN_OTP_PATH = "login/otp"
    }
}
