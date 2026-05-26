package com.simplr.mykitta2.data

import com.simplr.mykitta2.data.net.api.KtorCatalogApi
import com.simplr.mykitta2.data.repo.DefaultPrincipalRepository
import com.simplr.mykitta2.data.prefs.SettingsCountryStore
import com.simplr.mykitta2.data.prefs.SettingsSessionStore
import com.russhwolf.settings.MapSettings
import com.simplr.mykitta2.shared.db.MyKittaDatabase
import com.simplr.mykitta2.test.makeInMemoryDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PrincipalRepositoryFindByIdTest {

    @Test
    fun findById_returnsPrincipal_whenCached() = runTest {
        val db = makeInMemoryDatabase()
        db.principalQueries.upsert(
            principalId = "P-1", principalName = "Acme Co",
            principalImg = "", isActive = 1L, sortOrder = 0L,
        )
        val repo = repo(db)
        val result = repo.findById("P-1")
        assertEquals("Acme Co", result?.principalName)
        assertEquals(true, result?.isActive)
    }

    @Test
    fun findById_returnsNull_whenMissing() = runTest {
        val db = makeInMemoryDatabase()
        val repo = repo(db)
        assertNull(repo.findById("P-MISSING"))
    }

    private fun repo(db: MyKittaDatabase): DefaultPrincipalRepository {
        val settings = MapSettings()
        val client = HttpClient(MockEngine { _ ->
            respond("", HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }) { install(ContentNegotiation) { json() } }
        return DefaultPrincipalRepository(
            catalogApi = KtorCatalogApi(client),
            database = db,
            sessionStore = SettingsSessionStore(settings),
            countryStore = SettingsCountryStore(settings),
        )
    }
}
