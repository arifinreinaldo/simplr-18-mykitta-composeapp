package com.simplr.mykitta2.feature.notification

import com.arkivanov.mvikotlin.extensions.coroutines.labels
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import com.simplr.mykitta2.core.error.AppError
import com.simplr.mykitta2.core.error.ErrorMapper
import com.simplr.mykitta2.core.result.Outcome
import com.simplr.mykitta2.data.repo.NotificationPage
import com.simplr.mykitta2.data.repo.NotificationRepository
import com.simplr.mykitta2.data.repo.PrincipalRepository
import com.simplr.mykitta2.domain.Notification
import com.simplr.mykitta2.domain.NotificationType
import com.simplr.mykitta2.domain.Principal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationStoreTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    // ---- Bootstrap / loadPage ----

    @Test fun bootstrap_loadsFirstPage_andTransitionsOutOfFirstLoad() = runTest(dispatcher) {
        val repo = FakeNotificationRepository(pages = mapOf(
            0 to Outcome.Success(NotificationPage(
                items = notifications(count = 20), hasMore = true, fromCache = false)),
        ))
        val store = makeStore(repo)
        assertEquals(20, store.state.items.size)
        assertFalse(store.state.firstLoadInFlight)
        assertFalse(store.state.endReached)
        assertNull(store.state.error)
    }

    @Test fun bootstrap_shortPage_setsEndReached() = runTest(dispatcher) {
        val repo = FakeNotificationRepository(pages = mapOf(
            0 to Outcome.Success(NotificationPage(
                items = notifications(count = 5), hasMore = false, fromCache = false)),
        ))
        val store = makeStore(repo)
        assertEquals(5, store.state.items.size)
        assertTrue(store.state.endReached)
    }

    @Test fun bootstrap_networkFailure_setsError_andClearsLoading() = runTest(dispatcher) {
        val repo = FakeNotificationRepository(pages = mapOf(
            0 to Outcome.Failure(AppError.Network),
        ))
        val store = makeStore(repo)
        assertEquals(0, store.state.items.size)
        assertFalse(store.state.firstLoadInFlight)
        assertEquals(ErrorMapper.message(AppError.Network), store.state.error)
    }

    @Test fun bootstrap_returnsFromCache_setsShowingCache() = runTest(dispatcher) {
        val repo = FakeNotificationRepository(pages = mapOf(
            0 to Outcome.Success(NotificationPage(
                items = notifications(count = 3), hasMore = false, fromCache = true)),
        ))
        val store = makeStore(repo)
        assertTrue(store.state.showingCache)
    }

    // ---- Helpers ----

    private fun makeStore(
        notificationRepository: NotificationRepository = FakeNotificationRepository(),
        principalRepository: PrincipalRepository = FakePrincipalRepository(),
    ): NotificationStore = NotificationStoreFactory(
        storeFactory = DefaultStoreFactory(),
        notificationRepository = notificationRepository,
        principalRepository = principalRepository,
    ).create()

    private fun notifications(
        count: Int,
        startId: Int = 1,
        type: NotificationType = NotificationType.ORDER,
        payload: String = "{}",
    ) = (0 until count).map { i ->
        Notification(
            id = "N${startId + i}", title = "T${startId + i}", description = "D",
            type = type, payload = payload, isRead = false,
            createdAt = "2026-05-${10 + (i % 20)}T00:00:00Z",
        )
    }

    class FakeNotificationRepository(
        initialCount: Int = 0,
        var pages: Map<Int, Outcome<NotificationPage>> = emptyMap(),
        val markReadResult: Outcome<Unit> = Outcome.Success(Unit),
    ) : NotificationRepository {
        private val _count = MutableStateFlow(initialCount)
        override val unreadCount: StateFlow<Int> = _count.asStateFlow()
        var markedRead = mutableListOf<String>()
            private set

        override suspend fun refreshCount(): Outcome<Int> = Outcome.Success(_count.value)
        override suspend fun loadPage(offset: Int): Outcome<NotificationPage> =
            pages[offset] ?: Outcome.Success(NotificationPage(emptyList(), hasMore = false))
        override suspend fun markAsRead(id: String): Outcome<Unit> {
            markedRead += id
            if (markReadResult is Outcome.Success) _count.value = (_count.value - 1).coerceAtLeast(0)
            return markReadResult
        }
    }

    class FakePrincipalRepository(
        val byId: Map<String, Principal> = emptyMap(),
    ) : PrincipalRepository {
        override fun observeAll(): Flow<List<Principal>> = emptyFlow()
        override suspend fun refresh(): Outcome<Unit> = Outcome.Success(Unit)
        override suspend fun findById(principalId: String): Principal? = byId[principalId]
    }
}
