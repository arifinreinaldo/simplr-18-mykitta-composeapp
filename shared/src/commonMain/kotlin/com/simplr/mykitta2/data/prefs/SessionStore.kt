package com.simplr.mykitta2.data.prefs

import com.russhwolf.settings.Settings
import com.simplr.mykitta2.domain.Session

/**
 * Plain (non-secure) prefs for the authenticated user profile derived from
 * VerifyLoginOTP. The token itself lives in [TokenStore] (secure); these
 * fields are used to populate `User/GetObject` request bodies.
 *
 * Pagination is read separately because the backend tolerates the default and
 * the legacy app exposed it as a runtime knob via `C.PAGINATION` (default 15).
 */
interface SessionStore {
    suspend fun read(): Session?
    suspend fun write(session: Session)
    suspend fun clear()
    suspend fun pagination(): Int
}

class SettingsSessionStore(private val settings: Settings) : SessionStore {
    override suspend fun read(): Session? {
        val user = settings.getStringOrNull(KEY_USER) ?: return null
        val supervisor = settings.getStringOrNull(KEY_SUPERVISOR) ?: return null
        val isSupervisor = settings.getBoolean(KEY_IS_SUPERVISOR, false)
        return Session(userName = user, supervisorCode = supervisor, isSupervisor = isSupervisor)
    }

    override suspend fun write(session: Session) {
        settings.putString(KEY_USER, session.userName)
        settings.putString(KEY_SUPERVISOR, session.supervisorCode)
        settings.putBoolean(KEY_IS_SUPERVISOR, session.isSupervisor)
    }

    override suspend fun clear() {
        settings.remove(KEY_USER)
        settings.remove(KEY_SUPERVISOR)
        settings.remove(KEY_IS_SUPERVISOR)
        settings.remove(KEY_PAGINATION)
    }

    override suspend fun pagination(): Int = settings.getInt(KEY_PAGINATION, DEFAULT_PAGINATION)

    private companion object {
        const val KEY_USER = "session.user"
        const val KEY_SUPERVISOR = "session.supervisor"
        const val KEY_IS_SUPERVISOR = "session.is_supervisor"
        const val KEY_PAGINATION = "session.pagination"
        const val DEFAULT_PAGINATION = 15
    }
}
