package com.simplr.mykitta2.data.repo

import com.simplr.mykitta2.core.env.BuildEnv
import com.simplr.mykitta2.core.error.ErrorMapper
import com.simplr.mykitta2.core.result.Outcome
import com.simplr.mykitta2.data.net.api.CatalogApi
import com.simplr.mykitta2.data.net.dto.GetRequest
import com.simplr.mykitta2.data.prefs.CountryStore
import com.simplr.mykitta2.data.prefs.SessionStore
import com.simplr.mykitta2.domain.Banner
import com.simplr.mykitta2.domain.CategoryRail
import com.simplr.mykitta2.domain.Country
import com.simplr.mykitta2.domain.Item

interface HomeRepository {
    suspend fun loadBanners(): Outcome<List<Banner>>

    /**
     * Returns the rail headers (function name + title + display order) with empty
     * `items` and `loading = true`. Caller fans out one [loadRailItems] call per
     * entry, then updates the corresponding rail.
     */
    suspend fun loadConfigRails(): Outcome<List<CategoryRail>>

    /**
     * Fetches the items for a single rail. `functionName` is the rail's
     * `ConfigDto.systemValue` (e.g. `GetMostBuy`, `GetLastOrder`).
     */
    suspend fun loadRailItems(functionName: String): Outcome<List<Item>>

    /**
     * Loyalty-points balance for the current supervisor. Legacy contract
     * returns the count under `objectData[0][0].points`; a missing record
     * collapses to 0 (i.e. the user has no loyalty balance yet).
     */
    suspend fun loadLoyaltyPoints(): Outcome<Int>
}

class DefaultHomeRepository(
    private val catalogApi: CatalogApi,
    private val sessionStore: SessionStore,
    private val countryStore: CountryStore,
) : HomeRepository {

    override suspend fun loadBanners(): Outcome<List<Banner>> = runCall {
        val response = catalogApi.getBanners(baseUrl(), supervisorRequest("GetBanner"))
        response.getObjectResult.objectData.firstOrNull().orEmpty().map { it.toDomain() }
    }

    override suspend fun loadConfigRails(): Outcome<List<CategoryRail>> = runCall {
        val response = catalogApi.getConfigList(baseUrl(), supervisorRequest("GetMultiListConfig"))
        response.getObjectResult.objectData.firstOrNull().orEmpty()
            .sortedBy { it.displayNo }
            .map {
                CategoryRail(
                    functionName = it.systemValue,
                    title = it.description,
                    displayOrder = it.displayNo,
                    items = emptyList(),
                    loading = true,
                )
            }
    }

    override suspend fun loadRailItems(functionName: String): Outcome<List<Item>> = runCall {
        val response = catalogApi.getItems(baseUrl(), supervisorRequest(functionName))
        response.getObjectResult.objectData.firstOrNull().orEmpty().map { it.toDomain() }
    }

    override suspend fun loadLoyaltyPoints(): Outcome<Int> = runCall {
        catalogApi.getLoyaltyPoints(baseUrl(), supervisorRequest("GetLoyaltyPoints"))
            .points()
    }

    /** Active country dictates which backend; defaults to PH if none has been
     * stored (post-OTP this should never be null, but the bootstrap path can
     * race the country picker on first launch). */
    private suspend fun baseUrl(): String =
        BuildEnv.baseUrlFor(countryStore.read() ?: Country.PH)

    /** Build the legacy supervisor-style request used by every "list" call.
     * The Postman / legacy app default for unset users is `"M1"`. */
    private suspend fun supervisorRequest(
        functionName: String,
        parameter: String = "all",
        sort: String = "0",
        offset: Int = 0,
    ) = GetRequest(
        functionName = functionName,
        offset = offset,
        recordsize = sessionStore.pagination(),
        search = parameter,
        sort = sort,
        user = sessionStore.read()?.supervisorCode ?: FALLBACK_USER,
    )

    private inline fun <T> runCall(block: () -> T): Outcome<T> = try {
        Outcome.Success(block())
    } catch (t: Throwable) {
        Outcome.Failure(ErrorMapper.from(t))
    }

    private companion object {
        const val FALLBACK_USER = "M1"
    }
}
