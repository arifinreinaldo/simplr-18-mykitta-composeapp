package com.simplr.mykitta2.data.repo

import com.simplr.mykitta2.core.env.BuildEnv
import com.simplr.mykitta2.core.error.ErrorMapper
import com.simplr.mykitta2.core.result.Outcome
import com.simplr.mykitta2.data.net.api.AuthApi
import com.simplr.mykitta2.data.net.dto.LoginOtpRequest
import com.simplr.mykitta2.data.net.dto.VerifyLoginOtpRequest
import com.simplr.mykitta2.data.prefs.SessionStore
import com.simplr.mykitta2.data.prefs.TokenPair
import com.simplr.mykitta2.data.prefs.TokenStore
import com.simplr.mykitta2.domain.Country
import com.simplr.mykitta2.domain.Session
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

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
     * Verifies the OTP. On success, persists both the bearer token (secure) and the
     * user profile (plain), and returns the new [Session] so the caller can route
     * onward. The Ktor `Auth` plugin reads the token on every subsequent call.
     */
    suspend fun verifyLoginOtp(userIdDigits: String, otp: String, country: Country): Outcome<Session>
}

class DefaultAuthRepository(
    private val api: AuthApi,
    private val tokenStore: TokenStore,
    private val sessionStore: SessionStore,
) : AuthRepository {
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
    ): Outcome<Session> = try {
        val response = api.verifyLoginOtp(
            baseUrl = BuildEnv.baseUrlFor(country),
            request = VerifyLoginOtpRequest(userId = userIdDigits, otp = otp),
        )
        // Backend `expiredTime` arrives as a human-readable string ("Apr Tue 19 ...")
        // which kotlinx-datetime can't parse. Refresh isn't wired yet — we synthesise
        // a far-future expiry so the Bearer plugin loads tokens. A 401 from any call
        // is the real expiry signal until the refresh flow lands.
        tokenStore.write(
            TokenPair(
                access = response.token,
                refresh = response.refreshToken.orEmpty(),
                expiresAt = Clock.System.now() + 365.days,
            )
        )
        val session = Session(
            userName = response.userName,
            supervisorCode = response.supervisorCode,
            isSupervisor = response.parseIsSupervisor(),
        )
        sessionStore.write(session)
        Outcome.Success(session)
    } catch (t: Throwable) {
        Outcome.Failure(ErrorMapper.from(t))
    }
}
