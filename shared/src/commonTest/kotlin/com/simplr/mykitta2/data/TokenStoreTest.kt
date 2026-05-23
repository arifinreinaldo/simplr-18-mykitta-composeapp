package com.simplr.mykitta2.data

import com.russhwolf.settings.MapSettings
import com.simplr.mykitta2.data.prefs.SettingsTokenStore
import com.simplr.mykitta2.data.prefs.TokenPair
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TokenStoreTest {
    private fun store() = SettingsTokenStore(MapSettings())

    @Test
    fun readReturnsNullByDefault() = runTest {
        assertNull(store().read())
    }

    @Test
    fun writeThenReadRoundTrips() = runTest {
        val s = store()
        val pair = TokenPair("a", "r", Instant.fromEpochMilliseconds(1_700_000_000_000))
        s.write(pair)
        assertEquals(pair, s.read())
    }

    @Test
    fun overwriteReplacesPair() = runTest {
        val s = store()
        s.write(TokenPair("a", "r", Instant.fromEpochMilliseconds(1)))
        val later = TokenPair("a2", "r2", Instant.fromEpochMilliseconds(2))
        s.write(later)
        assertEquals(later, s.read())
    }

    @Test
    fun clearRemovesPair() = runTest {
        val s = store()
        s.write(TokenPair("a", "r", Instant.fromEpochMilliseconds(1)))
        s.clear()
        assertNull(s.read())
    }
}
