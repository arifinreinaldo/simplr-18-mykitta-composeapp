package com.simplr.mykitta2.feature.profile

import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineBootstrapper
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import com.simplr.mykitta2.core.error.ErrorMapper
import com.simplr.mykitta2.core.result.Outcome
import com.simplr.mykitta2.data.repo.ProfileRepository
import com.simplr.mykitta2.domain.Profile
import kotlinx.coroutines.launch

interface ProfileStore : Store<ProfileStore.Intent, ProfileStore.State, ProfileStore.Label> {

    data class State(
        /**
         * Header line — "Welcome back, ${headerName}". Populated from
         * `profile.custName` once the first cache hit or network result lands.
         * Empty until then; the screen renders a "Welcome back, there" fallback
         * to avoid showing a stale or technical placeholder.
         *
         * Session.userName is intentionally NOT used here — it's the legacy
         * phone number, not a display name, so seeding from it would cause an
         * awkward phone-number flash before the real custName arrives.
         */
        val headerName: String = "",
        /** Latest profile detail, either from cache or refresh. `null` until
         *  the first cache hit or successful network fetch. */
        val profile: Profile? = null,
        val loading: Boolean = true,
        val refreshing: Boolean = false,
        val error: String? = null,
    )

    sealed interface Intent {
        /** Pull-to-refresh / explicit refresh — bypasses the 24h cache TTL. */
        data object Refresh : Intent
    }

    sealed interface Label {
        // No navigation labels today — menu rows are still in-screen
        // callbacks. Promote to labels when the rows wire up to real
        // destinations (Profile detail edit, Shipment Address, History, …).
    }
}

class ProfileStoreFactory(
    private val storeFactory: StoreFactory,
    private val profileRepository: ProfileRepository,
) {
    fun create(): ProfileStore =
        object : ProfileStore,
            Store<ProfileStore.Intent, ProfileStore.State, ProfileStore.Label>
            by storeFactory.create(
                name = "ProfileStore",
                initialState = ProfileStore.State(),
                bootstrapper = BootstrapperImpl(),
                executorFactory = { ExecutorImpl() },
                reducer = ReducerImpl,
            ) {}

    private sealed interface Action {
        data object Load : Action
    }

    private sealed interface Message {
        data class ProfileLoaded(val profile: Profile) : Message
        data class LoadingChanged(val loading: Boolean) : Message
        data object RefreshStarted : Message
        data object RefreshFinished : Message
        data class ErrorSet(val message: String?) : Message
    }

    private inner class BootstrapperImpl : CoroutineBootstrapper<Action>() {
        override fun invoke() {
            dispatch(Action.Load)
        }
    }

    private inner class ExecutorImpl :
        CoroutineExecutor<ProfileStore.Intent, Action, ProfileStore.State, Message, ProfileStore.Label>() {

        override fun executeAction(action: Action) {
            when (action) {
                Action.Load -> initialLoad()
            }
        }

        override fun executeIntent(intent: ProfileStore.Intent) {
            when (intent) {
                ProfileStore.Intent.Refresh -> {
                    if (state().refreshing) return
                    forceRefresh()
                }
            }
        }

        /**
         * Bootstrap path: ask the repository for the profile. Cache hits
         * (typical case after the first login + 24h TTL) make this a single
         * synchronous read that paints the header on the first frame.
         */
        private fun initialLoad() {
            scope.launch {
                when (val outcome = profileRepository.loadProfile()) {
                    is Outcome.Success -> dispatch(Message.ProfileLoaded(outcome.value))
                    is Outcome.Failure -> dispatch(Message.ErrorSet(ErrorMapper.message(outcome.error)))
                    Outcome.Idle, Outcome.Loading -> Unit
                }
                dispatch(Message.LoadingChanged(false))
            }
        }

        private fun forceRefresh() {
            dispatch(Message.RefreshStarted)
            scope.launch {
                when (val outcome = profileRepository.refresh()) {
                    is Outcome.Success -> {
                        dispatch(Message.ProfileLoaded(outcome.value))
                        dispatch(Message.ErrorSet(null))
                    }
                    is Outcome.Failure ->
                        dispatch(Message.ErrorSet(ErrorMapper.message(outcome.error)))
                    Outcome.Idle, Outcome.Loading -> Unit
                }
                dispatch(Message.RefreshFinished)
            }
        }
    }

    private object ReducerImpl : Reducer<ProfileStore.State, Message> {
        override fun ProfileStore.State.reduce(msg: Message): ProfileStore.State = when (msg) {
            is Message.ProfileLoaded -> copy(
                profile = msg.profile,
                // Header tracks the freshly loaded custName; if the backend
                // returned a blank custName we keep whatever the header was
                // (typically empty → screen shows the generic fallback).
                headerName = msg.profile.custName.takeIf { it.isNotBlank() } ?: headerName,
            )
            is Message.LoadingChanged -> copy(loading = msg.loading)
            Message.RefreshStarted -> copy(refreshing = true, error = null)
            Message.RefreshFinished -> copy(refreshing = false)
            is Message.ErrorSet -> copy(error = msg.message)
        }
    }
}
