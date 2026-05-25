package com.simplr.mykitta2.feature.splash

import com.arkivanov.mvikotlin.extensions.coroutines.labels
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import com.simplr.mykitta2.core.logging.AppLogger
import com.simplr.mykitta2.data.prefs.SessionStore
import com.simplr.mykitta2.data.prefs.TokenStore
import com.simplr.mykitta2.data.prefs.TokenPair
import com.simplr.mykitta2.domain.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * Tests against SplashStore use Duration.ZERO for min-splash so settling
 * happens on the same virtual tick as the underlying reads. The one timing
 * test explicitly passes a non-zero duration and steps virtual time.
 *
 * UnconfinedTestDispatcher + setMain makes the bootstrapper run synchronously
 * up to its first suspension point — observable state lands immediately.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SplashStoreTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    // ---- Fixtures ----

    private val tokenPair = TokenPair(
        access = "access",
        refresh = "refresh",
        expiresAt = Instant.fromEpochMilliseconds(0),
    )
    private val session = Session(
        userName = "USR-1",
        supervisorCode = "SUP-1",
        isSupervisor = false,
    )

    // ---- Fakes ----

    private class FakeTokenStore(
        var stored: TokenPair? = null,
        var throwOnRead: Throwable? = null,
    ) : TokenStore {
        var reads = 0
        override suspend fun read(): TokenPair? {
            reads++
            throwOnRead?.let { throw it }
            return stored
        }
        override suspend fun write(pair: TokenPair) { stored = pair }
        override suspend fun clear() { stored = null }
    }

    private class FakeSessionStore(
        var stored: Session? = null,
        var throwOnRead: Throwable? = null,
    ) : SessionStore {
        var reads = 0
        override suspend fun read(): Session? {
            reads++
            throwOnRead?.let { throw it }
            return stored
        }
        override suspend fun write(session: Session) { stored = session }
        override suspend fun clear() { stored = null }
        override suspend fun pagination(): Int = 15
    }

    private fun storeWith(
        token: TokenPair? = null,
        session: Session? = null,
        tokenThrow: Throwable? = null,
        sessionThrow: Throwable? = null,
        warmup: suspend () -> Unit = {},
        minSplash: kotlin.time.Duration = kotlin.time.Duration.ZERO,
    ): SplashStore = SplashStoreFactory(
        storeFactory = DefaultStoreFactory(),
        tokenStore = FakeTokenStore(stored = token, throwOnRead = tokenThrow),
        sessionStore = FakeSessionStore(stored = session, throwOnRead = sessionThrow),
        warmup = warmup,
        appLogger = AppLogger(),
        minSplashDuration = minSplash,
    ).create()

    // ---- Destination resolution ----

    @Test fun bothPresent_landsOnHome() = runTest(dispatcher) {
        val store = storeWith(token = tokenPair, session = session)
        val ready = assertIs<SplashStore.State.Ready>(store.state)
        assertEquals(SplashStore.Destination.Home, ready.destination)
    }

    @Test fun tokenMissing_landsOnLogin() = runTest(dispatcher) {
        val store = storeWith(token = null, session = session)
        val ready = assertIs<SplashStore.State.Ready>(store.state)
        assertEquals(SplashStore.Destination.Login, ready.destination)
    }

    @Test fun sessionMissing_landsOnLogin() = runTest(dispatcher) {
        val store = storeWith(token = tokenPair, session = null)
        val ready = assertIs<SplashStore.State.Ready>(store.state)
        assertEquals(SplashStore.Destination.Login, ready.destination)
    }

    @Test fun bothMissing_landsOnLogin() = runTest(dispatcher) {
        val store = storeWith(token = null, session = null)
        val ready = assertIs<SplashStore.State.Ready>(store.state)
        assertEquals(SplashStore.Destination.Login, ready.destination)
    }

    // ---- Silent error fallbacks ----

    @Test fun tokenStoreThrows_treatedAsMissing() = runTest(dispatcher) {
        val store = storeWith(
            tokenThrow = RuntimeException("corrupt prefs"),
            session = session,
        )
        val ready = assertIs<SplashStore.State.Ready>(store.state)
        assertEquals(SplashStore.Destination.Login, ready.destination)
    }

    @Test fun sessionStoreThrows_treatedAsMissing() = runTest(dispatcher) {
        val store = storeWith(
            token = tokenPair,
            sessionThrow = RuntimeException("corrupt prefs"),
        )
        val ready = assertIs<SplashStore.State.Ready>(store.state)
        assertEquals(SplashStore.Destination.Login, ready.destination)
    }

    @Test fun warmupThrows_stillSettlesWithCorrectDestination() = runTest(dispatcher) {
        val store = storeWith(
            token = tokenPair,
            session = session,
            warmup = { throw RuntimeException("DB closed") },
        )
        // Warm-up failure must not gate the auth decision — token+session still
        // present means Home.
        val ready = assertIs<SplashStore.State.Ready>(store.state)
        assertEquals(SplashStore.Destination.Home, ready.destination)
    }

    // ---- Warm-up invocation ----

    @Test fun warmupIsInvokedOncePerStoreCreation() = runTest(dispatcher) {
        var calls = 0
        storeWith(warmup = { calls++ })
        assertEquals(1, calls)
    }

    @Test fun warmupRunsEvenWhenTokenMissing() = runTest(dispatcher) {
        var called = false
        storeWith(token = null, session = null, warmup = { called = true })
        assertTrue(called, "warmup must run regardless of auth state — it's about driver warm, not auth")
    }

    // ---- Timing ----

    @Test fun respectsMinSplashDuration_holdsWorkingUntilElapsed() = runTest(dispatcher) {
        val store = storeWith(
            token = tokenPair,
            session = session,
            minSplash = 200.milliseconds,
        )
        // Reads + warmup are instant under the fakes; the only suspension left
        // is the min-splash delay. State should still report Working.
        assertEquals(SplashStore.State.Working, store.state)

        advanceTimeBy(100L)
        assertEquals(SplashStore.State.Working, store.state, "still gated at t=100ms")

        // advanceTimeBy is exclusive on the upper bound — bump past 200 so the
        // delay scheduled AT t=200 also fires.
        advanceTimeBy(150L)
        val ready = assertIs<SplashStore.State.Ready>(store.state)
        assertEquals(SplashStore.Destination.Home, ready.destination)
    }

    // ---- Label publishing ----

    @Test fun publishesNavigateToLabel_matchingFinalDestination() = runTest(dispatcher) {
        // Collector must be attached before the bootstrapper publishes —
        // MutableSharedFlow.replay=0 means missed labels are dropped. With a
        // non-zero min-splash the bootstrapper is parked in delay() when the
        // store ctor returns, giving us time to attach.
        val store = storeWith(
            token = tokenPair,
            session = session,
            minSplash = 200.milliseconds,
        )
        val captured = mutableListOf<SplashStore.Label>()
        val collector = launch { store.labels.collect { captured += it } }

        advanceTimeBy(300L)
        collector.cancel()

        assertEquals(1, captured.size)
        val label = assertIs<SplashStore.Label.NavigateTo>(captured.single())
        assertEquals(SplashStore.Destination.Home, label.destination)
    }
}
