package com.simplr.mykitta2.feature.auth

import com.arkivanov.mvikotlin.core.utils.JvmSerializable
import com.arkivanov.mvikotlin.extensions.coroutines.labels
import com.arkivanov.mvikotlin.extensions.coroutines.states
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import com.simplr.mykitta2.core.error.AppError
import com.simplr.mykitta2.core.result.Outcome
import com.simplr.mykitta2.data.prefs.CountryStore
import com.simplr.mykitta2.data.repo.AuthRepository
import com.simplr.mykitta2.domain.Country
import com.simplr.mykitta2.domain.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Uses `UnconfinedTestDispatcher` so MVIKotlin's `CoroutineBootstrapper` and
 * `CoroutineExecutor` (both defaulted to `Dispatchers.Main`) run synchronously —
 * which means state is observable immediately after `store.create()` / `accept(...)`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginOtpStoreTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    // ---- Fakes ----

    private class FakeCountryStore(private var stored: Country? = null) : CountryStore {
        val writes = mutableListOf<Country>()
        override suspend fun read(): Country? = stored
        override suspend fun write(country: Country) { writes += country; stored = country }
        override suspend fun clear() { stored = null }
    }

    private class FakeAuthRepository(
        var result: Outcome<Unit> = Outcome.Success(Unit),
    ) : AuthRepository, JvmSerializable {
        val calls = mutableListOf<Pair<String, Country>>()
        override suspend fun loginOtp(userIdDigits: String, country: Country): Outcome<Unit> {
            calls += userIdDigits to country
            return result
        }
        // Unused by LoginOtpStore — present to satisfy the interface.
        override suspend fun verifyLoginOtp(
            userIdDigits: String,
            otp: String,
            country: Country,
        ): Outcome<Session> = Outcome.Success(
            Session(userName = "user", supervisorCode = "S1", isSupervisor = false)
        )
        override suspend fun logout(): Outcome<Unit> = Outcome.Success(Unit)
    }

    private class Harness(
        val store: LoginOtpStore,
        val countryStore: FakeCountryStore,
        val repo: FakeAuthRepository,
    )

    private fun harness(
        savedCountry: Country? = null,
        detected: Country? = null,
        repo: FakeAuthRepository = FakeAuthRepository(),
    ): Harness {
        val countryStore = FakeCountryStore(savedCountry)
        val store = LoginOtpStoreFactory(
            storeFactory = DefaultStoreFactory(),
            countryStore = countryStore,
            countryDetector = { detected },
            authRepository = repo,
        ).create()
        return Harness(store, countryStore, repo)
    }

    // ---- Bootstrapper ----

    @Test fun bootstrap_usesSavedCountryWhenPresent() = runTest(dispatcher) {
        val h = harness(savedCountry = Country.SG, detected = Country.PH)
        assertEquals(Country.SG, h.store.state.country)
    }

    @Test fun bootstrap_fallsBackToDetectorWhenNothingSaved() = runTest(dispatcher) {
        val h = harness(savedCountry = null, detected = Country.SG)
        assertEquals(Country.SG, h.store.state.country)
    }

    @Test fun bootstrap_defaultsToPhWhenNothingSavedAndDetectorReturnsNull() = runTest(dispatcher) {
        val h = harness(savedCountry = null, detected = null)
        assertEquals(Country.PH, h.store.state.country)
    }

    // ---- PhoneChanged reducer ----

    @Test fun phoneChanged_setsFormattedAndValid() = runTest(dispatcher) {
        val h = harness(savedCountry = Country.PH)
        h.store.accept(LoginOtpStore.Intent.PhoneChanged("9171234567"))
        assertEquals("9171234567", h.store.state.phoneRaw)
        assertEquals("917 123 4567", h.store.state.phoneFormatted)
        assertTrue(h.store.state.isValid)
    }

    @Test fun phoneChanged_invalidWhenTooShort() = runTest(dispatcher) {
        val h = harness(savedCountry = Country.PH)
        h.store.accept(LoginOtpStore.Intent.PhoneChanged("917"))
        assertFalse(h.store.state.isValid)
    }

    @Test fun phoneChanged_clearsExistingError() = runTest(dispatcher) {
        val h = harness(
            savedCountry = Country.PH,
            repo = FakeAuthRepository(result = Outcome.Failure(AppError.Network)),
        )
        h.store.accept(LoginOtpStore.Intent.PhoneChanged("9171234567"))
        h.store.accept(LoginOtpStore.Intent.Submit)
        assertNotNull(h.store.state.error)
        h.store.accept(LoginOtpStore.Intent.PhoneChanged("9171234500"))
        assertNull(h.store.state.error)
    }

    // ---- Country selector ----

    @Test fun openCountrySelector_setsFlag() = runTest(dispatcher) {
        val h = harness(savedCountry = Country.PH)
        h.store.accept(LoginOtpStore.Intent.OpenCountrySelector)
        assertTrue(h.store.state.countrySelectorOpen)
    }

    @Test fun closeCountrySelector_clearsFlag() = runTest(dispatcher) {
        val h = harness(savedCountry = Country.PH)
        h.store.accept(LoginOtpStore.Intent.OpenCountrySelector)
        h.store.accept(LoginOtpStore.Intent.CloseCountrySelector)
        assertFalse(h.store.state.countrySelectorOpen)
    }

    @Test fun selectCountry_preservesDigitsAndReformatsAgainstNewMask() = runTest(dispatcher) {
        val h = harness(savedCountry = Country.PH)
        h.store.accept(LoginOtpStore.Intent.PhoneChanged("9171234567"))
        h.store.accept(LoginOtpStore.Intent.SelectCountry(Country.SG))
        assertEquals(Country.SG, h.store.state.country)
        assertEquals("91712345", h.store.state.phoneRaw) // PH 10 truncated to SG 8
        assertEquals("9171 2345", h.store.state.phoneFormatted)
        assertTrue(h.store.state.isValid)
        assertFalse(h.store.state.countrySelectorOpen)
    }

    @Test fun selectCountry_clearsExistingError() = runTest(dispatcher) {
        val h = harness(
            savedCountry = Country.PH,
            repo = FakeAuthRepository(result = Outcome.Failure(AppError.Network)),
        )
        h.store.accept(LoginOtpStore.Intent.PhoneChanged("9171234567"))
        h.store.accept(LoginOtpStore.Intent.Submit)
        assertNotNull(h.store.state.error)
        h.store.accept(LoginOtpStore.Intent.SelectCountry(Country.SG))
        assertNull(h.store.state.error)
    }

    // ---- Submit executor ----

    @Test fun submit_callsRepoWithCleanedDigitsAndCountry_andPersistsCountryOnSuccess() = runTest(dispatcher) {
        val h = harness(savedCountry = Country.PH)
        h.store.accept(LoginOtpStore.Intent.PhoneChanged("09171234567"))
        h.store.accept(LoginOtpStore.Intent.Submit)
        assertEquals(listOf("9171234567" to Country.PH), h.repo.calls)
        assertEquals(listOf(Country.PH), h.countryStore.writes)
        assertFalse(h.store.state.submitting)
    }

    @Test fun submit_isNoOpWhenInvalid() = runTest(dispatcher) {
        val h = harness(savedCountry = Country.PH)
        h.store.accept(LoginOtpStore.Intent.PhoneChanged("917"))
        h.store.accept(LoginOtpStore.Intent.Submit)
        assertTrue(h.repo.calls.isEmpty())
        assertTrue(h.countryStore.writes.isEmpty())
    }

    @Test fun submit_failure_setsErrorAndDoesNotPersistCountry() = runTest(dispatcher) {
        val h = harness(
            savedCountry = Country.PH,
            repo = FakeAuthRepository(result = Outcome.Failure(AppError.Network)),
        )
        h.store.accept(LoginOtpStore.Intent.PhoneChanged("9171234567"))
        h.store.accept(LoginOtpStore.Intent.Submit)
        assertNotNull(h.store.state.error)
        assertFalse(h.store.state.submitting)
        assertTrue(h.countryStore.writes.isEmpty())
    }

    @Test fun submit_emitsNavigateLabelOnSuccess() = runTest(dispatcher) {
        val h = harness(savedCountry = Country.PH)
        val captured = mutableListOf<LoginOtpStore.Label>()
        val collector = launch { h.store.labels.collect { captured += it } }

        h.store.accept(LoginOtpStore.Intent.PhoneChanged("9171234567"))
        h.store.accept(LoginOtpStore.Intent.Submit)

        collector.cancel()
        assertEquals(1, captured.size)
        val label = captured.single()
        assertIs<LoginOtpStore.Label.NavigateToOtpVerify>(label)
        assertEquals("+639171234567", label.phoneE164)
        assertEquals("9171234567", label.userIdDigits)
        assertEquals(Country.PH, label.country)
    }

    @Test fun submit_failure_doesNotEmitLabel() = runTest(dispatcher) {
        val h = harness(
            savedCountry = Country.PH,
            repo = FakeAuthRepository(result = Outcome.Failure(AppError.Network)),
        )
        val captured = mutableListOf<LoginOtpStore.Label>()
        val collector = launch { h.store.labels.collect { captured += it } }

        h.store.accept(LoginOtpStore.Intent.PhoneChanged("9171234567"))
        h.store.accept(LoginOtpStore.Intent.Submit)

        collector.cancel()
        assertTrue(captured.isEmpty())
    }

    @Test fun submit_passesSgCountryAndCleanedDigits() = runTest(dispatcher) {
        val h = harness(savedCountry = Country.SG)
        h.store.accept(LoginOtpStore.Intent.PhoneChanged("81234567"))
        h.store.accept(LoginOtpStore.Intent.Submit)
        assertEquals(listOf("81234567" to Country.SG), h.repo.calls)
        assertEquals(listOf(Country.SG), h.countryStore.writes)
    }

    // ---- Invariants ----

    @Test fun states_flowEmitsOnEachReducerApply() = runTest(dispatcher) {
        val h = harness(savedCountry = Country.PH)
        val seen = mutableListOf<LoginOtpStore.State>()
        val collector = launch { h.store.states.collect { seen += it } }

        h.store.accept(LoginOtpStore.Intent.PhoneChanged("917"))
        h.store.accept(LoginOtpStore.Intent.PhoneChanged("9171234567"))

        collector.cancel()
        // Initial state + two PhoneEdited applications. (Bootstrap also fires one Apply.)
        assertTrue(seen.size >= 3, "expected at least 3 emissions, got ${seen.size}")
        assertEquals("9171234567", seen.last().phoneRaw)
    }
}
