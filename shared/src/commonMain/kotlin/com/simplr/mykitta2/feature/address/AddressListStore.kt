package com.simplr.mykitta2.feature.address

import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineBootstrapper
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import com.simplr.mykitta2.core.error.ErrorMapper
import com.simplr.mykitta2.core.result.Outcome
import com.simplr.mykitta2.data.repo.AddressRepository
import com.simplr.mykitta2.domain.Address
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

interface AddressListStore : Store<AddressListStore.Intent, AddressListStore.State, AddressListStore.Label> {

    data class State(
        val addresses: List<Address> = emptyList(),
        val initialLoading: Boolean = true,
        val refreshing: Boolean = false,
        val error: String? = null,
    )

    sealed interface Intent {
        data object Refresh : Intent
        data object DismissError : Intent
        data object AddTapped : Intent
        data class RowTapped(val customerAddressId: String) : Intent
    }

    sealed interface Label {
        data class OpenForm(val customerAddressId: String?) : Label
    }
}

class AddressListStoreFactory(
    private val storeFactory: StoreFactory,
    private val repository: AddressRepository,
) {
    fun create(): AddressListStore =
        object : AddressListStore,
            Store<AddressListStore.Intent, AddressListStore.State, AddressListStore.Label>
            by storeFactory.create(
                name = "AddressListStore",
                initialState = AddressListStore.State(),
                bootstrapper = BootstrapperImpl(),
                executorFactory = { ExecutorImpl() },
                reducer = ReducerImpl,
            ) {}

    private sealed interface Action {
        data object Init : Action
    }

    private sealed interface Message {
        data class AddressesChanged(val addresses: List<Address>) : Message
        data object InitialLoadStarted : Message
        data object InitialLoadFinished : Message
        data object RefreshStarted : Message
        data object RefreshFinished : Message
        data class ErrorSet(val error: String?) : Message
    }

    private inner class BootstrapperImpl : CoroutineBootstrapper<Action>() {
        override fun invoke() {
            dispatch(Action.Init)
        }
    }

    private inner class ExecutorImpl :
        CoroutineExecutor<AddressListStore.Intent, Action, AddressListStore.State, Message, AddressListStore.Label>() {

        override fun executeAction(action: Action) {
            when (action) {
                Action.Init -> {
                    subscribe()
                    initialRefresh()
                }
            }
        }

        override fun executeIntent(intent: AddressListStore.Intent) {
            when (intent) {
                AddressListStore.Intent.Refresh -> refresh(force = true)
                AddressListStore.Intent.DismissError -> dispatch(Message.ErrorSet(null))
                AddressListStore.Intent.AddTapped ->
                    publish(AddressListStore.Label.OpenForm(customerAddressId = null))
                is AddressListStore.Intent.RowTapped ->
                    publish(AddressListStore.Label.OpenForm(intent.customerAddressId))
            }
        }

        private fun subscribe() {
            repository.observe()
                .onEach { dispatch(Message.AddressesChanged(it)) }
                .launchIn(scope)
        }

        private fun initialRefresh() {
            dispatch(Message.InitialLoadStarted)
            scope.launch {
                when (val outcome = repository.refresh(force = false)) {
                    is Outcome.Failure ->
                        dispatch(Message.ErrorSet(ErrorMapper.message(outcome.error)))
                    Outcome.Idle, Outcome.Loading, is Outcome.Success -> Unit
                }
                dispatch(Message.InitialLoadFinished)
            }
        }

        private fun refresh(force: Boolean) {
            dispatch(Message.RefreshStarted)
            scope.launch {
                when (val outcome = repository.refresh(force = force)) {
                    is Outcome.Failure ->
                        dispatch(Message.ErrorSet(ErrorMapper.message(outcome.error)))
                    is Outcome.Success -> dispatch(Message.ErrorSet(null))
                    Outcome.Idle, Outcome.Loading -> Unit
                }
                dispatch(Message.RefreshFinished)
            }
        }
    }

    private object ReducerImpl : Reducer<AddressListStore.State, Message> {
        override fun AddressListStore.State.reduce(msg: Message): AddressListStore.State = when (msg) {
            is Message.AddressesChanged -> copy(addresses = msg.addresses)
            Message.InitialLoadStarted -> copy(initialLoading = true)
            Message.InitialLoadFinished -> copy(initialLoading = false)
            Message.RefreshStarted -> copy(refreshing = true)
            Message.RefreshFinished -> copy(refreshing = false)
            is Message.ErrorSet -> copy(error = msg.error)
        }
    }
}
