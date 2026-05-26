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

    // ---- LoadNextPage ----

    @Test fun loadNextPage_success_appendsItems_andAdvancesOffset() = runTest(dispatcher) {
        val repo = FakeNotificationRepository(pages = mapOf(
            0 to Outcome.Success(NotificationPage(notifications(20, startId = 1), hasMore = true)),
            20 to Outcome.Success(NotificationPage(notifications(20, startId = 21), hasMore = true)),
        ))
        val store = makeStore(repo)
        store.accept(NotificationStore.Intent.LoadNextPage)
        assertEquals(40, store.state.items.size)
        assertEquals(40, store.state.offset)
        assertFalse(store.state.loadingMore)
    }

    @Test fun loadNextPage_noop_whenEndReached() = runTest(dispatcher) {
        val repo = FakeNotificationRepository(pages = mapOf(
            0 to Outcome.Success(NotificationPage(notifications(5), hasMore = false)),
        ))
        val store = makeStore(repo)
        assertTrue(store.state.endReached)
        store.accept(NotificationStore.Intent.LoadNextPage)
        assertEquals(5, store.state.items.size)
    }

    @Test fun loadNextPage_failure_setsError_retainsItems() = runTest(dispatcher) {
        val repo = FakeNotificationRepository(pages = mapOf(
            0 to Outcome.Success(NotificationPage(notifications(20), hasMore = true)),
            20 to Outcome.Failure(AppError.Network),
        ))
        val store = makeStore(repo)
        store.accept(NotificationStore.Intent.LoadNextPage)
        assertEquals(20, store.state.items.size)
        assertFalse(store.state.loadingMore)
        assertNotNull(store.state.error)
    }

    // ---- Refresh ----

    @Test fun refresh_resetsState_andReloadsFirstPage() = runTest(dispatcher) {
        val repo = FakeNotificationRepository(pages = mapOf(
            0 to Outcome.Success(NotificationPage(notifications(20), hasMore = true)),
        ))
        val store = makeStore(repo)
        assertEquals(20, store.state.items.size)

        repo.pages = mapOf(
            0 to Outcome.Success(NotificationPage(
                notifications(count = 5, startId = 100), hasMore = false)),
        )
        store.accept(NotificationStore.Intent.Refresh)

        assertEquals(5, store.state.items.size)
        assertTrue(store.state.endReached)
        assertEquals(5, store.state.offset)
    }

    // ---- TapItem ----

    @Test fun tapItem_PRINCIPAL_cacheHit_publishesNavigateLabel_andMarksRead() = runTest(dispatcher) {
        val notif = Notification(
            id = "N1", title = "T", description = "D",
            type = NotificationType.PRINCIPAL,
            payload = """{"PrincipalId":"P-1"}""",
            isRead = false, createdAt = "2026-05-26T00:00:00Z",
        )
        val repo = FakeNotificationRepository(pages = mapOf(
            0 to Outcome.Success(NotificationPage(listOf(notif), hasMore = false)),
        ))
        val princRepo = FakePrincipalRepository(byId = mapOf(
            "P-1" to Principal("P-1", "Acme", "", true),
        ))
        val store = makeStore(repo, princRepo)

        val labels = mutableListOf<NotificationStore.Label>()
        val collector = launch { store.labels.collect { labels += it } }

        store.accept(NotificationStore.Intent.TapItem(notif))

        collector.cancel()
        assertEquals(NotificationStore.Label.NavigateToPrincipal("P-1", "Acme"), labels.single())
        assertEquals(listOf("N1"), repo.markedRead)
        assertTrue(store.state.items.first().isRead)
    }

    @Test fun tapItem_PRINCIPAL_cacheMiss_publishesUnsupported_andSnackbar() = runTest(dispatcher) {
        val notif = Notification(
            id = "N1", title = "T", description = "D",
            type = NotificationType.PRINCIPAL,
            payload = """{"PrincipalId":"P-MISSING"}""",
            isRead = false, createdAt = "2026-05-26T00:00:00Z",
        )
        val repo = FakeNotificationRepository(pages = mapOf(
            0 to Outcome.Success(NotificationPage(listOf(notif), hasMore = false)),
        ))
        val store = makeStore(repo, FakePrincipalRepository())

        val labels = mutableListOf<NotificationStore.Label>()
        val collector = launch { store.labels.collect { labels += it } }

        store.accept(NotificationStore.Intent.TapItem(notif))

        collector.cancel()
        assertTrue(labels.any { it is NotificationStore.Label.NavigateUnsupportedType })
        assertTrue(labels.any {
            it is NotificationStore.Label.ShowSnackbar && it.text == "Brand not available"
        })
    }

    @Test fun tapItem_ORDER_publishesUnsupported_andMarksRead() = runTest(dispatcher) {
        val notif = Notification(
            id = "N2", title = "T", description = "D",
            type = NotificationType.ORDER,
            payload = "{}", isRead = false, createdAt = "2026-05-26T00:00:00Z",
        )
        val repo = FakeNotificationRepository(pages = mapOf(
            0 to Outcome.Success(NotificationPage(listOf(notif), hasMore = false)),
        ))
        val store = makeStore(repo)

        val labels = mutableListOf<NotificationStore.Label>()
        val collector = launch { store.labels.collect { labels += it } }

        store.accept(NotificationStore.Intent.TapItem(notif))

        collector.cancel()
        assertEquals(NotificationStore.Label.NavigateUnsupportedType, labels.single())
        assertEquals(listOf("N2"), repo.markedRead)
        assertTrue(store.state.items.first().isRead)
    }

    @Test fun tapItem_markReadFails_stillPublishesNav_andShowsSnackbar() = runTest(dispatcher) {
        val notif = Notification(
            id = "N3", title = "T", description = "D",
            type = NotificationType.ORDER,
            payload = "{}", isRead = false, createdAt = "2026-05-26T00:00:00Z",
        )
        val repo = FakeNotificationRepository(
            pages = mapOf(0 to Outcome.Success(NotificationPage(listOf(notif), hasMore = false))),
            markReadResult = Outcome.Failure(AppError.Network),
        )
        val store = makeStore(repo)

        val labels = mutableListOf<NotificationStore.Label>()
        val collector = launch { store.labels.collect { labels += it } }

        store.accept(NotificationStore.Intent.TapItem(notif))

        collector.cancel()
        assertTrue(labels.any { it is NotificationStore.Label.NavigateUnsupportedType })
        assertTrue(labels.any { it is NotificationStore.Label.ShowSnackbar })
    }

    @Test fun loadNextPage_twice_doesNotDoubleAppendFromSameOffset() = runTest(dispatcher) {
        // Fires LoadNextPage twice in succession. The endReached guard prevents
        // the second call from re-loading the same offset and double-appending.
        // (Pure in-flight reentrancy isn't observable under UnconfinedTestDispatcher
        // because the launch resumes synchronously on the same stack — but a
        // missing guard would still produce 60 items here, so this catches it.)
        val repo = FakeNotificationRepository(pages = mapOf(
            0 to Outcome.Success(NotificationPage(notifications(20), hasMore = true)),
            20 to Outcome.Success(NotificationPage(notifications(20, startId = 21), hasMore = false)),
        ))
        val store = makeStore(repo)
        store.accept(NotificationStore.Intent.LoadNextPage)
        assertEquals(40, store.state.items.size)
        assertTrue(store.state.endReached)
        store.accept(NotificationStore.Intent.LoadNextPage)
        assertEquals(40, store.state.items.size)
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
