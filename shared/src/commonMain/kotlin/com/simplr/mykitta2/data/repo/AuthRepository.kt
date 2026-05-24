package com.simplr.mykitta2.data.repo

import com.simplr.mykitta2.core.env.BuildEnv
import com.simplr.mykitta2.core.error.ErrorMapper
import com.simplr.mykitta2.core.result.Outcome
import com.simplr.mykitta2.data.net.api.AuthApi
import com.simplr.mykitta2.data.net.dto.LoginOtpRequest
import com.simplr.mykitta2.data.net.dto.VerifyLoginOtpRequest
import com.simplr.mykitta2.domain.Country

interface AuthRepository {
    /**
     * Triggers the SMS containing the OTP.
     *
     * @param userIdDigits country-local digits only (e.g. "9171234567" for PH); no "+", no dial code.
     * @param country picks both the request's `country` field (`"63"`/`"65"`) AND the backend
     *   root (via [BuildEnv.baseUrlFor]) — each country lives on its own server.
     */
    suspend fun loginOtp(userIdDigits: String, country: Country): Outcome<Unit>

    /**
     * Verifies the OTP from the SMS. HTTP 2xx = verified.
     *
     * TODO: Once the response envelope is confirmed (token + principal info),
     * change the return type to carry the token and write it to TokenStore here.
     */
    suspend fun verifyLoginOtp(userIdDigits: String, otp: String, country: Country): Outcome<Unit>
}

class DefaultAuthRepository(private val api: AuthApi) : AuthRepository {
    override suspend fun loginOtp(userIdDigits: String, country: Country): Outcome<Unit> = try {
        api.loginOtp(
            baseUrl = BuildEnv.baseUrlFor(country),
            request = LoginOtpRequest(userId = userIdDigits, country = country.apiCountryCode),
        )
        Outcome.Success(Unit)
    } catch (t: Throwable) {
        Outcome.Failure(ErrorMapper.from(t))
    }

    override suspend fun verifyLoginOtp(
        userIdDigits: String,
        otp: String,
        country: Country,
    ): Outcome<Unit> = try {
        api.verifyLoginOtp(
            baseUrl = BuildEnv.baseUrlFor(country),
            request = VerifyLoginOtpRequest(userId = userIdDigits, otp = otp),
        )
        Outcome.Success(Unit)
    } catch (t: Throwable) {
        Outcome.Failure(ErrorMapper.from(t))
    }
}
