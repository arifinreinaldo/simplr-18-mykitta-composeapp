package com.simplr.mykitta2.data

import com.russhwolf.settings.MapSettings
import com.simplr.mykitta2.data.prefs.SettingsCountryStore
import com.simplr.mykitta2.domain.Country
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CountryStoreTest {
    private fun store() = SettingsCountryStore(MapSettings())

    @Test
    fun defaultIsNull() = runTest {
        assertNull(store().read())
    }

    @Test
    fun writeAndReadRoundTrips() = runTest {
        val s = store()
        s.write(Country.SG)
        assertEquals(Country.SG, s.read())
    }

    @Test
    fun overwriteReplaces() = runTest {
        val s = store()
        s.write(Country.PH)
        s.write(Country.SG)
        assertEquals(Country.SG, s.read())
    }

    @Test
    fun clearRemoves() = runTest {
        val s = store()
        s.write(Country.PH)
        s.clear()
        assertNull(s.read())
    }
}
