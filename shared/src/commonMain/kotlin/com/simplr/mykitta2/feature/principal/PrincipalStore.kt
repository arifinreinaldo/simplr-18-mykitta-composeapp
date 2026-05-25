package com.simplr.mykitta2.feature.principal

import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineBootstrapper
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import com.simplr.mykitta2.core.error.ErrorMapper
import com.simplr.mykitta2.core.result.Outcome
import com.simplr.mykitta2.data.repo.PrincipalRepository
import com.simplr.mykitta2.domain.Principal
import kotlinx.coroutines.launch

interface PrincipalStore : Store<PrincipalStore.Intent, PrincipalStore.State, PrincipalStore.Label> {

    data class State(
        val principals: List<Principal> = emptyList(),
        val loading: Boolean = true,
        val refreshing: Boolean = false,
        val error: String? = null,
    )

    sealed interface Intent {
        data object Refresh : Intent
        data class PrincipalClicked(val principal: Principal) : Intent
    }

    sealed interface Label {
        /** Tap → open the principal-scoped catalog. Navigation is the screen's
         *  responsibility (it owns the NavController); the store just publishes
         *  the request. */
        data class OpenCatalog(val principal: Principal) : Label
    }
}

class PrincipalStoreFactory(
    private val storeFactory: StoreFactory,
    private val principalRepository: PrincipalRepository,
) {
    fun create(): PrincipalStore =
        object : PrincipalStore,
            Store<PrincipalStore.Intent, PrincipalStore.State, PrincipalStore.Label>
            by storeFactory.create(
                name = "PrincipalStore",
                initialState = PrincipalStore.State(),
                bootstrapper = BootstrapperImpl(),
                executorFactory = { ExecutorImpl() },
                reducer = ReducerImpl,
            ) {}

    private sealed interface Action {
        data object Observe : Action
        data object Refresh : Action
    }

    private sealed interface Message {
        data class CacheUpdated(val principals: List<Principal>) : Message
        data object RefreshStarted : Message
        data object RefreshFinished : Message
        data class ErrorSet(val message: String?) : Message
    }

    private inner class BootstrapperImpl : CoroutineBootstrapper<Action>() {
        override fun invoke() {
            // Cache flow first — paint stale data immediately while the network
            // refresh runs in parallel.
            dispatch(Action.Observe)
            dispatch(Action.Refresh)
        }
    }

    private inner class ExecutorImpl :
        CoroutineExecutor<PrincipalStore.Intent, Action, PrincipalStore.State, Message, PrincipalStore.Label>() {

        override fun executeAction(action: Action) {
            when (action) {
                Action.Observe -> observeCache()
                Action.Refresh -> refreshFromNetwork()
            }
        }

        override fun executeIntent(intent: PrincipalStore.Intent) {
            when (intent) {
                PrincipalStore.Intent.Refresh -> {
                    if (state().refreshing) return
                    refreshFromNetwork()
                }
                is PrincipalStore.Intent.PrincipalClicked ->
                    publish(PrincipalStore.Label.OpenCatalog(intent.principal))
            }
        }

        private fun observeCache() {
            scope.launch {
                principalRepository.observeAll().collect { list ->
                    dispatch(Message.CacheUpdated(list.filter { it.isActive }))
                }
            }
        }

        private fun refreshFromNetwork() {
            dispatch(Message.RefreshStarted)
            scope.launch {
                when (val outcome = principalRepository.refresh()) {
                    is Outcome.Success -> dispatch(Message.ErrorSet(null))
                    is Outcome.Failure -> dispatch(Message.ErrorSet(ErrorMapper.message(outcome.error)))
                    Outcome.Idle, Outcome.Loading -> Unit
                }
                dispatch(Message.RefreshFinished)
            }
        }

    }

    private object ReducerImpl : Reducer<PrincipalStore.State, Message> {
        override fun PrincipalStore.State.reduce(msg: Message): PrincipalStore.State = when (msg) {
            is Message.CacheUpdated -> copy(principals = msg.principals, loading = false)
            Message.RefreshStarted -> copy(refreshing = true, error = null)
            Message.RefreshFinished -> copy(refreshing = false)
            is Message.ErrorSet -> copy(error = msg.message)
        }
    }
}
