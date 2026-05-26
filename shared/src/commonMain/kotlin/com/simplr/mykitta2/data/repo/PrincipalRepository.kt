package com.simplr.mykitta2.data.repo

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.simplr.mykitta2.core.env.BuildEnv
import com.simplr.mykitta2.core.error.ErrorMapper
import com.simplr.mykitta2.core.result.Outcome
import com.simplr.mykitta2.data.net.api.CatalogApi
import com.simplr.mykitta2.data.net.dto.GetRequest
import com.simplr.mykitta2.data.prefs.CountryStore
import com.simplr.mykitta2.data.prefs.SessionStore
import com.simplr.mykitta2.domain.Country
import com.simplr.mykitta2.domain.Principal
import com.simplr.mykitta2.shared.db.MyKittaDatabase
import com.simplr.mykitta2.shared.db.Principal as PrincipalRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface PrincipalRepository {
    /** Continuous stream of principals from the local cache. UI subscribes once
     *  and reacts to upserts after [refresh] writes. */
    fun observeAll(): Flow<List<Principal>>

    /** Pull the latest principal list from the backend and replace the cache.
     *  Errors surface via [Outcome.Failure]; the cache flow keeps its prior
     *  value so the UI shows last-known good data + an error banner. */
    suspend fun refresh(): Outcome<Unit>

    /** Synchronous cache lookup — returns null if the principal isn't cached
     *  yet (cold start before [refresh] runs, or genuinely-unknown id). */
    suspend fun findById(principalId: String): Principal?
}

class DefaultPrincipalRepository(
    private val catalogApi: CatalogApi,
    private val database: MyKittaDatabase,
    private val sessionStore: SessionStore,
    private val countryStore: CountryStore,
) : PrincipalRepository {

    override fun observeAll(): Flow<List<Principal>> =
        database.principalQueries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun refresh(): Outcome<Unit> = try {
        val baseUrl = BuildEnv.baseUrlFor(countryStore.read() ?: Country.PH)
        val request = GetRequest(
            functionName = "GetPrincipal",
            offset = 0,
            recordsize = sessionStore.pagination(),
            search = "all",
            sort = "0",
            user = sessionStore.read()?.supervisorCode ?: FALLBACK_USER,
        )
        val response = catalogApi.getPrincipals(baseUrl, request)
        val principals = response.getObjectResult.objectData.firstOrNull().orEmpty()

        database.principalQueries.transaction {
            database.principalQueries.deleteAll()
            principals.forEachIndexed { index, dto ->
                database.principalQueries.upsert(
                    principalId = dto.principalId,
                    principalName = dto.principalName,
                    principalImg = dto.principalImg,
                    isActive = if (dto.isActive == true) 1L else 0L,
                    sortOrder = index.toLong(),
                )
            }
        }
        Outcome.Success(Unit)
    } catch (t: Throwable) {
        Outcome.Failure(ErrorMapper.from(t))
    }

    override suspend fun findById(principalId: String): Principal? =
        database.principalQueries.selectById(principalId)
            .executeAsOneOrNull()
            ?.toDomain()

    private fun PrincipalRow.toDomain() = Principal(
        principalId = principalId,
        principalName = principalName,
        principalImg = principalImg,
        isActive = isActive == 1L,
    )

    private companion object {
        const val FALLBACK_USER = "M1"
    }
}
