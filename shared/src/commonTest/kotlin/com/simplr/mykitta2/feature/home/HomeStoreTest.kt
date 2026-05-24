package com.simplr.mykitta2.feature.home

import com.arkivanov.mvikotlin.core.utils.JvmSerializable
import com.arkivanov.mvikotlin.extensions.coroutines.labels
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import com.simplr.mykitta2.core.error.AppError
import com.simplr.mykitta2.core.result.Outcome
import com.simplr.mykitta2.data.repo.HomeRepository
import com.simplr.mykitta2.domain.Banner
import com.simplr.mykitta2.domain.CategoryRail
import com.simplr.mykitta2.domain.Item
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * MVIKotlin's CoroutineBootstrapper / CoroutineExecutor default to
 * Dispatchers.Main. UnconfinedTestDispatcher + setMain makes them run
 * synchronously so state is observable immediately after `create()` / `accept()`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeStoreTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    // ---- Fixtures ----

    private val banner1 = Banner("B1", "100 PLUS", "http://x/1.jpg", "1", "", "")
    private val banner2 = Banner("B2", "FRUIT TREE", "http://x/2.jpg", "1", "", "")

    private val mostBuyRail = CategoryRail(functionName = "GetMostBuy", title = "Most Buy", displayOrder = 1)
    private val lastBuyRail = CategoryRail(functionName = "GetLastOrder", title = "Last Buy", displayOrder = 2)

    private val itemA = item("PA", "Soap")
    private val itemB = item("PB", "Tea")

    private fun item(id: String, desc: String, invQty: Int = 5) = Item(
        productId = id, productDesc = desc, productLong = "", productUrl = "",
        principalId = "1", totalOrder = 0, basicPrice = "1", unitPrice = "1",
        baseUom = "PCS", salesUom = "PCS", invQty = invQty,
    )

    // ---- Fake repo ----

    /** Per-call results are mutable so tests can reconfigure between Refreshes. */
    private class FakeHomeRepository(
        var bannersResult: Outcome<List<Banner>> = Outcome.Success(emptyList()),
        var configResult: Outcome<List<CategoryRail>> = Outcome.Success(emptyList()),
        var notifResult: Outcome<Int> = Outcome.Success(0),
        var railItemsResults: MutableMap<String, Outcome<List<Item>>> = mutableMapOf(),
    ) : HomeRepository, JvmSerializable {
        var bannerCalls = 0
        var configCalls = 0
        var notifCalls = 0
        val railItemCalls = mutableListOf<String>()

        override suspend fun loadBanners(): Outcome<List<Banner>> {
            bannerCalls++; return bannersResult
        }
        override suspend fun loadConfigRails(): Outcome<List<CategoryRail>> {
            configCalls++; return configResult
        }
        override suspend fun loadRailItems(functionName: String): Outcome<List<Item>> {
            railItemCalls += functionName
            return railItemsResults[functionName] ?: Outcome.Success(emptyList())
        }
        override suspend fun loadNotificationCount(): Outcome<Int> {
            notifCalls++; return notifResult
        }
    }

    private fun storeWith(repo: FakeHomeRepository): HomeStore =
        HomeStoreFactory(storeFactory = DefaultStoreFactory(), homeRepository = repo).create()

    // ---- Bootstrap ----

    @Test fun bootstrap_loadsAllThreeChannels() = runTest(dispatcher) {
        val repo = FakeHomeRepository(
            bannersResult = Outcome.Success(listOf(banner1)),
            configResult = Outcome.Success(emptyList()),
            notifResult = Outcome.Success(3),
        )
        val store = storeWith(repo)

        assertEquals(1, repo.bannerCalls)
        assertEquals(1, repo.configCalls)
        assertEquals(1, repo.notifCalls)
        assertEquals(listOf(banner1), store.state.banners)
        assertEquals(3, store.state.notifCount)
        assertFalse(store.state.bannersLoading)
        assertFalse(store.state.railsLoading)
    }

    @Test fun bootstrap_fansOutOnePerRailItemFetch() = runTest(dispatcher) {
        val repo = FakeHomeRepository(
            configResult = Outcome.Success(listOf(mostBuyRail, lastBuyRail)),
            railItemsResults = mutableMapOf(
                "GetMostBuy" to Outcome.Success(listOf(itemA)),
                "GetLastOrder" to Outcome.Success(listOf(itemB)),
            ),
        )
        val store = storeWith(repo)

        assertEquals(setOf("GetMostBuy", "GetLastOrder"), repo.railItemCalls.toSet())
        val rails = store.state.rails
        assertEquals(2, rails.size)
        assertEquals(listOf(itemA), rails.first { it.functionName == "GetMostBuy" }.items)
        assertEquals(listOf(itemB), rails.first { it.functionName == "GetLastOrder" }.items)
        assertTrue(rails.all { !it.loading }, "all rails done loading: $rails")
    }

    @Test fun bootstrap_perRailFailureLeavesOtherRailsIntact() = runTest(dispatcher) {
        // One rail succeeds, one fails. Failure must not propagate to the
        // other rail nor to a screen-level error.
        val repo = FakeHomeRepository(
            configResult = Outcome.Success(listOf(mostBuyRail, lastBuyRail)),
            railItemsResults = mutableMapOf(
                "GetMostBuy" to Outcome.Success(listOf(itemA)),
                "GetLastOrder" to Outcome.Failure(AppError.Network),
            ),
        )
        val store = storeWith(repo)

        val mostBuy = store.state.rails.first { it.functionName == "GetMostBuy" }
        val lastBuy = store.state.rails.first { it.functionName == "GetLastOrder" }
        assertEquals(listOf(itemA), mostBuy.items)
        assertEquals(emptyList(), lastBuy.items)
        assertFalse(mostBuy.loading)
        assertFalse(lastBuy.loading, "failed rail still flips loading=false")
        // Rail-item failures DON'T set screen-level error (legacy parity).
        assertNull(store.state.error)
    }

    @Test fun bootstrap_bannerFailureSurfacesErrorAndEmptyList() = runTest(dispatcher) {
        val repo = FakeHomeRepository(bannersResult = Outcome.Failure(AppError.Network))
        val store = storeWith(repo)

        assertEquals(emptyList(), store.state.banners)
        assertFalse(store.state.bannersLoading)
        assertNotNull(store.state.error)
    }

    @Test fun bootstrap_configFailureSurfacesErrorAndEmptyRails() = runTest(dispatcher) {
        val repo = FakeHomeRepository(configResult = Outcome.Failure(AppError.Network))
        val store = storeWith(repo)

        assertEquals(emptyList(), store.state.rails)
        assertFalse(store.state.railsLoading)
        assertNotNull(store.state.error)
        // No rail items fetched when config failed.
        assertTrue(repo.railItemCalls.isEmpty())
    }

    @Test fun bootstrap_notifCountFailureDoesNotPoisonScreen() = runTest(dispatcher) {
        // Notif count is fire-and-forget — failure leaves the count at 0 and
        // doesn't surface as a screen-level error.
        val repo = FakeHomeRepository(notifResult = Outcome.Failure(AppError.Network))
        val store = storeWith(repo)
        assertEquals(0, store.state.notifCount)
        assertNull(store.state.error)
    }

    // ---- Click labels ----

    @Test fun itemClicked_emitsSnackbarLabelWithProductDesc() = runTest(dispatcher) {
        val store = storeWith(FakeHomeRepository())
        val captured = mutableListOf<HomeStore.Label>()
        val collector = launch { store.labels.collect { captured += it } }

        store.accept(HomeStore.Intent.ItemClicked(itemA))

        collector.cancel()
        assertEquals(1, captured.size)
        val label = captured.single()
        assertIs<HomeStore.Label.ShowSnackbar>(label)
        assertTrue(label.text.contains("Soap"), "label was: ${label.text}")
    }

    @Test fun bannerClicked_emitsSnackbarLabelWithBannerName() = runTest(dispatcher) {
        val store = storeWith(FakeHomeRepository())
        val captured = mutableListOf<HomeStore.Label>()
        val collector = launch { store.labels.collect { captured += it } }

        store.accept(HomeStore.Intent.BannerClicked(banner1))

        collector.cancel()
        assertEquals("100 PLUS", (captured.single() as HomeStore.Label.ShowSnackbar).text)
    }

    // ---- Refresh ----

    @Test fun refresh_reissuesAllThreeChannels() = runTest(dispatcher) {
        val repo = FakeHomeRepository(
            configResult = Outcome.Success(listOf(mostBuyRail)),
            railItemsResults = mutableMapOf("GetMostBuy" to Outcome.Success(listOf(itemA))),
        )
        val store = storeWith(repo)
        // Bootstrap fired one round.
        assertEquals(1, repo.bannerCalls)
        assertEquals(1, repo.configCalls)
        assertEquals(1, repo.notifCalls)
        assertEquals(1, repo.railItemCalls.size)

        store.accept(HomeStore.Intent.Refresh)

        assertEquals(2, repo.bannerCalls)
        assertEquals(2, repo.configCalls)
        assertEquals(2, repo.notifCalls)
        assertEquals(2, repo.railItemCalls.size)
    }

    @Test fun refresh_setsAndClearsRefreshingFlag() = runTest(dispatcher) {
        // With UnconfinedTestDispatcher the suspend calls resolve synchronously,
        // so by the time accept(Refresh) returns the entire load has completed
        // and refreshing is back to false. The test still pins the FINAL state
        // because a buggy refresh flow that left refreshing=true would fail.
        val store = storeWith(FakeHomeRepository())
        store.accept(HomeStore.Intent.Refresh)
        assertFalse(store.state.refreshing, "refreshing must clear once load settles")
    }

    @Test fun refresh_clearsPriorErrorOnStart() = runTest(dispatcher) {
        // First run fails → error is set. Second run is configured to succeed —
        // the RefreshStarted reducer nulls error before the new load begins,
        // and no new error is produced.
        val repo = FakeHomeRepository(bannersResult = Outcome.Failure(AppError.Network))
        val store = storeWith(repo)
        assertNotNull(store.state.error)

        repo.bannersResult = Outcome.Success(listOf(banner1))
        store.accept(HomeStore.Intent.Refresh)

        assertNull(store.state.error)
        assertEquals(listOf(banner1), store.state.banners)
    }

    // ---- State transitions ----

    @Test fun successfulConfigReplacesPriorRails() = runTest(dispatcher) {
        // Refresh with a smaller rail list — old rails should be REPLACED,
        // not appended. Catches a bug where the reducer concatenated.
        val repo = FakeHomeRepository(
            configResult = Outcome.Success(listOf(mostBuyRail, lastBuyRail)),
            railItemsResults = mutableMapOf(
                "GetMostBuy" to Outcome.Success(listOf(itemA)),
                "GetLastOrder" to Outcome.Success(listOf(itemB)),
            ),
        )
        val store = storeWith(repo)
        assertEquals(2, store.state.rails.size)

        repo.configResult = Outcome.Success(listOf(mostBuyRail))
        store.accept(HomeStore.Intent.Refresh)

        assertEquals(1, store.state.rails.size)
        assertEquals("GetMostBuy", store.state.rails.single().functionName)
    }

    @Test fun emptyConfigStillFlipsRailsLoadingOff() = runTest(dispatcher) {
        val repo = FakeHomeRepository(configResult = Outcome.Success(emptyList()))
        val store = storeWith(repo)
        assertEquals(emptyList(), store.state.rails)
        assertFalse(store.state.railsLoading)
    }
}
