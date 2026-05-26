package com.simplr.mykitta2.data.prefs

import com.russhwolf.settings.Settings
import com.simplr.mykitta2.domain.Profile
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.serialization.json.Json

/**
 * Plain (non-secure) cache for the `GetProfile` payload. The on-disk shape is
 * `(json-encoded Profile, fetchedAtEpochMs)`; readers can ask for the cached
 * value unconditionally ([read]) or gated by freshness ([readIfFresh]).
 *
 * TTL is intentionally exposed as a parameter on the freshness gate rather
 * than baked into the store so tests can drive expiry without manipulating
 * the clock.
 */
interface ProfileCacheStore {
    suspend fun read(): Profile?

    /** Last successful fetch instant, or `null` if no cache exists. */
    suspend fun fetchedAt(): Instant?

    /** [read] but only when the cache is no older than [ttl]; else `null`. */
    suspend fun readIfFresh(ttl: Duration = DEFAULT_TTL): Profile?

    suspend fun write(profile: Profile)
    suspend fun clear()

    companion object {
        /** "At least every 1 day" — refresh on the first open after 24h. */
        val DEFAULT_TTL: Duration = 24.hours
    }
}

class SettingsProfileCacheStore(
    private val settings: Settings,
    private val json: Json = DefaultJson,
    private val clock: Clock = Clock.System,
) : ProfileCacheStore {

    override suspend fun read(): Profile? {
        val raw = settings.getStringOrNull(KEY_PAYLOAD) ?: return null
        return runCatching { json.decodeFromString(Profile.serializer(), raw) }.getOrNull()
    }

    override suspend fun fetchedAt(): Instant? {
        if (!settings.hasKey(KEY_FETCHED_AT)) return null
        return Instant.fromEpochMilliseconds(settings.getLong(KEY_FETCHED_AT, 0L))
    }

    override suspend fun readIfFresh(ttl: Duration): Profile? {
        val at = fetchedAt() ?: return null
        // `now - at` is negative if the clock jumped backwards; treat that as
        // stale rather than indefinitely fresh, otherwise a one-time clock
        // skew could pin a bad cache forever.
        val age = clock.now() - at
        if (age < Duration.ZERO || age >= ttl) return null
        return read()
    }

    override suspend fun write(profile: Profile) {
        settings.putString(KEY_PAYLOAD, json.encodeToString(Profile.serializer(), profile))
        settings.putLong(KEY_FETCHED_AT, clock.now().toEpochMilliseconds())
    }

    override suspend fun clear() {
        settings.remove(KEY_PAYLOAD)
        settings.remove(KEY_FETCHED_AT)
    }

    private companion object {
        const val KEY_PAYLOAD = "profile.payload"
        const val KEY_FETCHED_AT = "profile.fetched_at"
        val DefaultJson: Json = Json { ignoreUnknownKeys = true }
    }
}
