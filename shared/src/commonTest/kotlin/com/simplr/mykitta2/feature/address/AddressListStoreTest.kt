package com.simplr.mykitta2.feature.address

import com.arkivanov.mvikotlin.extensions.coroutines.labels
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import com.simplr.mykitta2.core.error.AppError
import com.simplr.mykitta2.core.result.Outcome
import com.simplr.mykitta2.data.net.dto.AddressRequest
import com.simplr.mykitta2.data.repo.AddressRepository
import com.simplr.mykitta2.domain.Address
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlin.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
class AddressListStoreTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    // ---- Bootstrap ----

    @Test fun bootstrap_subscribesObserve_andCallsRefreshNonForce() = runTest(dispatcher) {
        val repo = FakeAddressRepository()
        repo.emit(listOf(address("A-1")))
        val store = makeStore(repo)
        assertEquals(1, store.state.addresses.size)
        assertEquals(listOf(false), repo.refreshCalls.map { it.force })
        assertFalse(store.state.initialLoading, "InitialLoadFinished must clear initialLoading")
        assertNull(store.state.error)
    }

    @Test fun bootstrap_failure_setsError_clearsLoading() = runTest(dispatcher) {
        val repo = FakeAddressRepository(refreshOutcome = Outcome.Failure(AppError.Network))
        val store = makeStore(repo)
        assertFalse(store.state.initialLoading)
        assertNotNull(store.state.error)
    }

    // ---- Observe stream ----

    @Test fun observeEmissions_updateState() = runTest(dispatcher) {
        val repo = FakeAddressRepository()
        val store = makeStore(repo)
        assertEquals(0, store.state.addresses.size)
        repo.emit(listOf(address("A-1"), address("A-2", isSelected = true)))
        assertEquals(2, store.state.addresses.size)
        assertEquals("A-2", store.state.addresses[1].customerAddressId)
    }

    // ---- Refresh ----

    @Test fun refreshIntent_setsRefreshing_andCallsRepoForce() = runTest(dispatcher) {
        val repo = FakeAddressRepository()
        val store = makeStore(repo)
        repo.refreshCalls.clear()

        store.accept(AddressListStore.Intent.Refresh)

        assertEquals(listOf(true), repo.refreshCalls.map { it.force })
        // Unconfined dispatcher → the launch{} resumed synchronously and we
        // already saw RefreshFinished.
        assertFalse(store.state.refreshing)
    }

    @Test fun refreshIntent_failure_setsError() = runTest(dispatcher) {
        val repo = FakeAddressRepository()
        val store = makeStore(repo)
        repo.refreshOutcome = Outcome.Failure(AppError.Network)

        store.accept(AddressListStore.Intent.Refresh)

        assertNotNull(store.state.error)
        assertFalse(store.state.refreshing)
    }

    // ---- Labels ----

    @Test fun addTapped_publishesOpenFormWithNullId() = runTest(dispatcher) {
        val store = makeStore(FakeAddressRepository())
        val labels = mutableListOf<AddressListStore.Label>()
        val collector = launch { store.labels.collect { labels += it } }

        store.accept(AddressListStore.Intent.AddTapped)

        collector.cancel()
        assertEquals(AddressListStore.Label.OpenForm(customerAddressId = null), labels.single())
    }

    @Test fun rowTapped_publishesOpenFormWithId() = runTest(dispatcher) {
        val store = makeStore(FakeAddressRepository())
        val labels = mutableListOf<AddressListStore.Label>()
        val collector = launch { store.labels.collect { labels += it } }

        store.accept(AddressListStore.Intent.RowTapped("A-9"))

        collector.cancel()
        assertEquals(AddressListStore.Label.OpenForm(customerAddressId = "A-9"), labels.single())
    }

    @Test fun setDefault_callsRepository() = runTest(dispatcher) {
        val repo = FakeAddressRepository()
        repo.emit(listOf(address("A-1"), address("A-2")))
        val store = makeStore(repo)

        store.accept(AddressListStore.Intent.SetDefault("A-2"))

        assertEquals(listOf("A-2"), repo.setDefaultCalls)
        // The fake mirrors the real repo's flag flip, and the store's
        // observe() subscription should have surfaced the new flag.
        val selected = store.state.addresses.firstOrNull { it.isSelected }
        assertNotNull(selected)
        assertEquals("A-2", selected.customerAddressId)
    }

    @Test fun setDefault_failure_surfacesAsError() = runTest(dispatcher) {
        val repo = FakeAddressRepository(
            setDefaultOutcome = Outcome.Failure(AppError.Network),
        )
        repo.emit(listOf(address("A-1"), address("A-2")))
        val store = makeStore(repo)

        store.accept(AddressListStore.Intent.SetDefault("A-2"))

        assertNotNull(store.state.error)
        assertEquals(false, store.state.addresses.any { it.isSelected },
            "no flag flip when the setter fails")
    }

    @Test fun dismissError_clearsErrorState() = runTest(dispatcher) {
        val repo = FakeAddressRepository(refreshOutcome = Outcome.Failure(AppError.Network))
        val store = makeStore(repo)
        assertNotNull(store.state.error)
        store.accept(AddressListStore.Intent.DismissError)
        assertNull(store.state.error)
    }

    // ---- Helpers ----

    private fun makeStore(repo: AddressRepository): AddressListStore =
        AddressListStoreFactory(storeFactory = DefaultStoreFactory(), repository = repo).create()

    private fun address(
        id: String,
        isSelected: Boolean = false,
    ) = Address(
        customerAddressId = id, name = "Home-$id", address1 = "1 St",
        address2 = "", zipcode = "1000", city = "Manila", phone = "0900",
        contact = "Juan", barangay = "", province = "", subdivision = "",
        isSelected = isSelected,
    )

    private data class RefreshCall(val force: Boolean)

    private class FakeAddressRepository(
        var refreshOutcome: Outcome<Unit> = Outcome.Success(Unit),
        var saveOutcome: Outcome<Unit> = Outcome.Success(Unit),
        var setDefaultOutcome: Outcome<Unit> = Outcome.Success(Unit),
    ) : AddressRepository {
        private val stream = MutableStateFlow<List<Address>>(emptyList())
        val refreshCalls = mutableListOf<RefreshCall>()
        val setDefaultCalls = mutableListOf<String>()

        fun emit(addresses: List<Address>) {
            stream.value = addresses
        }

        override fun observe(): Flow<List<Address>> = stream
        override suspend fun refresh(force: Boolean, ttl: Duration): Outcome<Unit> {
            refreshCalls += RefreshCall(force)
            return refreshOutcome
        }
        override suspend fun save(request: AddressRequest): Outcome<Unit> = saveOutcome
        override suspend fun setAsDefault(customerAddressId: String): Outcome<Unit> {
            setDefaultCalls += customerAddressId
            if (setDefaultOutcome is Outcome.Success) {
                // Mirror the real repo's local flag flip so observers see it.
                stream.value = stream.value.map {
                    it.copy(isSelected = it.customerAddressId == customerAddressId)
                }
            }
            return setDefaultOutcome
        }
        override suspend fun findById(customerAddressId: String): Address? =
            stream.value.firstOrNull { it.customerAddressId == customerAddressId }
    }
}
