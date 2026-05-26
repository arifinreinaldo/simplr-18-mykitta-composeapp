package com.simplr.mykitta2.feature.profile

import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import com.simplr.mykitta2.core.error.AppError
import com.simplr.mykitta2.core.result.Outcome
import com.simplr.mykitta2.data.repo.ProfileRepository
import com.simplr.mykitta2.domain.Profile
import kotlin.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

/**
 * Uses `UnconfinedTestDispatcher` (same pattern as [LoginOtpStoreTest]) so
 * MVIKotlin's bootstrapper / executor run synchronously and state is
 * observable immediately after `store.create()`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileStoreTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    // ---- Fakes ----

    private class FakeProfileRepository(
        var loadResult: Outcome<Profile> = Outcome.Success(Profile()),
        var refreshResult: Outcome<Profile> = Outcome.Success(Profile()),
    ) : ProfileRepository {
        var loadCalls = 0
        var refreshCalls = 0
        override suspend fun loadProfile(ttl: Duration): Outcome<Profile> {
            loadCalls += 1
            return loadResult
        }
        override suspend fun refresh(): Outcome<Profile> {
            refreshCalls += 1
            return refreshResult
        }
        override suspend fun cached(): Profile? = null
    }

    private fun create(
        loadResult: Outcome<Profile> = Outcome.Success(Profile()),
    ): Pair<ProfileStore, FakeProfileRepository> {
        val repo = FakeProfileRepository(loadResult = loadResult)
        val store = ProfileStoreFactory(
            storeFactory = DefaultStoreFactory(),
            profileRepository = repo,
        ).create()
        return store to repo
    }

    // ---- Bootstrap header ----

    @Test fun bootstrap_emptyResponse_leavesHeaderBlank() = runTest {
        val (store, _) = create(loadResult = Outcome.Success(Profile()))
        assertEquals("", store.state.headerName)
    }

    @Test fun bootstrap_profileLoaded_setsHeaderFromCustName() = runTest {
        val (store, _) = create(
            loadResult = Outcome.Success(Profile(custName = "Demo outlet", phone = "1111111111")),
        )
        assertEquals("Demo outlet", store.state.headerName)
        assertNotNull(store.state.profile)
        assertEquals("Demo outlet", store.state.profile?.custName)
        assertEquals("1111111111", store.state.profile?.phone)
    }

    @Test fun bootstrap_blankCustName_leavesHeaderBlank() = runTest {
        // Backend occasionally returns an empty CustName — header should
        // stay blank rather than ending up as "Welcome back, " with no name.
        val (store, _) = create(
            loadResult = Outcome.Success(Profile(custName = "", phone = "555")),
        )
        assertEquals("", store.state.headerName)
    }

    @Test fun bootstrap_callsLoadProfileExactlyOnce() = runTest {
        val (_, repo) = create()
        assertEquals(1, repo.loadCalls, "bootstrap should kick off exactly one load")
        assertEquals(0, repo.refreshCalls, "bootstrap is cache-aware; should not force-refresh")
    }

    @Test fun bootstrap_loadFailure_publishesErrorAndStopsLoading() = runTest {
        val (store, _) = create(loadResult = Outcome.Failure(AppError.Network))
        assertFalse(store.state.loading, "loading flips off even when load fails")
        assertNotNull(store.state.error, "error message must be surfaced")
    }

    // ---- Refresh intent ----

    @Test fun refreshIntent_callsRepositoryRefresh() = runTest {
        val (store, repo) = create()
        repo.refreshResult = Outcome.Success(Profile(custName = "Refreshed outlet"))

        store.accept(ProfileStore.Intent.Refresh)

        assertEquals(1, repo.refreshCalls)
        assertEquals("Refreshed outlet", store.state.profile?.custName)
        assertEquals("Refreshed outlet", store.state.headerName)
    }

    @Test fun refreshIntent_failurePreservesPriorProfile() = runTest {
        val seed = Profile(custName = "From cache")
        val (store, repo) = create(loadResult = Outcome.Success(seed))
        assertEquals(seed, store.state.profile)

        repo.refreshResult = Outcome.Failure(AppError.Network)
        store.accept(ProfileStore.Intent.Refresh)

        // The error is surfaced but the previously loaded profile remains visible.
        assertNotNull(store.state.error)
        assertEquals(seed, store.state.profile)
    }
}
