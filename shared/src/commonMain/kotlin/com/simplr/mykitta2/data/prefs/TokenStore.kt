package com.simplr.mykitta2.data.prefs

import com.russhwolf.settings.Settings
import kotlin.time.Instant

data class TokenPair(val access: String, val refresh: String, val expiresAt: Instant)

interface TokenStore {
    suspend fun read(): TokenPair?
    suspend fun write(pair: TokenPair)
    suspend fun clear()
}

class SettingsTokenStore(private val settings: Settings) : TokenStore {
    override suspend fun read(): TokenPair? {
        val access = settings.getStringOrNull(KEY_ACCESS) ?: return null
        val refresh = settings.getStringOrNull(KEY_REFRESH) ?: return null
        val expiresAtEpochMs = settings.getLongOrNull(KEY_EXPIRES_AT) ?: return null
        return TokenPair(
            access = access,
            refresh = refresh,
            expiresAt = Instant.fromEpochMilliseconds(expiresAtEpochMs),
        )
    }

    override suspend fun write(pair: TokenPair) {
        settings.putString(KEY_ACCESS, pair.access)
        settings.putString(KEY_REFRESH, pair.refresh)
        settings.putLong(KEY_EXPIRES_AT, pair.expiresAt.toEpochMilliseconds())
    }

    override suspend fun clear() {
        settings.remove(KEY_ACCESS)
        settings.remove(KEY_REFRESH)
        settings.remove(KEY_EXPIRES_AT)
    }

    private companion object {
        const val KEY_ACCESS = "auth.access_token"
        const val KEY_REFRESH = "auth.refresh_token"
        const val KEY_EXPIRES_AT = "auth.expires_at_epoch_ms"
    }
}
