package com.simplr.mykitta2.feature.splash

import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineBootstrapper
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import com.simplr.mykitta2.core.logging.AppLogger
import com.simplr.mykitta2.data.prefs.SessionStore
import com.simplr.mykitta2.data.prefs.TokenStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

interface SplashStore : Store<SplashStore.Intent, SplashStore.State, SplashStore.Label> {

    enum class Destination { Home, Login }

    sealed interface State {
        /** Init in flight. View plays the entrance once, then pulses until state changes. */
        data object Working : State

        /** Init complete. Parent observes [Label.NavigateTo] to leave the splash. */
        data class Ready(val destination: Destination) : State
    }

    /** No user-driven intents today; splash is fully bootstrap-driven. */
    sealed interface Intent

    sealed interface Label {
        data class NavigateTo(val destination: Destination) : Label
    }
}

class SplashStoreFactory(
    private val storeFactory: StoreFactory,
    private val tokenStore: TokenStore,
    private val sessionStore: SessionStore,
    /**
     * Best-effort startup work — runs once during the splash window.
     * Today: warms the SQLite driver so the first feature query doesn't pay
     * the driver-open + PRAGMA cost on the UI thread. Wrap any future startup
     * touches (locale preload, feature flags fetch, etc.) into this lambda.
     * Failures are logged and swallowed — they never gate the auth decision.
     */
    private val warmup: suspend () -> Unit,
    private val appLogger: AppLogger,
    private val minSplashDuration: Duration = DEFAULT_MIN_SPLASH,
) {
    fun create(): SplashStore =
        object : SplashStore,
            Store<SplashStore.Intent, SplashStore.State, SplashStore.Label>
            by storeFactory.create(
                name = "SplashStore",
                initialState = SplashStore.State.Working,
                bootstrapper = BootstrapperImpl(),
                executorFactory = { ExecutorImpl() },
                reducer = ReducerImpl,
            ) {}

    private sealed interface Action {
        data object Init : Action
    }

    private sealed interface Message {
        data class Settled(val destination: SplashStore.Destination) : Message
    }

    private inner class BootstrapperImpl : CoroutineBootstrapper<Action>() {
        override fun invoke() {
            dispatch(Action.Init)
        }
    }

    private inner class ExecutorImpl :
        CoroutineExecutor<SplashStore.Intent, Action, SplashStore.State, Message, SplashStore.Label>() {

        override fun executeAction(action: Action) {
            when (action) {
                Action.Init -> runInit()
            }
        }

        override fun executeIntent(intent: SplashStore.Intent) = Unit

        // Sequential rather than parallel: both prefs reads are synchronous under
        // the hood (Settings → SharedPreferences / NSUserDefaults) and the DB
        // touch on the empty Meta table is sub-millisecond. The launch+joinAll
        // ceremony would cost more than the work.
        private fun runInit() {
            scope.launch {
                val mark = TimeSource.Monotonic.markNow()

                val token = readOrNull("tokenStore") { tokenStore.read() }
                val session = readOrNull("sessionStore") { sessionStore.read() }

                try {
                    warmup()
                } catch (t: CancellationException) {
                    throw t
                } catch (t: Throwable) {
                    appLogger.w(TAG, t) { "Startup warm-up failed; continuing" }
                }

                val destination = if (token != null && session != null) {
                    SplashStore.Destination.Home
                } else {
                    SplashStore.Destination.Login
                }

                val remaining = minSplashDuration - mark.elapsedNow()
                if (remaining > Duration.ZERO) delay(remaining)

                dispatch(Message.Settled(destination))
                publish(SplashStore.Label.NavigateTo(destination))
            }
        }

        private suspend inline fun <T> readOrNull(
            label: String,
            crossinline block: suspend () -> T?,
        ): T? = try {
            block()
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            appLogger.w(TAG, t) { "$label.read() failed; treating as missing" }
            null
        }
    }

    private object ReducerImpl : Reducer<SplashStore.State, Message> {
        override fun SplashStore.State.reduce(msg: Message): SplashStore.State = when (msg) {
            is Message.Settled -> SplashStore.State.Ready(msg.destination)
        }
    }

    private companion object {
        const val TAG = "SplashStore"
        val DEFAULT_MIN_SPLASH = 800.milliseconds
    }
}
