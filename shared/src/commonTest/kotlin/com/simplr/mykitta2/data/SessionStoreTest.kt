package com.simplr.mykitta2.data

import com.russhwolf.settings.MapSettings
import com.simplr.mykitta2.data.prefs.SettingsSessionStore
import com.simplr.mykitta2.domain.Session
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionStoreTest {
    private fun store() = SettingsSessionStore(MapSettings())

    @Test fun readReturnsNullByDefault() = runTest {
        assertNull(store().read())
    }

    @Test fun writeThenReadRoundTrips() = runTest {
        val s = store()
        val session = Session(userName = "9171234567", supervisorCode = "S1", isSupervisor = true)
        s.write(session)
        assertEquals(session, s.read())
    }

    @Test fun overwriteReplacesSession() = runTest {
        val s = store()
        s.write(Session("u1", "S1", isSupervisor = false))
        val later = Session("u2", "S2", isSupervisor = true)
        s.write(later)
        assertEquals(later, s.read())
    }

    @Test fun isSupervisorFalsePreserved() = runTest {
        val s = store()
        s.write(Session("u", "S1", isSupervisor = false))
        assertEquals(false, s.read()?.isSupervisor)
    }

    @Test fun clearRemovesSession() = runTest {
        val s = store()
        s.write(Session("u", "S1", isSupervisor = true))
        s.clear()
        assertNull(s.read())
    }

    @Test fun paginationDefaultsTo15() = runTest {
        // Legacy contract — User/GetObject bodies fall back to 15 if no override.
        assertEquals(15, store().pagination())
    }

    @Test fun clearAlsoResetsPaginationDefault() = runTest {
        val s = store()
        s.write(Session("u", "S1", isSupervisor = true))
        s.clear()
        assertEquals(15, s.pagination())
    }
}
