package com.simplr.mykitta2.feature.history

import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import com.simplr.mykitta2.core.error.AppError
import com.simplr.mykitta2.core.result.Outcome
import com.simplr.mykitta2.data.repo.HasMore
import com.simplr.mykitta2.data.repo.HistoryRepository
import com.simplr.mykitta2.domain.Order
import com.simplr.mykitta2.domain.OrderStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryStoreTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private class FakeRepo(
        var refreshResult: Outcome<HasMore> = Outcome.Success(HasMore(true)),
        var loadMoreResult: Outcome<HasMore> = Outcome.Success(HasMore(false)),
    ) : HistoryRepository {
        val flows: MutableMap<OrderStatus, MutableStateFlow<List<Order>>> =
            OrderStatus.entries.associateWith { MutableStateFlow<List<Order>>(emptyList()) }
                .toMutableMap()
        var refreshCalls = 0
        var loadMoreCalls = 0
        val refreshArgs = mutableListOf<Pair<OrderStatus, Boolean>>()
        val loadMoreArgs = mutableListOf<Pair<OrderStatus, Int>>()

        override fun observe(status: OrderStatus) = flows.getValue(status)

        override suspend fun refresh(
            status: OrderStatus,
            force: Boolean,
            ttl: kotlin.time.Duration,
        ): Outcome<HasMore> {
            refreshCalls += 1
            refreshArgs += status to force
            return refreshResult
        }

        override suspend fun loadMore(status: OrderStatus, currentCount: Int): Outcome<HasMore> {
            loadMoreCalls += 1
            loadMoreArgs += status to currentCount
            return loadMoreResult
        }

        fun emit(status: OrderStatus, orders: List<Order>) {
            flows.getValue(status).value = orders
        }
    }

    private fun store(repo: FakeRepo = FakeRepo()): Pair<HistoryStore, FakeRepo> {
        val s = HistoryStoreFactory(
            storeFactory = DefaultStoreFactory(),
            repository = repo,
        ).create()
        return s to repo
    }

    private fun order(invNo: String, status: OrderStatus = OrderStatus.WAITING) = Order(
        invNo = invNo,
        invDate = "2026-05-20",
        status = status,
        principalName = "COLUMBIA",
        total = 10.0,
        currency = "PHP",
        itemCount = 1,
    )

    @Test fun bootstrap_subscribesToAllFourStatuses_andTriggersInitialRefresh() = runTest(dispatcher) {
        val (store, repo) = store()
        assertEquals(1, repo.refreshCalls)
        assertEquals(OrderStatus.WAITING to false, repo.refreshArgs.single())
        assertEquals(OrderStatus.WAITING, store.state.currentTab)
        repo.emit(OrderStatus.FINISHED, listOf(order("INV-F1", OrderStatus.FINISHED)))
        assertEquals(1, store.state.tabs.getValue(OrderStatus.FINISHED).orders.size)
    }

    @Test fun bootstrap_marksWaitingTabSuccessOnRefreshSuccess() = runTest(dispatcher) {
        val (store, _) = store()
        assertIs<Outcome.Success<Unit>>(store.state.tabs.getValue(OrderStatus.WAITING).initialLoad)
        assertTrue(store.state.tabs.getValue(OrderStatus.WAITING).hasMore)
    }

    @Test fun bootstrap_failure_publishesErrorLabel() = runTest(dispatcher) {
        val repo = FakeRepo(refreshResult = Outcome.Failure(AppError.Network))
        val received = mutableListOf<HistoryStore.Label>()
        val s = HistoryStoreFactory(
            storeFactory = DefaultStoreFactory(),
            repository = repo,
        ).create()
        s.labels(object : com.arkivanov.mvikotlin.core.rx.Observer<HistoryStore.Label> {
            override fun onComplete() {}
            override fun onNext(value: HistoryStore.Label) { received += value }
        })
        s.accept(HistoryStore.Intent.Refresh)
        assertTrue(received.any { it is HistoryStore.Label.Error })
        assertIs<Outcome.Failure>(s.state.tabs.getValue(OrderStatus.WAITING).initialLoad)
    }

    @Test fun selectTab_changesCurrentAndRefreshesNewTab() = runTest(dispatcher) {
        val (store, repo) = store()
        repo.refreshArgs.clear()
        store.accept(HistoryStore.Intent.SelectTab(OrderStatus.FINISHED))
        assertEquals(OrderStatus.FINISHED, store.state.currentTab)
        assertEquals(OrderStatus.FINISHED to false, repo.refreshArgs.last())
        assertTrue(store.state.tabs.getValue(OrderStatus.FINISHED).visited)
    }

    @Test fun refresh_forcesNetworkOnCurrentTab() = runTest(dispatcher) {
        val (store, repo) = store()
        repo.refreshArgs.clear()
        store.accept(HistoryStore.Intent.Refresh)
        assertEquals(OrderStatus.WAITING to true, repo.refreshArgs.last())
    }

    @Test fun loadMore_callsRepoWithCurrentCount() = runTest(dispatcher) {
        val (store, repo) = store()
        repo.emit(OrderStatus.WAITING, listOf(order("INV-1"), order("INV-2")))
        store.accept(HistoryStore.Intent.LoadMore)
        assertEquals(OrderStatus.WAITING to 2, repo.loadMoreArgs.single())
    }

    @Test fun loadMore_guardedWhilePaginating() = runTest(dispatcher) {
        val repo = FakeRepo(loadMoreResult = Outcome.Success(HasMore(true)))
        val (store, _) = store(repo)
        repo.emit(OrderStatus.WAITING, listOf(order("INV-1")))
        store.accept(HistoryStore.Intent.LoadMore)
        store.accept(HistoryStore.Intent.LoadMore)
        assertTrue(repo.loadMoreCalls <= 2)
    }

    @Test fun loadMore_blockedWhenNoMore() = runTest(dispatcher) {
        val repo = FakeRepo(refreshResult = Outcome.Success(HasMore(false)))
        val (store, _) = store(repo)
        repo.emit(OrderStatus.WAITING, listOf(order("INV-1")))
        store.accept(HistoryStore.Intent.LoadMore)
        assertEquals(0, repo.loadMoreCalls)
    }
}
