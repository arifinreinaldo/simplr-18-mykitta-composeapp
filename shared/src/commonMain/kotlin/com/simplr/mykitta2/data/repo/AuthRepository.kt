package com.simplr.mykitta2.data.repo

import com.simplr.mykitta2.core.error.ErrorMapper
import com.simplr.mykitta2.core.result.Outcome
import com.simplr.mykitta2.data.net.api.AuthApi
import com.simplr.mykitta2.data.net.dto.LoginOtpRequest
import com.simplr.mykitta2.domain.Country

interface AuthRepository {
    suspend fun loginOtp(phoneE164: String, country: Country): Outcome<Unit>
}

class DefaultAuthRepository(private val api: AuthApi) : AuthRepository {
    override suspend fun loginOtp(phoneE164: String, country: Country): Outcome<Unit> = try {
        val response = api.loginOtp(
            LoginOtpRequest(phone = phoneE164, country = country.wireFormat)
        )
        if (response.success) Outcome.Success(Unit)
        else Outcome.Failure(
            com.simplr.mykitta2.core.error.AppError.Http(
                status = 200,
                body = response.message,
            )
        )
    } catch (t: Throwable) {
        Outcome.Failure(ErrorMapper.from(t))
    }
}
