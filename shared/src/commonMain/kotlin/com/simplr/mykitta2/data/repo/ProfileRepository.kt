package com.simplr.mykitta2.data.repo

import com.simplr.mykitta2.core.env.BuildEnv
import com.simplr.mykitta2.core.error.ErrorMapper
import com.simplr.mykitta2.core.result.Outcome
import com.simplr.mykitta2.data.net.api.CatalogApi
import com.simplr.mykitta2.data.net.dto.GetRequest
import com.simplr.mykitta2.data.prefs.CountryStore
import com.simplr.mykitta2.data.prefs.ProfileCacheStore
import com.simplr.mykitta2.data.prefs.SessionStore
import com.simplr.mykitta2.domain.Country
import com.simplr.mykitta2.domain.Profile
import kotlin.time.Duration

/**
 * Profile detail (legacy `GetProfile`) — cached for 24h in
 * [ProfileCacheStore]. The repository is the *only* place that decides when
 * to hit the network; callers see a cache-or-fetch result either way.
 */
interface ProfileRepository {
    /**
     * Returns the user profile. Cache hit (< [ttl]) → returns cached and
     * skips the network entirely. Cache miss or stale → fires `GetProfile`,
     * writes the new value to cache, returns it. Network failure with a
     * non-empty stale cache → returns the stale cache as a Success (we'd
     * rather show old data than nothing); cache-less network failure →
     * returns the Failure.
     */
    suspend fun loadProfile(ttl: Duration = ProfileCacheStore.DEFAULT_TTL): Outcome<Profile>

    /**
     * Always hits the network, regardless of cache age. Used by a future
     * pull-to-refresh on the Profile screen.
     */
    suspend fun refresh(): Outcome<Profile>

    /** Synchronous (suspending) cache peek — `null` if nothing is cached. */
    suspend fun cached(): Profile?
}

class DefaultProfileRepository(
    private val catalogApi: CatalogApi,
    private val cacheStore: ProfileCacheStore,
    private val sessionStore: SessionStore,
    private val countryStore: CountryStore,
) : ProfileRepository {

    override suspend fun loadProfile(ttl: Duration): Outcome<Profile> {
        cacheStore.readIfFresh(ttl)?.let { return Outcome.Success(it) }
        // Stale or missing — fall back to a network fetch, but if that fails
        // and we still have a stale cached copy, prefer the stale data over
        // a blank screen.
        return when (val fresh = refresh()) {
            is Outcome.Success -> fresh
            is Outcome.Failure -> cacheStore.read()?.let { Outcome.Success(it) } ?: fresh
            Outcome.Idle, Outcome.Loading -> fresh
        }
    }

    override suspend fun refresh(): Outcome<Profile> = try {
        val response = catalogApi.getProfile(baseUrl(), profileRequest())
        val profile = response.firstOrNull()?.toDomain() ?: Profile()
        cacheStore.write(profile)
        Outcome.Success(profile)
    } catch (t: Throwable) {
        Outcome.Failure(ErrorMapper.from(t))
    }

    override suspend fun cached(): Profile? = cacheStore.read()

    private suspend fun baseUrl(): String =
        BuildEnv.baseUrlFor(countryStore.read() ?: Country.PH)

    /** Legacy `GetProfile` uses the standard supervisor-style request envelope
     *  identical to the other `User/GetObject` reads — `parameter="all"`. */
    private suspend fun profileRequest() = GetRequest(
        functionName = "GetProfile",
        offset = 0,
        recordsize = sessionStore.pagination(),
        search = "all",
        sort = "0",
        user = sessionStore.read()?.supervisorCode ?: FALLBACK_USER,
    )

    private companion object {
        const val FALLBACK_USER = "M1"
    }
}
