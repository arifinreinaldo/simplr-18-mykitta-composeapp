package com.simplr.mykitta2.feature.auth

import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineBootstrapper
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import com.simplr.mykitta2.core.error.ErrorMapper
import com.simplr.mykitta2.core.result.Outcome
import com.simplr.mykitta2.data.prefs.CountryStore
import com.simplr.mykitta2.data.repo.AuthRepository
import com.simplr.mykitta2.domain.Country
import kotlinx.coroutines.launch

interface LoginOtpStore : Store<LoginOtpStore.Intent, LoginOtpStore.State, LoginOtpStore.Label> {

    data class State(
        val country: Country = Country.PH,
        val countrySelectorOpen: Boolean = false,
        val phoneRaw: String = "",
        val phoneFormatted: String = "",
        val isValid: Boolean = false,
        val submitting: Boolean = false,
        val error: String? = null,
    )

    sealed interface Intent {
        data class PhoneChanged(val raw: String) : Intent
        data object OpenCountrySelector : Intent
        data object CloseCountrySelector : Intent
        data class SelectCountry(val country: Country) : Intent
        data object Submit : Intent
    }

    sealed interface Label {
        // Carries everything OtpVerify needs: phoneE164 for display, userIdDigits +
        // country for the verify API call (same per-country backend as login).
        data class NavigateToOtpVerify(
            val phoneE164: String,
            val userIdDigits: String,
            val country: Country,
        ) : Label
    }
}

class LoginOtpStoreFactory(
    private val storeFactory: StoreFactory,
    private val countryStore: CountryStore,
    private val countryDetector: CountryDetector,
    private val authRepository: AuthRepository,
) {
    fun create(): LoginOtpStore =
        object : LoginOtpStore,
            Store<LoginOtpStore.Intent, LoginOtpStore.State, LoginOtpStore.Label>
            by storeFactory.create(
                name = STORE_NAME,
                initialState = LoginOtpStore.State(),
                bootstrapper = BootstrapperImpl(),
                executorFactory = { ExecutorImpl() },
                reducer = ReducerImpl,
            ) {}

    private sealed interface Action {
        data class ApplyDetectedCountry(val country: Country) : Action
    }

    private sealed interface Message {
        data class CountryAndPhoneApplied(
            val country: Country,
            val raw: String,
            val formatted: String,
            val valid: Boolean,
        ) : Message
        data class CountrySelectorToggled(val open: Boolean) : Message
        // Edits clear the previous error in the same reducer pass — avoids a
        // second StateFlow emission and recomposition per keystroke.
        data class PhoneEdited(val raw: String, val formatted: String, val valid: Boolean) : Message
        data object SubmitStarted : Message
        data object SubmitFinished : Message
        data class ErrorSet(val message: String?) : Message
    }

    private inner class BootstrapperImpl : CoroutineBootstrapper<Action>() {
        override fun invoke() {
            scope.launch {
                val country = countryStore.read()
                    ?: countryDetector.detect()
                    ?: Country.PH
                dispatch(Action.ApplyDetectedCountry(country))
            }
        }
    }

    private inner class ExecutorImpl :
        CoroutineExecutor<LoginOtpStore.Intent, Action, LoginOtpStore.State, Message, LoginOtpStore.Label>() {

        override fun executeAction(action: Action) {
            when (action) {
                is Action.ApplyDetectedCountry ->
                    dispatch(
                        Message.CountryAndPhoneApplied(
                            country = action.country,
                            raw = "",
                            formatted = "",
                            valid = false,
                        )
                    )
            }
        }

        override fun executeIntent(intent: LoginOtpStore.Intent) {
            val s = state()
            when (intent) {
                is LoginOtpStore.Intent.PhoneChanged -> {
                    val cleaned = AuthCountryFormatter.clean(s.country, intent.raw)
                    val formatted = AuthCountryFormatter.format(s.country, cleaned)
                    val valid = AuthCountryFormatter.isValid(s.country, cleaned)
                    dispatch(Message.PhoneEdited(cleaned, formatted, valid))
                }
                LoginOtpStore.Intent.OpenCountrySelector ->
                    dispatch(Message.CountrySelectorToggled(true))
                LoginOtpStore.Intent.CloseCountrySelector ->
                    dispatch(Message.CountrySelectorToggled(false))
                is LoginOtpStore.Intent.SelectCountry -> {
                    val cleaned = AuthCountryFormatter.clean(intent.country, s.phoneRaw)
                    val formatted = AuthCountryFormatter.format(intent.country, cleaned)
                    val valid = AuthCountryFormatter.isValid(intent.country, cleaned)
                    dispatch(Message.CountryAndPhoneApplied(intent.country, cleaned, formatted, valid))
                    dispatch(Message.CountrySelectorToggled(false))
                }
                LoginOtpStore.Intent.Submit -> {
                    if (s.submitting || !s.isValid) return
                    val userIdDigits = AuthCountryFormatter.clean(s.country, s.phoneRaw)
                    val phoneE164 = AuthCountryFormatter.toE164(s.country, s.phoneRaw)
                    val country = s.country
                    dispatch(Message.SubmitStarted)
                    scope.launch {
                        when (val outcome = authRepository.loginOtp(userIdDigits, country)) {
                            is Outcome.Success -> {
                                countryStore.write(country)
                                dispatch(Message.SubmitFinished)
                                publish(
                                    LoginOtpStore.Label.NavigateToOtpVerify(
                                        phoneE164 = phoneE164,
                                        userIdDigits = userIdDigits,
                                        country = country,
                                    )
                                )
                            }
                            is Outcome.Failure -> {
                                dispatch(Message.SubmitFinished)
                                dispatch(Message.ErrorSet(ErrorMapper.message(outcome.error)))
                            }
                            Outcome.Idle, Outcome.Loading -> dispatch(Message.SubmitFinished)
                        }
                    }
                }
            }
        }
    }

    private object ReducerImpl : Reducer<LoginOtpStore.State, Message> {
        override fun LoginOtpStore.State.reduce(msg: Message): LoginOtpStore.State =
            when (msg) {
                // SelectCountry also clears error — switching country implies "try again",
                // so any prior failure message would be stale.
                is Message.CountryAndPhoneApplied -> copy(
                    country = msg.country,
                    phoneRaw = msg.raw,
                    phoneFormatted = msg.formatted,
                    isValid = msg.valid,
                    error = null,
                )
                is Message.CountrySelectorToggled -> copy(countrySelectorOpen = msg.open)
                // PhoneEdited clears error inline so each keystroke is exactly one state emission.
                is Message.PhoneEdited -> copy(
                    phoneRaw = msg.raw,
                    phoneFormatted = msg.formatted,
                    isValid = msg.valid,
                    error = null,
                )
                Message.SubmitStarted -> copy(submitting = true, error = null)
                Message.SubmitFinished -> copy(submitting = false)
                is Message.ErrorSet -> copy(error = msg.message)
            }
    }

    private companion object {
        const val STORE_NAME = "LoginOtpStore"
    }
}
