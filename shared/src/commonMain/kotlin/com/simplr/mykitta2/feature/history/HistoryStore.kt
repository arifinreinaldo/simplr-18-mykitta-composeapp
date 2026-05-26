package com.simplr.mykitta2.feature.history

import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineBootstrapper
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import com.simplr.mykitta2.core.error.ErrorMapper
import com.simplr.mykitta2.core.result.Outcome
import com.simplr.mykitta2.data.repo.HasMore
import com.simplr.mykitta2.data.repo.HistoryRepository
import com.simplr.mykitta2.domain.Order
import com.simplr.mykitta2.domain.OrderStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

interface HistoryStore : Store<HistoryStore.Intent, HistoryStore.State, HistoryStore.Label> {

    data class State(
        val currentTab: OrderStatus = OrderStatus.WAITING,
        val tabs: Map<OrderStatus, TabState> =
            OrderStatus.entries.associateWith { TabState() },
    )

    data class TabState(
        val orders: List<Order> = emptyList(),
        val initialLoad: Outcome<Unit> = Outcome.Idle,
        val pagination: Outcome<Unit> = Outcome.Idle,
        val hasMore: Boolean = true,
        val visited: Boolean = false,
    )

    sealed interface Intent {
        data class SelectTab(val status: OrderStatus) : Intent
        data object Refresh : Intent
        data object LoadMore : Intent
    }

    sealed interface Label {
        data class Error(val message: String) : Label
    }
}

class HistoryStoreFactory(
    private val storeFactory: StoreFactory,
    private val repository: HistoryRepository,
) {
    fun create(): HistoryStore =
        object : HistoryStore,
            Store<HistoryStore.Intent, HistoryStore.State, HistoryStore.Label>
            by storeFactory.create(
                name = "HistoryStore",
                initialState = HistoryStore.State(),
                bootstrapper = BootstrapperImpl(),
                executorFactory = { ExecutorImpl() },
                reducer = ReducerImpl,
            ) {}

    private sealed interface Action {
        data object Init : Action
    }

    private sealed interface Message {
        data class OrdersChanged(val status: OrderStatus, val orders: List<Order>) : Message
        data class TabSelected(val status: OrderStatus) : Message
        data class Visited(val status: OrderStatus) : Message
        data class InitialLoadChanged(val status: OrderStatus, val outcome: Outcome<Unit>) : Message
        data class PaginationChanged(val status: OrderStatus, val outcome: Outcome<Unit>) : Message
        data class HasMoreChanged(val status: OrderStatus, val hasMore: Boolean) : Message
    }

    private inner class BootstrapperImpl : CoroutineBootstrapper<Action>() {
        override fun invoke() {
            dispatch(Action.Init)
        }
    }

    private inner class ExecutorImpl :
        CoroutineExecutor<HistoryStore.Intent, Action, HistoryStore.State, Message, HistoryStore.Label>() {

        private val refreshJobs: MutableMap<OrderStatus, Job> = mutableMapOf()

        override fun executeAction(action: Action) {
            when (action) {
                Action.Init -> {
                    subscribeAll()
                    refreshTab(OrderStatus.WAITING, force = false)
                }
            }
        }

        override fun executeIntent(intent: HistoryStore.Intent) {
            when (intent) {
                is HistoryStore.Intent.SelectTab -> onSelectTab(intent.status)
                HistoryStore.Intent.Refresh -> refreshTab(state().currentTab, force = true)
                HistoryStore.Intent.LoadMore -> onLoadMore()
            }
        }

        private fun subscribeAll() {
            // Repository already runs the SQLDelight cursor->List mapping on
            // Dispatchers.Default; downstream stays on the store's scope so
            // test schedulers can drive emissions deterministically.
            OrderStatus.entries.forEach { status ->
                repository.observe(status)
                    .onEach { orders -> dispatch(Message.OrdersChanged(status, orders)) }
                    .launchIn(scope)
            }
        }

        private fun onSelectTab(status: OrderStatus) {
            if (state().currentTab != status) {
                dispatch(Message.TabSelected(status))
            }
            val tab = state().tabs.getValue(status)
            if (!tab.visited) {
                dispatch(Message.Visited(status))
            }
            refreshTab(status, force = false)
        }

        private fun refreshTab(status: OrderStatus, force: Boolean) {
            refreshJobs[status]?.cancel()
            dispatch(Message.InitialLoadChanged(status, Outcome.Loading))
            refreshJobs[status] = scope.launch {
                when (val outcome = repository.refresh(status, force = force)) {
                    is Outcome.Success<HasMore> -> {
                        dispatch(Message.HasMoreChanged(status, outcome.value.hasMore))
                        dispatch(Message.InitialLoadChanged(status, Outcome.Success(Unit)))
                    }
                    is Outcome.Failure -> {
                        dispatch(Message.InitialLoadChanged(status, Outcome.Failure(outcome.error)))
                        publish(HistoryStore.Label.Error(ErrorMapper.message(outcome.error)))
                    }
                    Outcome.Idle, Outcome.Loading -> Unit
                }
            }
        }

        private fun onLoadMore() {
            val current = state().currentTab
            val tab = state().tabs.getValue(current)
            if (tab.pagination is Outcome.Loading) return
            if (!tab.hasMore) return
            if (tab.initialLoad !is Outcome.Success) return

            dispatch(Message.PaginationChanged(current, Outcome.Loading))
            scope.launch {
                when (val outcome = repository.loadMore(current, tab.orders.size)) {
                    is Outcome.Success<HasMore> -> {
                        dispatch(Message.HasMoreChanged(current, outcome.value.hasMore))
                        dispatch(Message.PaginationChanged(current, Outcome.Success(Unit)))
                    }
                    is Outcome.Failure -> {
                        dispatch(Message.PaginationChanged(current, Outcome.Failure(outcome.error)))
                        publish(HistoryStore.Label.Error(ErrorMapper.message(outcome.error)))
                    }
                    Outcome.Idle, Outcome.Loading -> Unit
                }
            }
        }
    }

    private object ReducerImpl : Reducer<HistoryStore.State, Message> {
        override fun HistoryStore.State.reduce(msg: Message): HistoryStore.State = when (msg) {
            is Message.OrdersChanged -> patch(msg.status) { copy(orders = msg.orders) }
            is Message.TabSelected -> copy(currentTab = msg.status)
            is Message.Visited -> patch(msg.status) { copy(visited = true) }
            is Message.InitialLoadChanged -> patch(msg.status) { copy(initialLoad = msg.outcome) }
            is Message.PaginationChanged -> patch(msg.status) { copy(pagination = msg.outcome) }
            is Message.HasMoreChanged -> patch(msg.status) { copy(hasMore = msg.hasMore) }
        }

        private inline fun HistoryStore.State.patch(
            status: OrderStatus,
            block: HistoryStore.TabState.() -> HistoryStore.TabState,
        ): HistoryStore.State {
            val current = tabs.getValue(status)
            val updated = current.block()
            return copy(tabs = tabs + (status to updated))
        }
    }
}
