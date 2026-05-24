package com.simplr.mykitta2.feature.auth

import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineBootstrapper
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import com.simplr.mykitta2.core.error.ErrorMapper
import com.simplr.mykitta2.core.result.Outcome
import com.simplr.mykitta2.data.repo.AuthRepository
import com.simplr.mykitta2.domain.Country
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

interface OtpVerifyStore : Store<OtpVerifyStore.Intent, OtpVerifyStore.State, OtpVerifyStore.Label> {

    data class State(
        val otp: String = "",
        val submitting: Boolean = false,
        val resending: Boolean = false,
        val resendCountdownSeconds: Int = RESEND_COOLDOWN_SECONDS,
        val error: String? = null,
    ) {
        val isValid: Boolean get() = otp.length == OTP_LENGTH
        val canResend: Boolean get() = !resending && !submitting && resendCountdownSeconds == 0
    }

    sealed interface Intent {
        data class OtpChanged(val raw: String) : Intent
        data object Submit : Intent
        data object Resend : Intent
    }

    sealed interface Label {
        data object NavigateToLoggedIn : Label
        data object OtpResent : Label
    }

    companion object {
        const val OTP_LENGTH = 4
        const val RESEND_COOLDOWN_SECONDS = 180   // 3 minutes
    }
}

/**
 * Caller-supplied parameters for this store — captured from the navigation route
 * (Destination.OtpVerify) so verify + resend calls go to the right country backend
 * with the right userId.
 */
data class OtpVerifyArgs(
    val userIdDigits: String,
    val country: Country,
)

class OtpVerifyStoreFactory(
    private val storeFactory: StoreFactory,
    private val authRepository: AuthRepository,
    private val args: OtpVerifyArgs,
) {
    fun create(): OtpVerifyStore =
        object : OtpVerifyStore,
            Store<OtpVerifyStore.Intent, OtpVerifyStore.State, OtpVerifyStore.Label>
            by storeFactory.create(
                name = "OtpVerifyStore",
                initialState = OtpVerifyStore.State(),
                bootstrapper = BootstrapperImpl(),
                executorFactory = { ExecutorImpl() },
                reducer = ReducerImpl,
            ) {}

    private sealed interface Action {
        // Initial 3-minute cooldown starts as soon as the screen opens — the SMS
        // was just sent by the prior LoginOtp call.
        data object StartInitialCountdown : Action
    }

    private sealed interface Message {
        data class OtpEdited(val otp: String) : Message
        data object SubmitStarted : Message
        data object SubmitFinished : Message
        data object ResendStarted : Message
        data object ResendFinished : Message
        data class CountdownTick(val seconds: Int) : Message
        data class ErrorSet(val message: String?) : Message
    }

    private inner class BootstrapperImpl : CoroutineBootstrapper<Action>() {
        override fun invoke() {
            dispatch(Action.StartInitialCountdown)
        }
    }

    private inner class ExecutorImpl :
        CoroutineExecutor<OtpVerifyStore.Intent, Action, OtpVerifyStore.State, Message, OtpVerifyStore.Label>() {

        // Held outside the launch{} so a fresh Resend success can cancel the
        // previous ticker before starting a new one.
        private var countdownJob: Job? = null

        override fun executeAction(action: Action) {
            when (action) {
                Action.StartInitialCountdown -> startCountdown()
            }
        }

        override fun executeIntent(intent: OtpVerifyStore.Intent) {
            val s = state()
            when (intent) {
                is OtpVerifyStore.Intent.OtpChanged -> {
                    val cleaned = intent.raw.filter(Char::isDigit).take(OtpVerifyStore.OTP_LENGTH)
                    dispatch(Message.OtpEdited(cleaned))
                }
                OtpVerifyStore.Intent.Submit -> {
                    if (s.submitting || !s.isValid) return
                    dispatch(Message.SubmitStarted)
                    scope.launch {
                        when (val outcome = authRepository.verifyLoginOtp(
                            userIdDigits = args.userIdDigits,
                            otp = s.otp,
                            country = args.country,
                        )) {
                            is Outcome.Success -> {
                                dispatch(Message.SubmitFinished)
                                publish(OtpVerifyStore.Label.NavigateToLoggedIn)
                            }
                            is Outcome.Failure -> {
                                dispatch(Message.SubmitFinished)
                                dispatch(Message.ErrorSet(ErrorMapper.message(outcome.error)))
                            }
                            Outcome.Idle, Outcome.Loading -> dispatch(Message.SubmitFinished)
                        }
                    }
                }
                OtpVerifyStore.Intent.Resend -> {
                    if (!s.canResend) return
                    dispatch(Message.ResendStarted)
                    scope.launch {
                        when (val outcome = authRepository.loginOtp(args.userIdDigits, args.country)) {
                            is Outcome.Success -> {
                                dispatch(Message.ResendFinished)
                                startCountdown()
                                publish(OtpVerifyStore.Label.OtpResent)
                            }
                            is Outcome.Failure -> {
                                dispatch(Message.ResendFinished)
                                dispatch(Message.ErrorSet(ErrorMapper.message(outcome.error)))
                                // Cooldown NOT restarted on failure — user can retry immediately.
                            }
                            Outcome.Idle, Outcome.Loading -> dispatch(Message.ResendFinished)
                        }
                    }
                }
            }
        }

        private fun startCountdown() {
            countdownJob?.cancel()
            countdownJob = scope.launch {
                for (remaining in OtpVerifyStore.RESEND_COOLDOWN_SECONDS downTo 0) {
                    dispatch(Message.CountdownTick(remaining))
                    if (remaining > 0) delay(1.seconds)
                }
            }
        }
    }

    private object ReducerImpl : Reducer<OtpVerifyStore.State, Message> {
        override fun OtpVerifyStore.State.reduce(msg: Message): OtpVerifyStore.State =
            when (msg) {
                is Message.OtpEdited -> copy(otp = msg.otp, error = null)
                Message.SubmitStarted -> copy(submitting = true, error = null)
                Message.SubmitFinished -> copy(submitting = false)
                Message.ResendStarted -> copy(resending = true, error = null)
                Message.ResendFinished -> copy(resending = false)
                is Message.CountdownTick -> copy(resendCountdownSeconds = msg.seconds)
                is Message.ErrorSet -> copy(error = msg.message)
            }
    }
}
