package com.simplr.mykitta2.feature.auth

import com.arkivanov.mvikotlin.core.utils.JvmSerializable
import com.arkivanov.mvikotlin.extensions.coroutines.labels
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import com.simplr.mykitta2.core.error.AppError
import com.simplr.mykitta2.core.result.Outcome
import com.simplr.mykitta2.data.repo.AuthRepository
import com.simplr.mykitta2.domain.Country
import com.simplr.mykitta2.domain.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OtpVerifyStoreTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private val defaultSession = Session(userName = "user", supervisorCode = "S1", isSupervisor = false)

    private class FakeAuthRepository(
        var verifyResult: Outcome<Session> = Outcome.Success(
            Session(userName = "user", supervisorCode = "S1", isSupervisor = false)
        ),
        var loginResult: Outcome<Unit> = Outcome.Success(Unit),
    ) : AuthRepository, JvmSerializable {
        val verifyCalls = mutableListOf<Triple<String, String, Country>>()
        val loginCalls = mutableListOf<Pair<String, Country>>()
        override suspend fun loginOtp(userIdDigits: String, country: Country): Outcome<Unit> {
            loginCalls += userIdDigits to country
            return loginResult
        }
        override suspend fun verifyLoginOtp(
            userIdDigits: String,
            otp: String,
            country: Country,
        ): Outcome<Session> {
            verifyCalls += Triple(userIdDigits, otp, country)
            return verifyResult
        }
        override suspend fun logout(): Outcome<Unit> = Outcome.Success(Unit)
    }

    private fun store(
        repo: FakeAuthRepository = FakeAuthRepository(),
        userIdDigits: String = "9171234567",
        country: Country = Country.PH,
    ): Pair<OtpVerifyStore, FakeAuthRepository> {
        val s = OtpVerifyStoreFactory(
            storeFactory = DefaultStoreFactory(),
            authRepository = repo,
            args = OtpVerifyArgs(userIdDigits, country),
        ).create()
        return s to repo
    }

    // --- OTP editing ---

    @Test fun initialStateIsEmptyAndInvalid() = runTest(dispatcher) {
        val (s, _) = store()
        assertEquals("", s.state.otp)
        assertFalse(s.state.isValid)
    }

    @Test fun otpChanged_filtersNonDigitsAndCapsAtFour() = runTest(dispatcher) {
        val (s, _) = store()
        s.accept(OtpVerifyStore.Intent.OtpChanged("12a3b45678"))
        assertEquals("1234", s.state.otp)
        assertTrue(s.state.isValid)
    }

    @Test fun otpChanged_invalidWhenShort() = runTest(dispatcher) {
        val (s, _) = store()
        s.accept(OtpVerifyStore.Intent.OtpChanged("12"))
        assertFalse(s.state.isValid)
    }

    @Test fun otpChanged_clearsExistingError() = runTest(dispatcher) {
        val (s, _) = store(repo = FakeAuthRepository(verifyResult = Outcome.Failure(AppError.Network)))
        s.accept(OtpVerifyStore.Intent.OtpChanged("1234"))
        s.accept(OtpVerifyStore.Intent.Submit)
        assertNotNull(s.state.error)
        s.accept(OtpVerifyStore.Intent.OtpChanged("1235"))
        assertNull(s.state.error)
    }

    // --- Submit (verify) ---

    @Test fun submit_isNoOpWhenInvalid() = runTest(dispatcher) {
        val (s, repo) = store()
        s.accept(OtpVerifyStore.Intent.OtpChanged("12"))
        s.accept(OtpVerifyStore.Intent.Submit)
        assertTrue(repo.verifyCalls.isEmpty())
    }

    @Test fun submit_callsRepoWithArgsFromConstruction() = runTest(dispatcher) {
        val (s, repo) = store(userIdDigits = "81234567", country = Country.SG)
        s.accept(OtpVerifyStore.Intent.OtpChanged("9999"))
        s.accept(OtpVerifyStore.Intent.Submit)
        assertEquals(listOf(Triple("81234567", "9999", Country.SG)), repo.verifyCalls)
    }

    @Test fun submit_success_emitsNavigateToLoggedIn() = runTest(dispatcher) {
        val (s, _) = store()
        val captured = mutableListOf<OtpVerifyStore.Label>()
        val collector = launch { s.labels.collect { captured += it } }

        s.accept(OtpVerifyStore.Intent.OtpChanged("1234"))
        s.accept(OtpVerifyStore.Intent.Submit)

        collector.cancel()
        assertEquals(1, captured.size)
        assertEquals(OtpVerifyStore.Label.NavigateToLoggedIn, captured.single())
        assertFalse(s.state.submitting)
    }

    @Test fun submit_failure_setsErrorAndDoesNotEmitLabel() = runTest(dispatcher) {
        val (s, _) = store(repo = FakeAuthRepository(verifyResult = Outcome.Failure(AppError.Network)))
        val captured = mutableListOf<OtpVerifyStore.Label>()
        val collector = launch { s.labels.collect { captured += it } }

        s.accept(OtpVerifyStore.Intent.OtpChanged("1234"))
        s.accept(OtpVerifyStore.Intent.Submit)

        collector.cancel()
        assertTrue(captured.isEmpty())
        assertNotNull(s.state.error)
        assertFalse(s.state.submitting)
    }

    // --- Resend countdown ---

    @Test fun bootstrap_startsCountdownAtThreeMinutes() = runTest(dispatcher) {
        val (s, _) = store()
        // Bootstrapper has run synchronously under UnconfinedTestDispatcher,
        // and the first tick (180) has been dispatched.
        assertEquals(OtpVerifyStore.RESEND_COOLDOWN_SECONDS, s.state.resendCountdownSeconds)
        assertFalse(s.state.canResend)
    }

    @Test fun countdown_decrementsOverTime() = runTest(dispatcher) {
        val (s, _) = store()
        // advanceTimeBy is exclusive on the upper bound — bump past 5s so the
        // tick scheduled AT t=5000 also fires.
        advanceTimeBy(5_500L)
        assertEquals(OtpVerifyStore.RESEND_COOLDOWN_SECONDS - 5, s.state.resendCountdownSeconds)
        assertFalse(s.state.canResend)
    }

    @Test fun countdown_reachesZero_canResendBecomesTrue() = runTest(dispatcher) {
        val (s, _) = store()
        advanceTimeBy((OtpVerifyStore.RESEND_COOLDOWN_SECONDS * 1000L) + 100L)
        assertEquals(0, s.state.resendCountdownSeconds)
        assertTrue(s.state.canResend)
    }

    // --- Resend intent ---

    @Test fun resend_isNoOpDuringCooldown() = runTest(dispatcher) {
        val (s, repo) = store()
        s.accept(OtpVerifyStore.Intent.Resend)
        assertTrue(repo.loginCalls.isEmpty())
        assertFalse(s.state.resending)
    }

    @Test fun resend_afterCooldown_callsLoginOtp_emitsLabel_restartsCountdown() = runTest(dispatcher) {
        val (s, repo) = store(userIdDigits = "81234567", country = Country.SG)
        val captured = mutableListOf<OtpVerifyStore.Label>()
        val collector = launch { s.labels.collect { captured += it } }

        // Advance past cooldown.
        advanceTimeBy((OtpVerifyStore.RESEND_COOLDOWN_SECONDS * 1000L) + 100L)
        assertTrue(s.state.canResend)

        s.accept(OtpVerifyStore.Intent.Resend)

        collector.cancel()
        assertEquals(listOf("81234567" to Country.SG), repo.loginCalls)
        assertEquals(1, captured.size)
        assertEquals(OtpVerifyStore.Label.OtpResent, captured.single())
        assertFalse(s.state.resending)
        // Countdown restarted: back at the top, canResend false again.
        assertEquals(OtpVerifyStore.RESEND_COOLDOWN_SECONDS, s.state.resendCountdownSeconds)
        assertFalse(s.state.canResend)
    }

    @Test fun resend_failure_setsErrorAndKeepsCooldownReady() = runTest(dispatcher) {
        val (s, _) = store(repo = FakeAuthRepository(loginResult = Outcome.Failure(AppError.Network)))
        advanceTimeBy((OtpVerifyStore.RESEND_COOLDOWN_SECONDS * 1000L) + 100L)
        assertTrue(s.state.canResend)

        s.accept(OtpVerifyStore.Intent.Resend)

        assertNotNull(s.state.error)
        assertFalse(s.state.resending)
        // Failure does NOT restart cooldown — user can retry immediately.
        assertEquals(0, s.state.resendCountdownSeconds)
        assertTrue(s.state.canResend)
    }
}
