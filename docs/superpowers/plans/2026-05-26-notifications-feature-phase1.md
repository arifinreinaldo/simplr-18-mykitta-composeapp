# Notifications Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the in-app notification list page + reactive unread-count badge on the Home top-bar, with type-routed taps and an offline-mirror cache. No push (Phases 2 & 3 are separate specs).

**Architecture:** New `NotificationRepository` (Koin `single`) is the single source of truth for the unread count via a `StateFlow<Int>`. `HomeStore` subscribes to that flow for the badge; the new `NotificationScreen` owns its own `NotificationStore` for the paginated list and calls `repository.markAsRead(...)`, which decrements the flow — `HomeStore` updates automatically with no cross-feature coupling. Pagination state lives in the Store; the repository's `loadPage(offset)` is stateless. Cross-NavController deep-link (Notifications → Principal catalog) goes through a small `PendingNavStore` singleton consumed by `MainShell`.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform 1.11, MVIKotlin 4.3, SQLDelight 2.1, Ktor 3.x, Koin, kotlinx-coroutines, kotlinx-serialization, kotlin.test + ktor-client-mock for tests.

**Reference spec:** `docs/superpowers/specs/2026-05-26-notifications-feature-phase1-design.md`

---

## File map

```
NEW
  shared/src/commonMain/kotlin/com/simplr/mykitta2/domain/Notification.kt
  shared/src/commonMain/kotlin/com/simplr/mykitta2/data/net/dto/NotificationDtos.kt
  shared/src/commonMain/kotlin/com/simplr/mykitta2/data/repo/NotificationRepository.kt
  shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/notification/NotificationStore.kt
  shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/notification/NotificationViewModel.kt
  shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/notification/NotificationScreen.kt
  shared/src/commonMain/kotlin/com/simplr/mykitta2/ui/nav/PendingNavStore.kt
  shared/src/commonMain/sqldelight/com/simplr/mykitta2/shared/db/Notification.sq
  shared/src/commonTest/kotlin/com/simplr/mykitta2/data/NotificationRepositoryTest.kt
  shared/src/commonTest/kotlin/com/simplr/mykitta2/feature/notification/NotificationStoreTest.kt
  shared/src/commonTest/kotlin/com/simplr/mykitta2/ui/nav/PendingNavStoreTest.kt

EDIT
  shared/src/commonMain/kotlin/com/simplr/mykitta2/data/repo/PrincipalRepository.kt   (add findById)
  shared/src/commonMain/kotlin/com/simplr/mykitta2/data/repo/HomeRepository.kt        (drop loadNotificationCount)
  shared/src/commonMain/kotlin/com/simplr/mykitta2/data/repo/LocalDataWiper.kt        (wipe Notification + transaction)
  shared/src/commonMain/kotlin/com/simplr/mykitta2/data/net/api/CatalogApi.kt         (callPath, list, markRead)
  shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/home/HomeStore.kt          (swap count source + RefreshNotifications)
  shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/main/MainShell.kt          (hoist HomeViewModel, wire callbacks, PendingNavStore consumer)
  shared/src/commonMain/kotlin/com/simplr/mykitta2/ui/nav/Destination.kt              (add Notifications)
  shared/src/commonMain/kotlin/com/simplr/mykitta2/ui/nav/AppNavHost.kt               (register composable + pass callback)
  shared/src/commonMain/kotlin/com/simplr/mykitta2/ui/common/MyKittaScaffold.kt       (add snackbarHost slot)
  shared/src/commonMain/kotlin/com/simplr/mykitta2/di/AppModule.kt                    (NotificationRepository + featureNotificationModule)
  shared/src/commonTest/kotlin/com/simplr/mykitta2/feature/home/HomeStoreTest.kt      (unread-count tests)
  shared/src/androidHostTest/kotlin/com/simplr/mykitta2/data/MyKittaDatabaseWiperTest.kt  (assert Notification wiped)
```

12 new files + 12 edits = 24 files.

---

## Task 0: Verify `createdAt` wire format (manual, no code)

**Files:** none

- [ ] **Step 1: Trigger one fresh `GetNotificationData` request from the live app**

  Build + install `devDebug` on Android. Log in. Tap the bell icon (today it's a no-op so use Chucker to drive: open the app, force-trigger any backend call that returns `createdAt` in its payload — `GetLastOrder` works as a proxy for the legacy date format the backend uses).

  Run: `./gradlew :androidApp:installDevDebug` then launch from the device. Open Chucker (notification on the device after first network call).

- [ ] **Step 2: Inspect a `createdAt` field**

  Goal: confirm the value is lexically sortable (e.g. `"2026-05-26T14:32:00Z"` or `"2026-05-26 14:32:00"`). Anything starting with a year and using `YYYY-MM-DD` ordering sorts lexically.

  If the field is regional (e.g. `"26/05/2026 14:32"`) STOP and amend the spec — add an `epochMs INTEGER` derived column populated at upsert and sort on that instead.

- [ ] **Step 3: Record finding in plan**

  Edit this file to either:
  - Confirm: append `(✅ confirmed ISO-like via Chucker, YYYY-MM-DD prefix)` after the file's "Goal:" line.
  - Or: amend Task 12 (SQLDelight schema) to add an `epochMs` column.

---

## Task 1: Extend `MyKittaScaffold` with optional `snackbarHost` slot

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/simplr/mykitta2/ui/common/MyKittaScaffold.kt`

No new test — pure parameter addition with default value; all existing callers stay green.

- [ ] **Step 1: Add the parameter**

  Replace the existing `MyKittaScaffold` function:

```kotlin
package com.simplr.mykitta2.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyKittaScaffold(
    title: String? = null,
    onBack: (() -> Unit)? = null,
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            if (title != null || onBack != null) {
                TopAppBar(
                    title = { if (title != null) Text(title) },
                    navigationIcon = {
                        if (onBack != null) {
                            PlatformBackButton(onClick = onBack)
                        }
                    },
                )
            }
        },
        snackbarHost = snackbarHost,
        content = content,
    )
}

@Composable
fun <T> ListViewSeparated(
    items: List<T>,
    separator: @Composable () -> Unit,
    itemContent: @Composable (T) -> Unit
) {
    LazyColumn {
        itemsIndexed(items) { index, item ->

            itemContent(item)

            if (index < items.lastIndex) {
                separator()
            }
        }
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :shared:compileCommonMainKotlinMetadata`
Expected: `BUILD SUCCESSFUL`. No existing caller breaks because the new param defaults to a no-op.

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/simplr/mykitta2/ui/common/MyKittaScaffold.kt
git commit -m "feat(ui): add snackbarHost slot to MyKittaScaffold"
```

---

## Task 2: Add `PrincipalRepository.findById(id)` for notification tap-routing

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/simplr/mykitta2/data/repo/PrincipalRepository.kt`
- Test: existing `PrincipalRepositoryTest` (if any) — add one case; else inline in this task

Reuses the existing `principalQueries.selectById` SQL from `Principal.sq:14`.

- [ ] **Step 1: Write failing test**

Add to `shared/src/commonTest/kotlin/com/simplr/mykitta2/data/PrincipalRepositoryTest.kt` (create file if missing):

```kotlin
package com.simplr.mykitta2.data

import com.simplr.mykitta2.data.repo.DefaultPrincipalRepository
import com.simplr.mykitta2.data.prefs.SettingsCountryStore
import com.simplr.mykitta2.data.prefs.SettingsSessionStore
import com.russhwolf.settings.MapSettings
import com.simplr.mykitta2.shared.db.MyKittaDatabase
import com.simplr.mykitta2.test.makeInMemoryDatabase  // helper — see step 1a
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
            catalogApi = com.simplr.mykitta2.data.net.api.KtorCatalogApi(client),
            database = db,
            sessionStore = SettingsSessionStore(settings),
            countryStore = SettingsCountryStore(settings),
        )
    }
}
```

- [ ] **Step 1a: Create the in-memory DB helper if missing**

If `com.simplr.mykitta2.test.makeInMemoryDatabase` doesn't exist, add it for `androidHostTest` only (JVM SQLite driver). Create `shared/src/androidHostTest/kotlin/com/simplr/mykitta2/test/Db.kt`:

```kotlin
package com.simplr.mykitta2.test

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.simplr.mykitta2.shared.db.MyKittaDatabase

fun makeInMemoryDatabase(): MyKittaDatabase {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    MyKittaDatabase.Schema.create(driver)
    return MyKittaDatabase(driver)
}
```

Note: This test must move to `androidHostTest` (JVM SQLite). If a similar helper already exists, reuse it.

- [ ] **Step 2: Run — expect failure (no `findById` method)**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.simplr.mykitta2.data.PrincipalRepositoryFindByIdTest"`
Expected: FAIL — `findById` is unresolved.

- [ ] **Step 3: Implement `findById` on interface + impl**

Edit `shared/src/commonMain/kotlin/com/simplr/mykitta2/data/repo/PrincipalRepository.kt`:

```kotlin
interface PrincipalRepository {
    fun observeAll(): Flow<List<Principal>>
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

    // ... existing observeAll() + refresh() unchanged ...

    override suspend fun findById(principalId: String): Principal? =
        database.principalQueries.selectById(principalId)
            .executeAsOneOrNull()
            ?.toDomain()

    // ... existing toDomain() + companion unchanged ...
}
```

- [ ] **Step 4: Run — expect pass**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.simplr.mykitta2.data.PrincipalRepositoryFindByIdTest"`
Expected: 2 tests pass.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/simplr/mykitta2/data/repo/PrincipalRepository.kt \
        shared/src/commonTest/kotlin/com/simplr/mykitta2/data/PrincipalRepositoryFindByIdTest.kt \
        shared/src/androidHostTest/kotlin/com/simplr/mykitta2/test/Db.kt
git commit -m "feat(data): add PrincipalRepository.findById for cache lookups"
```

---

## Task 3: Add `Notification` domain model + `NotificationType` enum

**Files:**
- Create: `shared/src/commonMain/kotlin/com/simplr/mykitta2/domain/Notification.kt`
- Test: `shared/src/commonTest/kotlin/com/simplr/mykitta2/domain/NotificationTypeTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
package com.simplr.mykitta2.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationTypeTest {

    @Test fun fromWire_mapsPrincipalAndOrder() {
        assertEquals(NotificationType.PRINCIPAL, NotificationType.fromWire("Principal"))
        assertEquals(NotificationType.PRINCIPAL, NotificationType.fromWire("PRINCIPAL"))
        assertEquals(NotificationType.ORDER, NotificationType.fromWire("order"))
    }

    @Test fun fromWire_unknownAndNull_returnUNKNOWN() {
        assertEquals(NotificationType.UNKNOWN, NotificationType.fromWire(null))
        assertEquals(NotificationType.UNKNOWN, NotificationType.fromWire(""))
        assertEquals(NotificationType.UNKNOWN, NotificationType.fromWire("Promo"))
    }
}
```

- [ ] **Step 2: Run — expect failure**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.simplr.mykitta2.domain.NotificationTypeTest"`
Expected: FAIL — `NotificationType` is unresolved.

- [ ] **Step 3: Create the domain file**

```kotlin
package com.simplr.mykitta2.domain

/**
 * In-app notification record. Mirrors the legacy `Notif` payload but drops the
 * pre-parsed `payload_data` field — parsing is done on-demand at tap-time.
 * `createdAt` is assumed lexically sortable (ISO8601-like). See Task 0.
 */
data class Notification(
    val id: String,
    val title: String,
    val description: String,
    val type: NotificationType,
    val payload: String,
    val isRead: Boolean,
    val createdAt: String,
)

enum class NotificationType {
    PRINCIPAL,
    ORDER,
    UNKNOWN;

    companion object {
        fun fromWire(raw: String?): NotificationType = when (raw?.uppercase()) {
            "PRINCIPAL" -> PRINCIPAL
            "ORDER" -> ORDER
            else -> UNKNOWN
        }
    }
}
```

- [ ] **Step 4: Run — expect pass**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.simplr.mykitta2.domain.NotificationTypeTest"`
Expected: 2 tests pass.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/simplr/mykitta2/domain/Notification.kt \
        shared/src/commonTest/kotlin/com/simplr/mykitta2/domain/NotificationTypeTest.kt
git commit -m "feat(domain): add Notification model and NotificationType enum"
```

---

## Task 4: Add `NotificationDtos.kt` (list + mark-read DTOs)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/simplr/mykitta2/data/net/dto/NotificationDtos.kt`
- Test: `shared/src/commonTest/kotlin/com/simplr/mykitta2/data/net/dto/NotificationDtosTest.kt`

- [ ] **Step 1: Write failing serialization test**

```kotlin
package com.simplr.mykitta2.data.net.dto

import com.simplr.mykitta2.domain.NotificationType
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotificationDtosTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test fun listResponse_parsesItems_andDtoToDomain() {
        val payload = """
            {"getObjectResult":{"errorData":{"code":0,"description":""},"hasMoreRecords":1,
             "objectData":[[
               {"Id":"N1","Title":"New brand","Description":"Acme available now",
                "Type":"Principal","Payload":"{\"principalId\":\"P-1\"}",
                "IsRead":0,"CreatedAt":"2026-05-26T14:32:00Z"}
             ]]}}
        """.trimIndent()
        val parsed = json.decodeFromString<NotificationListServerResponse>(payload)
        val items = parsed.items()
        assertEquals(1, items.size)
        val domain = items[0].toDomain()
        assertEquals("N1", domain.id)
        assertEquals(NotificationType.PRINCIPAL, domain.type)
        assertEquals(false, domain.isRead)
    }

    @Test fun listResponse_emptyObjectData_returnsEmptyList() {
        val payload = """{"getObjectResult":{"errorData":{"code":0,"description":""},
            "hasMoreRecords":0,"objectData":[[]]}}""".trimIndent()
        assertTrue(json.decodeFromString<NotificationListServerResponse>(payload).items().isEmpty())
    }

    @Test fun markReadRequest_serializes_withOnlyNotifIDField() {
        val req = MarkNotificationReadRequest(notifId = "N1")
        val str = json.encodeToString(MarkNotificationReadRequest.serializer(), req)
        assertEquals("""{"NotifID":"N1"}""", str)
    }
}
```

- [ ] **Step 2: Run — expect failure (DTOs not defined)**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.simplr.mykitta2.data.net.dto.NotificationDtosTest"`
Expected: FAIL — `NotificationListServerResponse` unresolved.

- [ ] **Step 3: Create the DTO file**

```kotlin
package com.simplr.mykitta2.data.net.dto

import com.simplr.mykitta2.domain.Notification
import com.simplr.mykitta2.domain.NotificationType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `User/GetObject` response wrapper for `functionName = "GetNotificationData"`.
 *
 * `hasMoreRecords` is intentionally NOT exposed. Legacy contract is unreliable
 * (`Repository.getNotificationList` forces `hasMore=true`); we rely solely on
 * "page returned fewer items than pageSize" as the end-of-list signal.
 */
@Serializable
data class NotificationListServerResponse(
    @SerialName("getObjectResult") val getObjectResult: GetObjectResult<NotificationDto>,
) {
    fun items(): List<NotificationDto> = getObjectResult.objectData.firstOrNull().orEmpty()
}

@Serializable
data class NotificationDto(
    @SerialName("Id") val id: String? = null,
    @SerialName("Title") val title: String? = null,
    @SerialName("Description") val description: String? = null,
    @SerialName("Type") val type: String? = null,
    @SerialName("Payload") val payload: String? = null,
    @SerialName("IsRead") val isRead: Int = 0,
    @SerialName("CreatedAt") val createdAt: String? = null,
) {
    fun toDomain() = Notification(
        id = id.orEmpty(),
        title = title.orEmpty(),
        description = description.orEmpty(),
        type = NotificationType.fromWire(type),
        payload = payload.orEmpty(),
        isRead = isRead == 1,
        createdAt = createdAt.orEmpty(),
    )
}

/**
 * Body for `POST Notification/ReadNotification`. Dedicated endpoint, NOT routed
 * through `User/GetObject`. Field name `NotifID` matches legacy `NotificationRequest`.
 * Notification IDs are sent as strings even though legacy uses Int; the wire field
 * has always been string-formatted (`notifID.toString()`).
 */
@Serializable
data class MarkNotificationReadRequest(
    @SerialName("NotifID") val notifId: String,
)

@Serializable
data class MarkNotificationReadResponse(
    val errorData: ErrorData = ErrorData(),
)
```

- [ ] **Step 4: Run — expect pass**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.simplr.mykitta2.data.net.dto.NotificationDtosTest"`
Expected: 3 tests pass.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/simplr/mykitta2/data/net/dto/NotificationDtos.kt \
        shared/src/commonTest/kotlin/com/simplr/mykitta2/data/net/dto/NotificationDtosTest.kt
git commit -m "feat(net): add Notification list + mark-read DTOs"
```

---

## Task 5: Add `Notification.sq` SQLDelight schema

**Files:**
- Create: `shared/src/commonMain/sqldelight/com/simplr/mykitta2/shared/db/Notification.sq`

- [ ] **Step 1: Create the schema file**

```sql
-- User-scoped notification cache. Refreshed from `GetNotificationData` on
-- screen open (offset=0 only — deeper pages aren't reachable offline).
-- Wiped on logout via [com.simplr.mykitta2.data.repo.MyKittaDatabaseWiper].
CREATE TABLE IF NOT EXISTS Notification (
    id TEXT NOT NULL PRIMARY KEY,
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    type TEXT NOT NULL,
    payload TEXT NOT NULL,
    isRead INTEGER NOT NULL,
    createdAt TEXT NOT NULL
);

selectFirstPage:
SELECT * FROM Notification ORDER BY createdAt DESC LIMIT :pageSize;

countUnread:
SELECT COUNT(*) FROM Notification WHERE isRead = 0;

upsert:
INSERT OR REPLACE INTO Notification(id, title, description, type, payload, isRead, createdAt)
VALUES (?, ?, ?, ?, ?, ?, ?);

markRead:
UPDATE Notification SET isRead = 1 WHERE id = :id;

deleteAll:
DELETE FROM Notification;
```

- [ ] **Step 2: Regenerate SQLDelight interface**

Run: `./gradlew :shared:generateSqlDelightInterface`
Expected: `BUILD SUCCESSFUL`. New file appears at `shared/build/generated/sqldelight/code/MyKittaDatabase/commonMain/com/simplr/mykitta2/shared/db/NotificationQueries.kt`.

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :shared:compileCommonMainKotlinMetadata`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add shared/src/commonMain/sqldelight/com/simplr/mykitta2/shared/db/Notification.sq
git commit -m "feat(db): add Notification SQLDelight schema"
```

---

## Task 6: Add `KtorCatalogApi.callPath` helper + `getNotificationList` + `markNotificationRead`

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/simplr/mykitta2/data/net/api/CatalogApi.kt`

No standalone test — exercised through `NotificationRepositoryTest` (Tasks 8-11).

- [ ] **Step 1: Edit the interface and implementation**

Replace `CatalogApi.kt`:

```kotlin
package com.simplr.mykitta2.data.net.api

import com.simplr.mykitta2.data.net.dto.BannerServerResponse
import com.simplr.mykitta2.data.net.dto.ConfigListResponse
import com.simplr.mykitta2.data.net.dto.GetRequest
import com.simplr.mykitta2.data.net.dto.ItemServerResponse
import com.simplr.mykitta2.data.net.dto.LoyaltyPointsServerResponse
import com.simplr.mykitta2.data.net.dto.MarkNotificationReadRequest
import com.simplr.mykitta2.data.net.dto.MarkNotificationReadResponse
import com.simplr.mykitta2.data.net.dto.NotifCountServerResponse
import com.simplr.mykitta2.data.net.dto.NotificationListServerResponse
import com.simplr.mykitta2.data.net.dto.PrincipalServerResponse
import com.simplr.mykitta2.data.net.dto.ProfileServerResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.takeFrom

/**
 * Authenticated list-fetch endpoints. List reads go through `POST User/GetObject`
 * with a [GetRequest] body where `functionName` selects the actual procedure.
 * Writes (currently only [markNotificationRead]) hit dedicated endpoints via
 * [KtorCatalogApi.callPath].
 */
interface CatalogApi {
    suspend fun getBanners(baseUrl: String, request: GetRequest): BannerServerResponse
    suspend fun getItems(baseUrl: String, request: GetRequest): ItemServerResponse
    suspend fun getConfigList(baseUrl: String, request: GetRequest): ConfigListResponse
    suspend fun getNotificationCount(baseUrl: String, request: GetRequest): NotifCountServerResponse
    suspend fun getNotificationList(baseUrl: String, request: GetRequest): NotificationListServerResponse
    suspend fun markNotificationRead(baseUrl: String, request: MarkNotificationReadRequest): MarkNotificationReadResponse
    suspend fun getPrincipals(baseUrl: String, request: GetRequest): PrincipalServerResponse
    suspend fun getLoyaltyPoints(baseUrl: String, request: GetRequest): LoyaltyPointsServerResponse
    suspend fun getProfile(baseUrl: String, request: GetRequest): ProfileServerResponse
}

class KtorCatalogApi(private val client: HttpClient) : CatalogApi {

    private suspend inline fun <reified R> call(baseUrl: String, request: GetRequest): R {
        val url = URLBuilder().takeFrom(baseUrl).appendPathSegments("User", "GetObject").build()
        return client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    /** Sibling of [call] for endpoints not routed through `User/GetObject`. */
    private suspend inline fun <reified Body : Any, reified R> callPath(
        baseUrl: String,
        path: List<String>,
        body: Body,
    ): R {
        val url = URLBuilder().takeFrom(baseUrl).appendPathSegments(path).build()
        return client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()
    }

    override suspend fun getBanners(baseUrl: String, request: GetRequest) =
        call<BannerServerResponse>(baseUrl, request)

    override suspend fun getItems(baseUrl: String, request: GetRequest) =
        call<ItemServerResponse>(baseUrl, request)

    override suspend fun getConfigList(baseUrl: String, request: GetRequest) =
        call<ConfigListResponse>(baseUrl, request)

    override suspend fun getNotificationCount(baseUrl: String, request: GetRequest) =
        call<NotifCountServerResponse>(baseUrl, request)

    override suspend fun getNotificationList(baseUrl: String, request: GetRequest) =
        call<NotificationListServerResponse>(baseUrl, request)

    override suspend fun markNotificationRead(baseUrl: String, request: MarkNotificationReadRequest) =
        callPath<MarkNotificationReadRequest, MarkNotificationReadResponse>(
            baseUrl, listOf("Notification", "ReadNotification"), request,
        )

    override suspend fun getPrincipals(baseUrl: String, request: GetRequest) =
        call<PrincipalServerResponse>(baseUrl, request)

    override suspend fun getLoyaltyPoints(baseUrl: String, request: GetRequest) =
        call<LoyaltyPointsServerResponse>(baseUrl, request)

    override suspend fun getProfile(baseUrl: String, request: GetRequest) =
        call<ProfileServerResponse>(baseUrl, request)
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :shared:compileCommonMainKotlinMetadata`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/simplr/mykitta2/data/net/api/CatalogApi.kt
git commit -m "feat(net): add CatalogApi notification list + mark-read endpoints"
```

---

## Task 7: Scaffold `NotificationRepository` interface + `NotificationPage` + skeleton impl

**Files:**
- Create: `shared/src/commonMain/kotlin/com/simplr/mykitta2/data/repo/NotificationRepository.kt`

No test in this task — methods are stubs that throw. Tasks 8-10 implement and test each method one at a time.

- [ ] **Step 1: Create the file**

```kotlin
package com.simplr.mykitta2.data.repo

import com.simplr.mykitta2.core.env.BuildEnv
import com.simplr.mykitta2.core.error.ErrorMapper
import com.simplr.mykitta2.core.result.Outcome
import com.simplr.mykitta2.data.net.api.CatalogApi
import com.simplr.mykitta2.data.net.dto.GetRequest
import com.simplr.mykitta2.data.net.dto.MarkNotificationReadRequest
import com.simplr.mykitta2.data.prefs.CountryStore
import com.simplr.mykitta2.data.prefs.SessionStore
import com.simplr.mykitta2.domain.Country
import com.simplr.mykitta2.domain.Notification
import com.simplr.mykitta2.domain.NotificationType
import com.simplr.mykitta2.shared.db.MyKittaDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val PAGE_SIZE = 20

/**
 * Single source of truth for the unread-notification count via [unreadCount].
 * HomeStore subscribes to that flow for the top-bar badge; NotificationStore
 * calls [markAsRead] which decrements the flow — HomeStore updates automatically
 * with no cross-feature import.
 *
 * Pagination state lives in NotificationStore, not here. [loadPage] is a pure
 * function from (offset) → page; the repository owns no list state.
 */
interface NotificationRepository {
    val unreadCount: StateFlow<Int>
    suspend fun refreshCount(): Outcome<Int>
    suspend fun loadPage(offset: Int): Outcome<NotificationPage>
    suspend fun markAsRead(id: String): Outcome<Unit>
}

data class NotificationPage(
    val items: List<Notification>,
    val hasMore: Boolean,
    val fromCache: Boolean = false,
)

class DefaultNotificationRepository(
    private val catalogApi: CatalogApi,
    private val sessionStore: SessionStore,
    private val countryStore: CountryStore,
    private val db: MyKittaDatabase,
) : NotificationRepository {

    private val _unreadCount = MutableStateFlow(0)
    override val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    override suspend fun refreshCount(): Outcome<Int> =
        throw NotImplementedError("Task 8")

    override suspend fun loadPage(offset: Int): Outcome<NotificationPage> =
        throw NotImplementedError("Task 9")

    override suspend fun markAsRead(id: String): Outcome<Unit> =
        throw NotImplementedError("Task 10")

    private suspend fun baseUrl(): String =
        BuildEnv.baseUrlFor(countryStore.read() ?: Country.PH)

    private suspend fun supervisorRequest(
        functionName: String,
        offset: Int = 0,
    ) = GetRequest(
        functionName = functionName,
        offset = offset,
        recordsize = sessionStore.pagination(),
        search = "all",
        sort = "0",
        user = sessionStore.read()?.supervisorCode ?: FALLBACK_USER,
    )

    private inline fun <T> runCall(block: () -> T): Outcome<T> = try {
        Outcome.Success(block())
    } catch (t: Throwable) {
        Outcome.Failure(ErrorMapper.from(t))
    }

    private fun upsertCache(items: List<Notification>) {
        db.notificationQueries.transaction {
            items.forEach {
                db.notificationQueries.upsert(
                    id = it.id, title = it.title, description = it.description,
                    type = it.type.name, payload = it.payload,
                    isRead = if (it.isRead) 1L else 0L, createdAt = it.createdAt,
                )
            }
        }
    }

    private fun readCacheFirstPage(): List<Notification> =
        db.notificationQueries.selectFirstPage(PAGE_SIZE.toLong()).executeAsList().map { row ->
            Notification(
                id = row.id, title = row.title, description = row.description,
                type = NotificationType.fromWire(row.type), payload = row.payload,
                isRead = row.isRead == 1L, createdAt = row.createdAt,
            )
        }

    private companion object { const val FALLBACK_USER = "M1" }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :shared:compileCommonMainKotlinMetadata`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/simplr/mykitta2/data/repo/NotificationRepository.kt
git commit -m "feat(data): scaffold NotificationRepository interface and skeleton impl"
```

---

## Task 8: Implement `refreshCount()` with tests

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/simplr/mykitta2/data/repo/NotificationRepository.kt`
- Create: `shared/src/androidHostTest/kotlin/com/simplr/mykitta2/data/NotificationRepositoryTest.kt`

Test lives in `androidHostTest` so we get the real JVM SQLite driver (needed because the repo touches `db.notificationQueries` in later tasks). We could split tests across `commonTest` + `androidHostTest`, but bundling them in `androidHostTest` keeps the file singular and uses one consistent harness.

- [ ] **Step 1: Write failing tests**

```kotlin
package com.simplr.mykitta2.data

import com.russhwolf.settings.MapSettings
import com.simplr.mykitta2.core.result.Outcome
import com.simplr.mykitta2.data.net.api.KtorCatalogApi
import com.simplr.mykitta2.data.prefs.SettingsCountryStore
import com.simplr.mykitta2.data.prefs.SettingsSessionStore
import com.simplr.mykitta2.data.repo.DefaultNotificationRepository
import com.simplr.mykitta2.domain.Country
import com.simplr.mykitta2.domain.Session
import com.simplr.mykitta2.test.makeInMemoryDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NotificationRepositoryTest {

    private val jsonHeaders =
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    @Test fun refreshCount_success_updatesFlow_andReturnsCount() = runTest {
        val captured = mutableListOf<HttpRequestData>()
        val repo = harness({ req ->
            captured += req
            respond("""{"getObjectResult":{"errorData":{"code":0,"description":""},
                "hasMoreRecords":0,"objectData":[[{"count":7}]]}}""".trimIndent(),
                HttpStatusCode.OK, jsonHeaders)
        })
        val outcome = repo.refreshCount()
        assertIs<Outcome.Success<Int>>(outcome)
        assertEquals(7, outcome.value)
        assertEquals(7, repo.unreadCount.value)
        assertTrue(captured.single().url.toString().endsWith("/User/GetObject"))
    }

    @Test fun refreshCount_failure_keepsFlow_unchanged() = runTest {
        val repo = harness({ respondError(HttpStatusCode.InternalServerError) })
        // seed a known value through a successful call first
        // ... easier: rely on default 0
        val outcome = repo.refreshCount()
        assertIs<Outcome.Failure>(outcome)
        assertEquals(0, repo.unreadCount.value)
    }

    private fun harness(handler: MockRequestHandler): DefaultNotificationRepository {
        val settings = MapSettings()
        val sessionStore = SettingsSessionStore(settings)
        val countryStore = SettingsCountryStore(settings)
        // prime the stores; SettingsSessionStore/SettingsCountryStore expose suspend writes
        kotlinx.coroutines.runBlocking {
            sessionStore.write(Session(userName = "u", supervisorCode = "S1", isSupervisor = true))
            countryStore.write(Country.PH)
        }
        val client = HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) { json() }
        }
        return DefaultNotificationRepository(
            catalogApi = KtorCatalogApi(client),
            sessionStore = sessionStore,
            countryStore = countryStore,
            db = makeInMemoryDatabase(),
        )
    }
}
```

NOTE: `kotlinx.coroutines.runBlocking` is JVM/native only and works in `androidHostTest`. If the existing harness pattern in `HomeRepositoryTest` uses `runTest`'s suspend builder, mirror that style instead — re-read `HomeRepositoryTest.kt` lines 75-110 for the canonical pattern.

- [ ] **Step 2: Run — expect failure (NotImplementedError)**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.simplr.mykitta2.data.NotificationRepositoryTest"`
Expected: 2 tests fail with `NotImplementedError("Task 8")`.

- [ ] **Step 3: Implement `refreshCount()`**

In `NotificationRepository.kt` replace the `refreshCount` stub:

```kotlin
override suspend fun refreshCount(): Outcome<Int> = runCall {
    val count = catalogApi
        .getNotificationCount(baseUrl(), supervisorRequest("GetNotificationCount"))
        .count()
    _unreadCount.value = count
    count
}
```

- [ ] **Step 4: Run — expect pass**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.simplr.mykitta2.data.NotificationRepositoryTest"`
Expected: 2 tests pass.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/simplr/mykitta2/data/repo/NotificationRepository.kt \
        shared/src/androidHostTest/kotlin/com/simplr/mykitta2/data/NotificationRepositoryTest.kt
git commit -m "feat(data): implement NotificationRepository.refreshCount"
```

---

## Task 9: Implement `loadPage()` — network success + cache upsert + size-short hasMore

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/simplr/mykitta2/data/repo/NotificationRepository.kt`
- Modify: `shared/src/androidHostTest/kotlin/com/simplr/mykitta2/data/NotificationRepositoryTest.kt`

- [ ] **Step 1: Add failing tests**

Append to `NotificationRepositoryTest`:

```kotlin
@Test fun loadPage_offset0_fullPage_returnsHasMoreTrue_andUpsertsCache() = runTest {
    val twentyItems = buildItemsJson(count = 20, startId = 1)
    val captured = mutableListOf<HttpRequestData>()
    val repo = harness({ req -> captured += req; respond(twentyItems, HttpStatusCode.OK, jsonHeaders) })

    val outcome = repo.loadPage(offset = 0)
    assertIs<Outcome.Success<com.simplr.mykitta2.data.repo.NotificationPage>>(outcome)
    val page = outcome.value
    assertEquals(20, page.items.size)
    assertTrue(page.hasMore)
    assertEquals(false, page.fromCache)
    // cache contains 20 rows after upsert
    // (use a second repo instance reading from the same DB to verify offline-fallback works later)
}

@Test fun loadPage_offset0_shortPage_returnsHasMoreFalse() = runTest {
    val sevenItems = buildItemsJson(count = 7, startId = 1)
    val repo = harness({ respond(sevenItems, HttpStatusCode.OK, jsonHeaders) })
    val outcome = repo.loadPage(0)
    assertIs<Outcome.Success<com.simplr.mykitta2.data.repo.NotificationPage>>(outcome)
    assertEquals(7, outcome.value.items.size)
    assertEquals(false, outcome.value.hasMore)
}

@Test fun loadPage_ignoresServerHasMoreRecords() = runTest {
    // 5 items but server says hasMoreRecords=1 — repository must NOT trust it
    val fiveButServerSaysMore = buildItemsJson(count = 5, startId = 1, hasMoreRecords = 1)
    val repo = harness({ respond(fiveButServerSaysMore, HttpStatusCode.OK, jsonHeaders) })
    val outcome = repo.loadPage(0)
    assertIs<Outcome.Success<com.simplr.mykitta2.data.repo.NotificationPage>>(outcome)
    assertEquals(false, outcome.value.hasMore, "size<PAGE_SIZE must win over server's hasMoreRecords")
}

private fun buildItemsJson(count: Int, startId: Int, hasMoreRecords: Int = 0): String {
    val items = (0 until count).joinToString(",") { i ->
        val id = startId + i
        """{"Id":"N$id","Title":"T$id","Description":"D$id","Type":"Order",
           "Payload":"{}","IsRead":0,"CreatedAt":"2026-05-${10 + (i % 20)}T00:00:00Z"}"""
    }
    return """{"getObjectResult":{"errorData":{"code":0,"description":""},
        "hasMoreRecords":$hasMoreRecords,"objectData":[[$items]]}}""".trimIndent()
}
```

- [ ] **Step 2: Run — expect failure**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.simplr.mykitta2.data.NotificationRepositoryTest"`
Expected: 3 new tests fail with `NotImplementedError("Task 9")`.

- [ ] **Step 3: Implement `loadPage()` happy path (no offline fallback yet)**

In `NotificationRepository.kt` replace the `loadPage` stub:

```kotlin
override suspend fun loadPage(offset: Int): Outcome<NotificationPage> = runCall {
    val response = catalogApi.getNotificationList(
        baseUrl(),
        supervisorRequest("GetNotificationData", offset = offset),
    )
    val items = response.items().map { it.toDomain() }
    if (offset == 0) upsertCache(items)
    NotificationPage(
        items = items,
        hasMore = items.size >= PAGE_SIZE,    // server's hasMoreRecords is unreliable
        fromCache = false,
    )
}
```

- [ ] **Step 4: Run — expect pass for the 3 new tests**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.simplr.mykitta2.data.NotificationRepositoryTest"`
Expected: 5 total tests pass (2 from Task 8 + 3 from Task 9).

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/simplr/mykitta2/data/repo/NotificationRepository.kt \
        shared/src/androidHostTest/kotlin/com/simplr/mykitta2/data/NotificationRepositoryTest.kt
git commit -m "feat(data): implement loadPage happy-path with size-based hasMore"
```

---

## Task 10: `loadPage()` offline fallback (network fail + cache fallback for offset=0 only)

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/simplr/mykitta2/data/repo/NotificationRepository.kt`
- Modify: `shared/src/androidHostTest/kotlin/com/simplr/mykitta2/data/NotificationRepositoryTest.kt`

- [ ] **Step 1: Add failing tests**

```kotlin
@Test fun loadPage_offset0_networkFails_withEmptyCache_propagatesFailure() = runTest {
    val repo = harness({ respondError(HttpStatusCode.InternalServerError) })
    val outcome = repo.loadPage(0)
    assertIs<Outcome.Failure>(outcome)
}

@Test fun loadPage_offset0_networkFails_withCachedRows_returnsCache_withFromCacheTrue() = runTest {
    // Seed the cache via a successful call first, then swap to a failing engine
    val responses = mutableListOf<MockRequestHandler>(
        { respond(buildItemsJson(count = 3, startId = 100), HttpStatusCode.OK, jsonHeaders) },
        { respondError(HttpStatusCode.InternalServerError) },
    )
    val repo = harnessSequence(responses)

    val first = repo.loadPage(0)   // populates cache
    assertIs<Outcome.Success<com.simplr.mykitta2.data.repo.NotificationPage>>(first)

    val second = repo.loadPage(0)  // network fails — cache fallback
    assertIs<Outcome.Success<com.simplr.mykitta2.data.repo.NotificationPage>>(second)
    assertEquals(3, second.value.items.size)
    assertEquals(true, second.value.fromCache)
    assertEquals(false, second.value.hasMore)
}

@Test fun loadPage_deepOffset_networkFails_doesNotFallBack() = runTest {
    val repo = harness({ respondError(HttpStatusCode.InternalServerError) })
    val outcome = repo.loadPage(offset = 20)
    assertIs<Outcome.Failure>(outcome)
}

@Test fun loadPage_deepOffset_success_doesNotUpsertCache() = runTest {
    val sevenAtOffset20 = buildItemsJson(count = 7, startId = 50)
    val responses = mutableListOf<MockRequestHandler>(
        { respond(sevenAtOffset20, HttpStatusCode.OK, jsonHeaders) },
        { respondError(HttpStatusCode.InternalServerError) },
    )
    val repo = harnessSequence(responses)

    val first = repo.loadPage(offset = 20)   // success but deep — should NOT cache
    assertIs<Outcome.Success<com.simplr.mykitta2.data.repo.NotificationPage>>(first)

    // Try the offline fallback for offset=0 — cache must still be empty
    val second = repo.loadPage(offset = 0)
    assertIs<Outcome.Failure>(second)   // no cache to fall back on
}

// Helper that returns a different response per call in sequence
private fun harnessSequence(handlers: MutableList<MockRequestHandler>): DefaultNotificationRepository {
    val settings = MapSettings()
    val sessionStore = SettingsSessionStore(settings)
    val countryStore = SettingsCountryStore(settings)
    kotlinx.coroutines.runBlocking {
        sessionStore.write(Session(userName = "u", supervisorCode = "S1", isSupervisor = true))
        countryStore.write(Country.PH)
    }
    val client = HttpClient(MockEngine { req ->
        val next = handlers.removeAt(0)
        next(this, req)
    }) { install(ContentNegotiation) { json() } }
    return DefaultNotificationRepository(
        catalogApi = KtorCatalogApi(client),
        sessionStore = sessionStore,
        countryStore = countryStore,
        db = makeInMemoryDatabase(),
    )
}
```

- [ ] **Step 2: Run — expect 4 failures**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.simplr.mykitta2.data.NotificationRepositoryTest"`
Expected: 4 of the new tests fail because offline-fallback isn't wired yet (`loadPage` returns the raw `Outcome.Failure` instead of trying cache).

- [ ] **Step 3: Wrap `loadPage` with offline fallback**

In `NotificationRepository.kt` replace `loadPage` again:

```kotlin
override suspend fun loadPage(offset: Int): Outcome<NotificationPage> {
    val networkOutcome = runCall {
        val response = catalogApi.getNotificationList(
            baseUrl(),
            supervisorRequest("GetNotificationData", offset = offset),
        )
        val items = response.items().map { it.toDomain() }
        if (offset == 0) upsertCache(items)
        NotificationPage(
            items = items,
            hasMore = items.size >= PAGE_SIZE,
            fromCache = false,
        )
    }
    // Offline fallback: only for first page. Deeper pages surface the original
    // failure so the UI can show a "load more failed — retry" affordance.
    return if (networkOutcome is Outcome.Failure && offset == 0) {
        val cached = readCacheFirstPage()
        if (cached.isNotEmpty()) {
            Outcome.Success(NotificationPage(items = cached, hasMore = false, fromCache = true))
        } else {
            networkOutcome
        }
    } else networkOutcome
}
```

- [ ] **Step 4: Run — expect pass**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.simplr.mykitta2.data.NotificationRepositoryTest"`
Expected: 9 total tests pass.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/simplr/mykitta2/data/repo/NotificationRepository.kt \
        shared/src/androidHostTest/kotlin/com/simplr/mykitta2/data/NotificationRepositoryTest.kt
git commit -m "feat(data): wire offline-cache fallback for first page only"
```

---

## Task 11: Implement `markAsRead()` (success, server fail, clamp-at-zero)

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/simplr/mykitta2/data/repo/NotificationRepository.kt`
- Modify: `shared/src/androidHostTest/kotlin/com/simplr/mykitta2/data/NotificationRepositoryTest.kt`

- [ ] **Step 1: Add failing tests**

```kotlin
@Test fun markAsRead_success_hitsDedicatedEndpoint_decrementsCount_updatesCache() = runTest {
    val captured = mutableListOf<HttpRequestData>()
    val responses = mutableListOf<MockRequestHandler>(
        // 1. seed cache + count via successful first page
        { req -> captured += req
          respond(buildItemsJson(count = 1, startId = 7), HttpStatusCode.OK, jsonHeaders) },
        // 2. seed count to 5
        { req -> captured += req
          respond("""{"getObjectResult":{"errorData":{"code":0,"description":""},
              "hasMoreRecords":0,"objectData":[[{"count":5}]]}}""".trimIndent(),
              HttpStatusCode.OK, jsonHeaders) },
        // 3. markAsRead response
        { req -> captured += req
          respond("""{"errorData":{"code":0,"description":""}}""", HttpStatusCode.OK, jsonHeaders) },
    )
    val repo = harnessSequence(responses)
    repo.loadPage(0)
    repo.refreshCount()
    assertEquals(5, repo.unreadCount.value)

    val outcome = repo.markAsRead("N7")
    assertIs<Outcome.Success<Unit>>(outcome)
    assertEquals(4, repo.unreadCount.value)

    val markReadCall = captured.last()
    assertTrue(markReadCall.url.toString().endsWith("/Notification/ReadNotification"))
}

@Test fun markAsRead_serverFailure_doesNotMutateState() = runTest {
    val responses = mutableListOf<MockRequestHandler>(
        // seed count to 3
        { respond("""{"getObjectResult":{"errorData":{"code":0,"description":""},
            "hasMoreRecords":0,"objectData":[[{"count":3}]]}}""".trimIndent(),
            HttpStatusCode.OK, jsonHeaders) },
        // markAsRead 500
        { respondError(HttpStatusCode.InternalServerError) },
    )
    val repo = harnessSequence(responses)
    repo.refreshCount()
    assertEquals(3, repo.unreadCount.value)

    val outcome = repo.markAsRead("N1")
    assertIs<Outcome.Failure>(outcome)
    assertEquals(3, repo.unreadCount.value)  // unchanged
}

@Test fun markAsRead_whenCountIsZero_clamps_doesNotGoNegative() = runTest {
    val responses = mutableListOf<MockRequestHandler>(
        { respond("""{"errorData":{"code":0,"description":""}}""", HttpStatusCode.OK, jsonHeaders) },
    )
    val repo = harnessSequence(responses)
    // unreadCount defaults to 0
    val outcome = repo.markAsRead("Nothing")
    assertIs<Outcome.Success<Unit>>(outcome)
    assertEquals(0, repo.unreadCount.value)
}
```

- [ ] **Step 2: Run — expect 3 failures (`NotImplementedError("Task 10")` — keep the original message in stub if you want pinpoint info, or update it)**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.simplr.mykitta2.data.NotificationRepositoryTest"`
Expected: 3 new failures.

- [ ] **Step 3: Implement `markAsRead`**

Replace the stub in `NotificationRepository.kt`:

```kotlin
override suspend fun markAsRead(id: String): Outcome<Unit> = runCall {
    catalogApi.markNotificationRead(baseUrl(), MarkNotificationReadRequest(notifId = id))
    db.notificationQueries.markRead(id)
    _unreadCount.update { (it - 1).coerceAtLeast(0) }
}
```

- [ ] **Step 4: Run — expect pass**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.simplr.mykitta2.data.NotificationRepositoryTest"`
Expected: 12 total tests pass.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/simplr/mykitta2/data/repo/NotificationRepository.kt \
        shared/src/androidHostTest/kotlin/com/simplr/mykitta2/data/NotificationRepositoryTest.kt
git commit -m "feat(data): implement markAsRead with optimistic count decrement"
```

---

## Task 12: Wire `NotificationRepository` in Koin's `repositoryModule`

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/simplr/mykitta2/di/AppModule.kt`

- [ ] **Step 1: Add binding inside `repositoryModule`**

Edit `AppModule.kt`. Inside the `repositoryModule` block, append:

```kotlin
single<NotificationRepository> {
    DefaultNotificationRepository(
        catalogApi = get(),
        sessionStore = get(),
        countryStore = get(),
        db = get(),
    )
}
```

Add the import at the top:

```kotlin
import com.simplr.mykitta2.data.repo.DefaultNotificationRepository
import com.simplr.mykitta2.data.repo.NotificationRepository
```

- [ ] **Step 2: Compile**

Run: `./gradlew :shared:compileCommonMainKotlinMetadata`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/simplr/mykitta2/di/AppModule.kt
git commit -m "feat(di): register NotificationRepository as Koin single"
```

---

## Task 13: Extend `LocalDataWiper` to wipe Notification table (in a transaction)

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/simplr/mykitta2/data/repo/LocalDataWiper.kt`
- Modify: `shared/src/androidHostTest/kotlin/com/simplr/mykitta2/data/MyKittaDatabaseWiperTest.kt` (or create)

- [ ] **Step 1: Locate or create the wiper test**

Run: `find shared/src/androidHostTest -name "MyKittaDatabaseWiperTest*"`. If the file doesn't exist yet, create `shared/src/androidHostTest/kotlin/com/simplr/mykitta2/data/MyKittaDatabaseWiperTest.kt` (used in next step).

- [ ] **Step 2: Write failing test**

```kotlin
package com.simplr.mykitta2.data

import com.simplr.mykitta2.data.repo.MyKittaDatabaseWiper
import com.simplr.mykitta2.test.makeInMemoryDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MyKittaDatabaseWiperTest {

    @Test fun wipeAll_removesNotifications_andPrincipals() = runTest {
        val db = makeInMemoryDatabase()
        // seed 3 notifications + 1 principal
        db.notificationQueries.upsert(
            id = "N1", title = "T", description = "D", type = "Order",
            payload = "{}", isRead = 0L, createdAt = "2026-05-26T00:00:00Z")
        db.notificationQueries.upsert(
            id = "N2", title = "T", description = "D", type = "Principal",
            payload = "{}", isRead = 0L, createdAt = "2026-05-26T00:00:01Z")
        db.notificationQueries.upsert(
            id = "N3", title = "T", description = "D", type = "Order",
            payload = "{}", isRead = 1L, createdAt = "2026-05-26T00:00:02Z")
        db.principalQueries.upsert(
            principalId = "P1", principalName = "A", principalImg = "",
            isActive = 1L, sortOrder = 0L)

        MyKittaDatabaseWiper(db).wipeAll()

        assertEquals(0L, db.notificationQueries.countUnread().executeAsOne())
        assertEquals(0, db.notificationQueries.selectFirstPage(20).executeAsList().size)
        assertEquals(0, db.principalQueries.selectAll().executeAsList().size)
    }
}
```

- [ ] **Step 3: Run — expect failure**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.simplr.mykitta2.data.MyKittaDatabaseWiperTest"`
Expected: FAIL — notification rows not deleted (wiper doesn't know about the table yet).

- [ ] **Step 4: Update the wiper**

Replace `MyKittaDatabaseWiper` in `LocalDataWiper.kt`:

```kotlin
class MyKittaDatabaseWiper(
    private val database: MyKittaDatabase,
) : LocalDataWiper {
    // User-scoped tables. Wrap in a transaction so a partial failure can't
    // leave the user with mixed-tenancy data on disk.
    override suspend fun wipeAll() {
        database.principalQueries.transaction {
            database.principalQueries.deleteAll()
            database.notificationQueries.deleteAll()
        }
    }
}
```

- [ ] **Step 5: Run — expect pass**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.simplr.mykitta2.data.MyKittaDatabaseWiperTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/simplr/mykitta2/data/repo/LocalDataWiper.kt \
        shared/src/androidHostTest/kotlin/com/simplr/mykitta2/data/MyKittaDatabaseWiperTest.kt
git commit -m "feat(data): wipe Notification table on logout in a transaction"
```

---

## Task 14: Swap `HomeStore` count source from `HomeRepository` to `NotificationRepository`

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/simplr/mykitta2/data/repo/HomeRepository.kt`
- Modify: `shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/home/HomeStore.kt`
- Modify: `shared/src/commonMain/kotlin/com/simplr/mykitta2/di/AppModule.kt`
- Modify: `shared/src/commonTest/kotlin/com/simplr/mykitta2/feature/home/HomeStoreTest.kt`

**One commit** so the badge is never broken between intermediate states.

- [ ] **Step 1: Write failing tests in `HomeStoreTest`**

Add inside the existing `HomeStoreTest` class:

```kotlin
@Test fun bootstrap_subscribesToUnreadCountFlow() = runTest {
    val notifRepo = FakeNotificationRepository(initialCount = 5)
    val store = makeStore(notificationRepository = notifRepo)
    advanceUntilIdle()
    assertEquals(5, store.state.notifCount)
}

@Test fun unreadCount_flow_updates_propagateToState() = runTest {
    val notifRepo = FakeNotificationRepository(initialCount = 5)
    val store = makeStore(notificationRepository = notifRepo)
    advanceUntilIdle()
    notifRepo.setUnreadCount(2)
    advanceUntilIdle()
    assertEquals(2, store.state.notifCount)
}

@Test fun refreshNotifications_intent_callsRefreshCount() = runTest {
    val notifRepo = FakeNotificationRepository(initialCount = 0)
    val store = makeStore(notificationRepository = notifRepo)
    advanceUntilIdle()
    store.accept(HomeStore.Intent.RefreshNotifications)
    advanceUntilIdle()
    assertEquals(1, notifRepo.refreshCountInvocations)
}
```

Add the fake at the bottom of the test file:

```kotlin
private class FakeNotificationRepository(
    initialCount: Int = 0,
) : com.simplr.mykitta2.data.repo.NotificationRepository {
    private val _flow = kotlinx.coroutines.flow.MutableStateFlow(initialCount)
    override val unreadCount: kotlinx.coroutines.flow.StateFlow<Int> = _flow.asStateFlow()
    var refreshCountInvocations = 0
        private set
    fun setUnreadCount(n: Int) { _flow.value = n }
    override suspend fun refreshCount(): com.simplr.mykitta2.core.result.Outcome<Int> {
        refreshCountInvocations++
        return com.simplr.mykitta2.core.result.Outcome.Success(_flow.value)
    }
    override suspend fun loadPage(offset: Int) = error("not used by HomeStore")
    override suspend fun markAsRead(id: String) = error("not used by HomeStore")
}
```

Adjust the existing `makeStore(...)` helper signature to accept `notificationRepository`. If the existing helper is named differently, mirror the convention.

- [ ] **Step 2: Run — expect failure (RefreshNotifications intent doesn't exist; constructor mismatch)**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.simplr.mykitta2.feature.home.HomeStoreTest"`
Expected: compile failure (`Intent.RefreshNotifications` unresolved, `HomeStoreFactory` ctor mismatch).

- [ ] **Step 3: Drop `loadNotificationCount` from `HomeRepository`**

Edit `HomeRepository.kt`:
- Remove `suspend fun loadNotificationCount(): Outcome<Int>` from the interface.
- Remove the `override suspend fun loadNotificationCount() = runCall { ... }` block in `DefaultHomeRepository`.

- [ ] **Step 4: Update `HomeStore` to subscribe to `NotificationRepository.unreadCount`**

Edit `HomeStore.kt`. Concrete changes:

```kotlin
// Add intent (in sealed interface Intent):
data object RefreshNotifications : Intent

// Update factory constructor + add notificationRepository:
class HomeStoreFactory(
    private val storeFactory: StoreFactory,
    private val homeRepository: HomeRepository,
    private val notificationRepository: NotificationRepository,
) {
    // ... existing create() unchanged ...

    private sealed interface Action {
        data object LoadAll : Action
        data object RefreshNotifCount : Action
        data class NotifCountObserved(val count: Int) : Action
    }

    private inner class BootstrapperImpl : CoroutineBootstrapper<Action>() {
        override fun invoke() {
            scope.launch {
                notificationRepository.unreadCount.collect { count ->
                    dispatch(Action.NotifCountObserved(count))
                }
            }
            dispatch(Action.LoadAll)
            dispatch(Action.RefreshNotifCount)
        }
    }

    private inner class ExecutorImpl :
        CoroutineExecutor<HomeStore.Intent, Action, HomeStore.State, Message, HomeStore.Label>() {

        override fun executeAction(action: Action) {
            when (action) {
                Action.LoadAll -> loadAll()
                Action.RefreshNotifCount -> scope.launch {
                    // Failure silently ignored — badge stays at prior value
                    notificationRepository.refreshCount()
                }
                is Action.NotifCountObserved ->
                    dispatch(Message.NotifCountLoaded(action.count))
            }
        }

        override fun executeIntent(intent: HomeStore.Intent) {
            when (intent) {
                HomeStore.Intent.Refresh -> {
                    if (state().refreshing) return
                    dispatch(Message.RefreshStarted)
                    loadAll(onFinished = { dispatch(Message.RefreshFinished) })
                }
                HomeStore.Intent.RefreshNotifications ->
                    forward(Action.RefreshNotifCount)   // see helper below
                HomeStore.Intent.RefreshPoints -> {
                    if (state().pointsLoading) return
                    loadPoints()
                }
                is HomeStore.Intent.ItemClicked ->
                    publish(HomeStore.Label.ShowSnackbar("${intent.item.productDesc} — detail page coming soon"))
                is HomeStore.Intent.BannerClicked ->
                    publish(HomeStore.Label.ShowSnackbar(intent.banner.bannerName))
            }
        }

        // MVIKotlin's executor doesn't have a forward(Action) by default; emulate
        // by inlining the same scope.launch as RefreshNotifCount:
        private fun forward(action: Action) {
            when (action) {
                Action.RefreshNotifCount -> scope.launch { notificationRepository.refreshCount() }
                else -> Unit
            }
        }

        // Remove the in-place loadNotificationCount block — count now arrives via
        // the unreadCount.collect{} subscription in BootstrapperImpl above.
        private fun loadAll(onFinished: (() -> Unit)? = null) {
            // ... existing scope.launch blocks for banners + rails ...
            // DELETE the scope.launch { ... homeRepository.loadNotificationCount() ... } block
            loadPoints()
        }
    }

    // ... rest unchanged ...
}
```

Add the import at the top of `HomeStore.kt`:

```kotlin
import com.simplr.mykitta2.data.repo.NotificationRepository
```

- [ ] **Step 5: Update DI binding for `HomeStoreFactory`**

In `AppModule.kt` change:

```kotlin
val featureHomeModule = module {
    factory { HomeStoreFactory(storeFactory = get(), homeRepository = get(), notificationRepository = get()) }
    viewModelOf(::HomeViewModel)
}
```

- [ ] **Step 6: Run — expect pass**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.simplr.mykitta2.feature.home.HomeStoreTest"`
Expected: all existing + 3 new tests pass.

Also run `HomeRepositoryTest` to ensure removing the count method didn't break anything:

Run: `./gradlew :shared:testAndroidHostTest --tests "com.simplr.mykitta2.data.HomeRepositoryTest"`
Expected: existing tests still pass (note: any `HomeRepositoryTest` test that asserted `loadNotificationCount` results MUST be deleted; the spec moves that responsibility).

- [ ] **Step 7: Commit**

```bash
git add shared/src/commonMain/kotlin/com/simplr/mykitta2/data/repo/HomeRepository.kt \
        shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/home/HomeStore.kt \
        shared/src/commonMain/kotlin/com/simplr/mykitta2/di/AppModule.kt \
        shared/src/commonTest/kotlin/com/simplr/mykitta2/feature/home/HomeStoreTest.kt \
        shared/src/commonTest/kotlin/com/simplr/mykitta2/data/HomeRepositoryTest.kt
git commit -m "refactor(home): swap notif-count source from HomeRepository to NotificationRepository"
```

---

## Task 15: Scaffold `NotificationStore` + `NotificationViewModel` (stub bootstrap)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/notification/NotificationStore.kt`
- Create: `shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/notification/NotificationViewModel.kt`

No tests in this task — pure interface + skeleton; bootstrap is implemented in Task 16.

- [ ] **Step 1: Create `NotificationStore.kt`**

```kotlin
package com.simplr.mykitta2.feature.notification

import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineBootstrapper
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import com.simplr.mykitta2.core.error.ErrorMapper
import com.simplr.mykitta2.core.result.Outcome
import com.simplr.mykitta2.data.repo.NotificationRepository
import com.simplr.mykitta2.data.repo.PrincipalRepository
import com.simplr.mykitta2.domain.Notification
import com.simplr.mykitta2.domain.NotificationType
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

interface NotificationStore : Store<NotificationStore.Intent, NotificationStore.State, NotificationStore.Label> {

    data class State(
        val items: List<Notification> = emptyList(),
        val offset: Int = 0,
        val endReached: Boolean = false,
        val firstLoadInFlight: Boolean = true,
        val loadingMore: Boolean = false,
        val error: String? = null,
        val showingCache: Boolean = false,
    )

    sealed interface Intent {
        data object LoadNextPage : Intent
        data object Refresh : Intent
        data class TapItem(val notification: Notification) : Intent
        data object DismissError : Intent
    }

    sealed interface Label {
        data class NavigateToPrincipal(val principalId: String, val principalName: String) : Label
        data object NavigateUnsupportedType : Label
        data class ShowSnackbar(val text: String) : Label
    }
}

class NotificationStoreFactory(
    private val storeFactory: StoreFactory,
    private val notificationRepository: NotificationRepository,
    private val principalRepository: PrincipalRepository,
) {
    fun create(): NotificationStore =
        object : NotificationStore,
            Store<NotificationStore.Intent, NotificationStore.State, NotificationStore.Label>
            by storeFactory.create(
                name = "NotificationStore",
                initialState = NotificationStore.State(),
                bootstrapper = BootstrapperImpl(),
                executorFactory = { ExecutorImpl() },
                reducer = ReducerImpl,
            ) {}

    private sealed interface Action {
        data object LoadFirstPage : Action
    }

    private sealed interface Message {
        data object FirstLoadStarted : Message
        data class PageLoaded(
            val items: List<Notification>,
            val nextOffset: Int,
            val endReached: Boolean,
            val fromCache: Boolean,
        ) : Message
        data class LoadFailed(val error: String) : Message
        data object LoadingMoreStarted : Message
        data class MarkedRead(val id: String) : Message
        data class ErrorSet(val error: String?) : Message
        data object Reset : Message
    }

    private inner class BootstrapperImpl : CoroutineBootstrapper<Action>() {
        override fun invoke() {
            dispatch(Action.LoadFirstPage)
        }
    }

    private inner class ExecutorImpl :
        CoroutineExecutor<NotificationStore.Intent, Action, NotificationStore.State, Message, NotificationStore.Label>() {

        override fun executeAction(action: Action) {
            when (action) {
                Action.LoadFirstPage -> loadPage(offset = 0, isFirstLoad = true)
            }
        }

        override fun executeIntent(intent: NotificationStore.Intent) {
            // implemented in Tasks 16-19
        }

        private fun loadPage(offset: Int, isFirstLoad: Boolean) {
            // implemented in Task 16
        }
    }

    private object ReducerImpl : Reducer<NotificationStore.State, Message> {
        override fun NotificationStore.State.reduce(msg: Message): NotificationStore.State = when (msg) {
            Message.FirstLoadStarted -> copy(firstLoadInFlight = true, error = null)
            is Message.PageLoaded -> copy(
                items = if (offset == 0) msg.items else items + msg.items,
                offset = msg.nextOffset,
                endReached = msg.endReached,
                firstLoadInFlight = false,
                loadingMore = false,
                showingCache = msg.fromCache,
                error = null,
            )
            is Message.LoadFailed -> copy(
                firstLoadInFlight = false,
                loadingMore = false,
                error = msg.error,
            )
            Message.LoadingMoreStarted -> copy(loadingMore = true)
            is Message.MarkedRead -> copy(
                items = items.map { if (it.id == msg.id) it.copy(isRead = true) else it },
            )
            is Message.ErrorSet -> copy(error = msg.error)
            Message.Reset -> NotificationStore.State()
        }
    }
}
```

- [ ] **Step 2: Create `NotificationViewModel.kt`**

```kotlin
package com.simplr.mykitta2.feature.notification

import com.simplr.mykitta2.core.mvi.ScreenViewModel

class NotificationViewModel(
    storeFactory: NotificationStoreFactory,
) : ScreenViewModel<NotificationStore.Intent, NotificationStore.State, NotificationStore.Label>(
    store = storeFactory.create(),
)
```

- [ ] **Step 3: Compile**

Run: `./gradlew :shared:compileCommonMainKotlinMetadata`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/notification/NotificationStore.kt \
        shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/notification/NotificationViewModel.kt
git commit -m "feat(feature): scaffold NotificationStore + ViewModel"
```

---

## Task 16: Implement `loadPage` in Store + tests (bootstrap success, short page, network failure, fromCache)

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/notification/NotificationStore.kt`
- Create: `shared/src/commonTest/kotlin/com/simplr/mykitta2/feature/notification/NotificationStoreTest.kt`

- [ ] **Step 1: Write failing tests + the test fakes**

```kotlin
package com.simplr.mykitta2.feature.notification

import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import com.simplr.mykitta2.core.result.Outcome
import com.simplr.mykitta2.data.repo.NotificationPage
import com.simplr.mykitta2.data.repo.NotificationRepository
import com.simplr.mykitta2.data.repo.PrincipalRepository
import com.simplr.mykitta2.domain.Notification
import com.simplr.mykitta2.domain.NotificationType
import com.simplr.mykitta2.domain.Principal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotificationStoreTest {

    @Test fun bootstrap_loadsFirstPage_andTransitionsOutOfFirstLoad() = runTest {
        val repo = FakeNotificationRepository(
            pages = mapOf(0 to Outcome.Success(NotificationPage(
                items = notifications(count = 20), hasMore = true, fromCache = false))),
        )
        val store = makeStore(repo)
        advanceUntilIdle()
        assertEquals(20, store.state.items.size)
        assertFalse(store.state.firstLoadInFlight)
        assertFalse(store.state.endReached)
        assertNull(store.state.error)
    }

    @Test fun bootstrap_shortPage_setsEndReached() = runTest {
        val repo = FakeNotificationRepository(
            pages = mapOf(0 to Outcome.Success(NotificationPage(
                items = notifications(count = 5), hasMore = false, fromCache = false))),
        )
        val store = makeStore(repo)
        advanceUntilIdle()
        assertEquals(5, store.state.items.size)
        assertTrue(store.state.endReached)
    }

    @Test fun bootstrap_networkFailure_setsError_andClearsLoading() = runTest {
        val repo = FakeNotificationRepository(
            pages = mapOf(0 to Outcome.Failure(com.simplr.mykitta2.core.error.AppError.Network)),
        )
        val store = makeStore(repo)
        advanceUntilIdle()
        assertEquals(0, store.state.items.size)
        assertFalse(store.state.firstLoadInFlight)
        assertEquals("No internet connection", store.state.error)
    }

    @Test fun bootstrap_returnsFromCache_setsShowingCache() = runTest {
        val repo = FakeNotificationRepository(
            pages = mapOf(0 to Outcome.Success(NotificationPage(
                items = notifications(count = 3), hasMore = false, fromCache = true))),
        )
        val store = makeStore(repo)
        advanceUntilIdle()
        assertTrue(store.state.showingCache)
    }

    private fun makeStore(
        notificationRepository: NotificationRepository = FakeNotificationRepository(),
        principalRepository: PrincipalRepository = FakePrincipalRepository(),
    ): NotificationStore = NotificationStoreFactory(
        storeFactory = DefaultStoreFactory(),
        notificationRepository = notificationRepository,
        principalRepository = principalRepository,
    ).create()

    private fun notifications(count: Int, startId: Int = 1, type: NotificationType = NotificationType.ORDER) =
        (0 until count).map { i ->
            Notification(
                id = "N${startId + i}", title = "T", description = "D",
                type = type, payload = "{}", isRead = false,
                createdAt = "2026-05-${10 + (i % 20)}T00:00:00Z",
            )
        }

    class FakeNotificationRepository(
        initialCount: Int = 0,
        val pages: Map<Int, Outcome<NotificationPage>> = emptyMap(),
        val markReadResult: Outcome<Unit> = Outcome.Success(Unit),
    ) : NotificationRepository {
        private val _count = MutableStateFlow(initialCount)
        override val unreadCount: StateFlow<Int> = _count.asStateFlow()
        var markedRead = mutableListOf<String>()
            private set
        override suspend fun refreshCount(): Outcome<Int> = Outcome.Success(_count.value)
        override suspend fun loadPage(offset: Int): Outcome<NotificationPage> =
            pages[offset] ?: Outcome.Success(NotificationPage(emptyList(), hasMore = false))
        override suspend fun markAsRead(id: String): Outcome<Unit> {
            markedRead += id
            if (markReadResult is Outcome.Success) _count.value = (_count.value - 1).coerceAtLeast(0)
            return markReadResult
        }
    }

    class FakePrincipalRepository(
        val byId: Map<String, Principal> = emptyMap(),
    ) : PrincipalRepository {
        override fun observeAll(): Flow<List<Principal>> = emptyFlow()
        override suspend fun refresh(): Outcome<Unit> = Outcome.Success(Unit)
        override suspend fun findById(principalId: String): Principal? = byId[principalId]
    }
}
```

- [ ] **Step 2: Run — expect failures (loadPage stub does nothing)**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.simplr.mykitta2.feature.notification.NotificationStoreTest"`
Expected: 4 tests fail — state shows `firstLoadInFlight=true` forever; items empty.

- [ ] **Step 3: Implement `loadPage`**

In `NotificationStore.kt` replace the stub `loadPage` with:

```kotlin
private fun loadPage(offset: Int, isFirstLoad: Boolean) {
    if (isFirstLoad) dispatch(Message.FirstLoadStarted)
    else dispatch(Message.LoadingMoreStarted)
    scope.launch {
        when (val outcome = notificationRepository.loadPage(offset)) {
            is Outcome.Success -> {
                val page = outcome.value
                dispatch(Message.PageLoaded(
                    items = page.items,
                    nextOffset = offset + page.items.size,
                    endReached = !page.hasMore,
                    fromCache = page.fromCache,
                ))
            }
            is Outcome.Failure -> dispatch(Message.LoadFailed(ErrorMapper.message(outcome.error)))
            Outcome.Idle, Outcome.Loading -> Unit
        }
    }
}
```

- [ ] **Step 4: Run — expect pass**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.simplr.mykitta2.feature.notification.NotificationStoreTest"`
Expected: 4 tests pass.

NOTE: the failure test expects error string `"No internet connection"` — confirm that `ErrorMapper.message(AppError.Network)` actually returns that string in the codebase. If not, adjust the expected string in the test to match.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/notification/NotificationStore.kt \
        shared/src/commonTest/kotlin/com/simplr/mykitta2/feature/notification/NotificationStoreTest.kt
git commit -m "feat(notification): implement bootstrap loadPage + reducer"
```

---

## Task 17: Implement `LoadNextPage` intent + idempotency tests

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/notification/NotificationStore.kt`
- Modify: `shared/src/commonTest/kotlin/com/simplr/mykitta2/feature/notification/NotificationStoreTest.kt`

- [ ] **Step 1: Add failing tests**

```kotlin
@Test fun loadNextPage_success_appendsItems_andAdvancesOffset() = runTest {
    val repo = FakeNotificationRepository(pages = mapOf(
        0 to Outcome.Success(NotificationPage(notifications(count = 20, startId = 1), hasMore = true)),
        20 to Outcome.Success(NotificationPage(notifications(count = 20, startId = 21), hasMore = true)),
    ))
    val store = makeStore(repo)
    advanceUntilIdle()
    store.accept(NotificationStore.Intent.LoadNextPage)
    advanceUntilIdle()
    assertEquals(40, store.state.items.size)
    assertEquals(40, store.state.offset)
    assertFalse(store.state.loadingMore)
}

@Test fun loadNextPage_noop_whileLoadingMore() = runTest {
    val repo = FakeNotificationRepository(pages = mapOf(
        0 to Outcome.Success(NotificationPage(notifications(20), hasMore = true)),
        20 to Outcome.Success(NotificationPage(notifications(20, startId = 21), hasMore = true)),
    ))
    val store = makeStore(repo)
    advanceUntilIdle()
    // Fire twice quickly — second call must be a no-op
    store.accept(NotificationStore.Intent.LoadNextPage)
    store.accept(NotificationStore.Intent.LoadNextPage)
    advanceUntilIdle()
    assertEquals(40, store.state.items.size)  // not 60
}

@Test fun loadNextPage_noop_whenEndReached() = runTest {
    val repo = FakeNotificationRepository(pages = mapOf(
        0 to Outcome.Success(NotificationPage(notifications(count = 5), hasMore = false)),
    ))
    val store = makeStore(repo)
    advanceUntilIdle()
    assertTrue(store.state.endReached)
    store.accept(NotificationStore.Intent.LoadNextPage)
    advanceUntilIdle()
    assertEquals(5, store.state.items.size)   // unchanged
}

@Test fun loadNextPage_failure_setsError_retainsItems() = runTest {
    val repo = FakeNotificationRepository(pages = mapOf(
        0 to Outcome.Success(NotificationPage(notifications(20), hasMore = true)),
        20 to Outcome.Failure(com.simplr.mykitta2.core.error.AppError.Network),
    ))
    val store = makeStore(repo)
    advanceUntilIdle()
    store.accept(NotificationStore.Intent.LoadNextPage)
    advanceUntilIdle()
    assertEquals(20, store.state.items.size)  // page 1 retained
    assertFalse(store.state.loadingMore)
    assertTrue(store.state.error != null)
}
```

- [ ] **Step 2: Run — expect failures (`executeIntent` no-op for LoadNextPage)**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.simplr.mykitta2.feature.notification.NotificationStoreTest"`
Expected: 4 new failures.

- [ ] **Step 3: Wire `LoadNextPage` in `executeIntent`**

In `NotificationStore.kt` inside `ExecutorImpl.executeIntent`:

```kotlin
override fun executeIntent(intent: NotificationStore.Intent) {
    when (intent) {
        NotificationStore.Intent.LoadNextPage -> {
            if (state().loadingMore || state().endReached || state().firstLoadInFlight) return
            loadPage(offset = state().offset, isFirstLoad = false)
        }
        NotificationStore.Intent.Refresh -> Unit          // Task 19
        is NotificationStore.Intent.TapItem -> Unit       // Task 18
        NotificationStore.Intent.DismissError -> dispatch(Message.ErrorSet(null))
    }
}
```

- [ ] **Step 4: Run — expect pass**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.simplr.mykitta2.feature.notification.NotificationStoreTest"`
Expected: 8 tests pass.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/notification/NotificationStore.kt \
        shared/src/commonTest/kotlin/com/simplr/mykitta2/feature/notification/NotificationStoreTest.kt
git commit -m "feat(notification): wire LoadNextPage intent with idempotency"
```

---

## Task 18: Implement `TapItem` for all type branches (PRINCIPAL lookup, ORDER, UNKNOWN, mark-read fail)

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/notification/NotificationStore.kt`
- Modify: `shared/src/commonTest/kotlin/com/simplr/mykitta2/feature/notification/NotificationStoreTest.kt`

- [ ] **Step 1: Add failing tests**

```kotlin
@Test fun tapItem_PRINCIPAL_cacheHit_publishesNavigateLabel_andMarksRead() = runTest {
    val notif = Notification(
        id = "N1", title = "T", description = "D",
        type = NotificationType.PRINCIPAL,
        payload = """{"principalId":"P-1"}""",
        isRead = false, createdAt = "2026-05-26T00:00:00Z",
    )
    val notifRepo = FakeNotificationRepository(pages = mapOf(
        0 to Outcome.Success(NotificationPage(listOf(notif), hasMore = false))))
    val princRepo = FakePrincipalRepository(byId = mapOf(
        "P-1" to Principal(principalId = "P-1", principalName = "Acme",
            principalImg = "", isActive = true)))
    val store = makeStore(notifRepo, princRepo)
    advanceUntilIdle()
    val labels = mutableListOf<NotificationStore.Label>()
    val job = launch { store.labels.collect { labels += it } }
    store.accept(NotificationStore.Intent.TapItem(notif))
    advanceUntilIdle()
    assertEquals(NotificationStore.Label.NavigateToPrincipal("P-1", "Acme"), labels.single())
    assertEquals(listOf("N1"), notifRepo.markedRead)
    assertTrue(store.state.items.first().isRead)
    job.cancel()
}

@Test fun tapItem_PRINCIPAL_cacheMiss_publishesUnsupported_plusSnackbar() = runTest {
    val notif = Notification(
        id = "N1", title = "T", description = "D",
        type = NotificationType.PRINCIPAL,
        payload = """{"principalId":"P-MISSING"}""",
        isRead = false, createdAt = "2026-05-26T00:00:00Z",
    )
    val notifRepo = FakeNotificationRepository(pages = mapOf(
        0 to Outcome.Success(NotificationPage(listOf(notif), hasMore = false))))
    val princRepo = FakePrincipalRepository(byId = emptyMap())
    val store = makeStore(notifRepo, princRepo)
    advanceUntilIdle()
    val labels = mutableListOf<NotificationStore.Label>()
    val job = launch { store.labels.collect { labels += it } }
    store.accept(NotificationStore.Intent.TapItem(notif))
    advanceUntilIdle()
    assertTrue(labels.any { it is NotificationStore.Label.NavigateUnsupportedType })
    assertTrue(labels.any { it is NotificationStore.Label.ShowSnackbar &&
        (it as NotificationStore.Label.ShowSnackbar).text == "Brand not available" })
    job.cancel()
}

@Test fun tapItem_ORDER_publishesUnsupported_andMarksRead() = runTest {
    val notif = Notification(
        id = "N2", title = "T", description = "D",
        type = NotificationType.ORDER,
        payload = "{}", isRead = false, createdAt = "x",
    )
    val notifRepo = FakeNotificationRepository(pages = mapOf(
        0 to Outcome.Success(NotificationPage(listOf(notif), hasMore = false))))
    val store = makeStore(notifRepo)
    advanceUntilIdle()
    val labels = mutableListOf<NotificationStore.Label>()
    val job = launch { store.labels.collect { labels += it } }
    store.accept(NotificationStore.Intent.TapItem(notif))
    advanceUntilIdle()
    assertEquals(NotificationStore.Label.NavigateUnsupportedType, labels.single())
    assertEquals(listOf("N2"), notifRepo.markedRead)
    assertTrue(store.state.items.first().isRead)
    job.cancel()
}

@Test fun tapItem_markReadFails_stillPublishesNavLabel_andShowsSnackbar() = runTest {
    val notif = Notification(
        id = "N3", title = "T", description = "D",
        type = NotificationType.ORDER,
        payload = "{}", isRead = false, createdAt = "x",
    )
    val notifRepo = FakeNotificationRepository(
        pages = mapOf(0 to Outcome.Success(NotificationPage(listOf(notif), hasMore = false))),
        markReadResult = Outcome.Failure(com.simplr.mykitta2.core.error.AppError.Network),
    )
    val store = makeStore(notifRepo)
    advanceUntilIdle()
    val labels = mutableListOf<NotificationStore.Label>()
    val job = launch { store.labels.collect { labels += it } }
    store.accept(NotificationStore.Intent.TapItem(notif))
    advanceUntilIdle()
    assertTrue(labels.any { it is NotificationStore.Label.NavigateUnsupportedType })
    assertTrue(labels.any { it is NotificationStore.Label.ShowSnackbar })  // failure surfaced
    job.cancel()
}
```

Add to top of test file:

```kotlin
import kotlinx.coroutines.launch
```

- [ ] **Step 2: Run — expect 4 failures**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.simplr.mykitta2.feature.notification.NotificationStoreTest"`
Expected: 4 new tests fail (TapItem still a no-op).

- [ ] **Step 3: Implement `TapItem` handler**

Add a companion-object `Json` parser and a private helper in `NotificationStoreFactory`. Update `executeIntent`:

```kotlin
private val json = Json { ignoreUnknownKeys = true }

private fun handleTap(notification: Notification) {
    scope.launch {
        // 1. mark-read (fail-open)
        when (val markOutcome = notificationRepository.markAsRead(notification.id)) {
            is Outcome.Success -> dispatch(Message.MarkedRead(notification.id))
            is Outcome.Failure -> {
                dispatch(Message.MarkedRead(notification.id))  // optimistic UI even on failure (fail-open)
                publish(NotificationStore.Label.ShowSnackbar(ErrorMapper.message(markOutcome.error)))
            }
            Outcome.Idle, Outcome.Loading -> Unit
        }
        // 2. type-routed nav
        when (notification.type) {
            NotificationType.PRINCIPAL -> {
                val principalId = parsePrincipalId(notification.payload)
                val cached = principalId?.let { principalRepository.findById(it) }
                if (principalId != null && cached != null) {
                    publish(NotificationStore.Label.NavigateToPrincipal(
                        principalId = principalId,
                        principalName = cached.principalName,
                    ))
                } else {
                    publish(NotificationStore.Label.NavigateUnsupportedType)
                    publish(NotificationStore.Label.ShowSnackbar("Brand not available"))
                }
            }
            NotificationType.ORDER, NotificationType.UNKNOWN ->
                publish(NotificationStore.Label.NavigateUnsupportedType)
        }
    }
}

private fun parsePrincipalId(payload: String): String? = try {
    json.parseToJsonElement(payload).jsonObject["principalId"]?.jsonPrimitive?.content
} catch (t: Throwable) {
    null
}
```

Wire it into `executeIntent`:

```kotlin
is NotificationStore.Intent.TapItem -> handleTap(intent.notification)
```

NOTE on test ordering: the PRINCIPAL-cache-hit test asserts `labels.single() == NavigateToPrincipal(...)`. Our impl publishes mark-read snackbar ONLY on failure, so success-path emits exactly one label (NavigateToPrincipal). For the cache-miss test we publish two labels (NavigateUnsupportedType + ShowSnackbar) — test uses `assertTrue(labels.any { ... })` to be order-independent.

- [ ] **Step 4: Run — expect pass**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.simplr.mykitta2.feature.notification.NotificationStoreTest"`
Expected: 12 tests pass.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/notification/NotificationStore.kt \
        shared/src/commonTest/kotlin/com/simplr/mykitta2/feature/notification/NotificationStoreTest.kt
git commit -m "feat(notification): type-routed TapItem with PrincipalRepository lookup"
```

---

## Task 19: Implement `Refresh` intent (resets state, reloads page 0)

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/notification/NotificationStore.kt`
- Modify: `shared/src/commonTest/kotlin/com/simplr/mykitta2/feature/notification/NotificationStoreTest.kt`

- [ ] **Step 1: Add failing test**

```kotlin
@Test fun refresh_resetsState_andReloadsFirstPage() = runTest {
    val repo = FakeNotificationRepository(pages = mapOf(
        0 to Outcome.Success(NotificationPage(notifications(count = 20), hasMore = true)),
    ))
    val store = makeStore(repo)
    advanceUntilIdle()
    assertEquals(20, store.state.items.size)
    // Swap repo's page 0 to a different set of items
    val newRepo = FakeNotificationRepository(pages = mapOf(
        0 to Outcome.Success(NotificationPage(notifications(count = 5, startId = 100), hasMore = false)),
    ))
    val store2 = makeStore(newRepo)
    advanceUntilIdle()
    store2.accept(NotificationStore.Intent.Refresh)
    advanceUntilIdle()
    assertEquals(5, store2.state.items.size)
    assertTrue(store2.state.endReached)
    assertEquals(0, store2.state.offset)
}
```

NOTE: this test as written doesn't really exercise refresh because the second store boots with the new repo from scratch. Adjust by making the fake repo's `pages` mutable so Refresh can return a *new* response on the second call. Replace `FakeNotificationRepository` with:

```kotlin
class FakeNotificationRepository(
    initialCount: Int = 0,
    var pages: Map<Int, Outcome<NotificationPage>> = emptyMap(),
    val markReadResult: Outcome<Unit> = Outcome.Success(Unit),
) : NotificationRepository {
    // ... unchanged ...
}
```

Then the test becomes:

```kotlin
@Test fun refresh_resetsState_andReloadsFirstPage() = runTest {
    val repo = FakeNotificationRepository(pages = mapOf(
        0 to Outcome.Success(NotificationPage(notifications(count = 20), hasMore = true)),
    ))
    val store = makeStore(repo)
    advanceUntilIdle()
    assertEquals(20, store.state.items.size)
    // Repository now returns a shorter page on next call
    repo.pages = mapOf(
        0 to Outcome.Success(NotificationPage(notifications(count = 5, startId = 100), hasMore = false)),
    )
    store.accept(NotificationStore.Intent.Refresh)
    advanceUntilIdle()
    assertEquals(5, store.state.items.size)
    assertTrue(store.state.endReached)
    assertEquals(5, store.state.offset)   // size of new page
}
```

- [ ] **Step 2: Run — expect failure**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.simplr.mykitta2.feature.notification.NotificationStoreTest"`
Expected: 1 new failure (Refresh still a no-op).

- [ ] **Step 3: Implement `Refresh`**

In `NotificationStore.kt` `executeIntent`:

```kotlin
NotificationStore.Intent.Refresh -> {
    dispatch(Message.Reset)
    loadPage(offset = 0, isFirstLoad = true)
}
```

- [ ] **Step 4: Run — expect pass**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.simplr.mykitta2.feature.notification.NotificationStoreTest"`
Expected: 13 tests pass.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/notification/NotificationStore.kt \
        shared/src/commonTest/kotlin/com/simplr/mykitta2/feature/notification/NotificationStoreTest.kt
git commit -m "feat(notification): wire Refresh intent (state reset + reload)"
```

---

## Task 20: Register `featureNotificationModule` in Koin

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/simplr/mykitta2/di/AppModule.kt`

- [ ] **Step 1: Add the module**

In `AppModule.kt` add (after `featureProfileModule`):

```kotlin
val featureNotificationModule = module {
    factory {
        NotificationStoreFactory(
            storeFactory = get(),
            notificationRepository = get(),
            principalRepository = get(),
        )
    }
    viewModelOf(::NotificationViewModel)
}
```

Add imports:

```kotlin
import com.simplr.mykitta2.feature.notification.NotificationStoreFactory
import com.simplr.mykitta2.feature.notification.NotificationViewModel
```

Add `featureNotificationModule` to `commonModules()`:

```kotlin
fun commonModules(): List<Module> = listOf(
    coreModule, prefsModule, databaseModule, networkModule,
    repositoryModule, featureAuthModule, featureHomeModule,
    featurePrincipalModule, featureProfileModule,
    featureNotificationModule,       // NEW
    featureSplashModule,
)
```

- [ ] **Step 2: Compile**

Run: `./gradlew :shared:compileCommonMainKotlinMetadata`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/simplr/mykitta2/di/AppModule.kt
git commit -m "feat(di): register featureNotificationModule"
```

---

## Task 21: Add `PendingNavStore` + Koin binding + small unit test

**Files:**
- Create: `shared/src/commonMain/kotlin/com/simplr/mykitta2/ui/nav/PendingNavStore.kt`
- Create: `shared/src/commonTest/kotlin/com/simplr/mykitta2/ui/nav/PendingNavStoreTest.kt`
- Modify: `shared/src/commonMain/kotlin/com/simplr/mykitta2/di/AppModule.kt`

- [ ] **Step 1: Write failing test**

```kotlin
package com.simplr.mykitta2.ui.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PendingNavStoreTest {

    @Test fun requestPrincipalCatalog_thenConsume_clearsValue() {
        val store = PendingNavStore()
        assertNull(store.pendingPrincipal.value)

        store.requestPrincipalCatalog(id = "P-1", name = "Acme")
        assertEquals(PendingNavStore.PendingPrincipal("P-1", "Acme"), store.pendingPrincipal.value)

        store.consume()
        assertNull(store.pendingPrincipal.value)
    }

    @Test fun secondRequest_overwritesFirst_ifNotConsumed() {
        val store = PendingNavStore()
        store.requestPrincipalCatalog("P-1", "A")
        store.requestPrincipalCatalog("P-2", "B")
        assertEquals(PendingNavStore.PendingPrincipal("P-2", "B"), store.pendingPrincipal.value)
    }
}
```

- [ ] **Step 2: Run — expect failure (class doesn't exist)**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.simplr.mykitta2.ui.nav.PendingNavStoreTest"`
Expected: FAIL — `PendingNavStore` unresolved.

- [ ] **Step 3: Create the class**

```kotlin
package com.simplr.mykitta2.ui.nav

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One-shot channel for cross-NavController deep-links. `NotificationScreen`
 * lives on the outer NavController; `MainTab.PrincipalCatalog` lives on the
 * inner one (owned by MainShell). The notification screen writes here,
 * MainShell observes + consumes once. Reusable for Phase 2 push deep-links.
 */
class PendingNavStore {
    private val _pendingPrincipal = MutableStateFlow<PendingPrincipal?>(null)
    val pendingPrincipal: StateFlow<PendingPrincipal?> = _pendingPrincipal.asStateFlow()

    fun requestPrincipalCatalog(id: String, name: String) {
        _pendingPrincipal.value = PendingPrincipal(id, name)
    }

    fun consume() {
        _pendingPrincipal.value = null
    }

    data class PendingPrincipal(val principalId: String, val principalName: String)
}
```

- [ ] **Step 4: Run — expect pass**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.simplr.mykitta2.ui.nav.PendingNavStoreTest"`
Expected: 2 tests pass.

- [ ] **Step 5: Register in Koin**

In `AppModule.kt`'s `featureNotificationModule`:

```kotlin
val featureNotificationModule = module {
    single { PendingNavStore() }
    factory {
        NotificationStoreFactory(
            storeFactory = get(),
            notificationRepository = get(),
            principalRepository = get(),
        )
    }
    viewModelOf(::NotificationViewModel)
}
```

Add the import:

```kotlin
import com.simplr.mykitta2.ui.nav.PendingNavStore
```

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/simplr/mykitta2/ui/nav/PendingNavStore.kt \
        shared/src/commonTest/kotlin/com/simplr/mykitta2/ui/nav/PendingNavStoreTest.kt \
        shared/src/commonMain/kotlin/com/simplr/mykitta2/di/AppModule.kt
git commit -m "feat(nav): add PendingNavStore for cross-NavController deep-links"
```

---

## Task 22: Add `Destination.Notifications` + register composable in `AppNavHost`

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/simplr/mykitta2/ui/nav/Destination.kt`
- Modify: `shared/src/commonMain/kotlin/com/simplr/mykitta2/ui/nav/AppNavHost.kt`

Builds a destination that opens a stub `NotificationScreen` (added in Task 23). The route exists so we can navigate to it from `MainShell`.

- [ ] **Step 1: Add destination**

In `Destination.kt` add (inside the sealed interface):

```kotlin
@Serializable
data object Notifications : Destination
```

- [ ] **Step 2: Wire callback in `AppNavHost`**

In `AppNavHost.kt`:
- Inside the existing `composable<Destination.Home>` block, add `onOpenNotifications` to the `MainShell(...)` call:

```kotlin
composable<Destination.Home> {
    MainShell(
        onOpenSearch = { navController.navigate(Destination.Search) },
        onOpenProfileDetail = { navController.navigate(Destination.ProfileDetail) },
        onOpenNotifications = { navController.navigate(Destination.Notifications) },
        onLogout = {
            navController.navigate(Destination.LoginOtp) {
                popUpTo(Destination.Home) { inclusive = true }
            }
        },
    )
}
```

- Add a new composable block (after `Destination.ProfileDetail`):

```kotlin
composable<Destination.Notifications> {
    val pendingNavStore: PendingNavStore = koinInject()
    NotificationScreen(
        onBack = { navController.popBackStack() },
        onOpenPrincipal = { principalId, principalName ->
            pendingNavStore.requestPrincipalCatalog(principalId, principalName)
            navController.popBackStack()
        },
    )
}
```

Add imports:

```kotlin
import com.simplr.mykitta2.feature.notification.NotificationScreen
import com.simplr.mykitta2.ui.nav.PendingNavStore
import org.koin.compose.koinInject
```

- [ ] **Step 3: Compile (note: `NotificationScreen` doesn't exist yet — Step 4 stubs it)**

Run: `./gradlew :shared:compileCommonMainKotlinMetadata`
Expected: FAIL — `NotificationScreen` unresolved.

- [ ] **Step 4: Stub `NotificationScreen` so this commit compiles**

Create `shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/notification/NotificationScreen.kt` with a minimal stub (full UI lands in Task 23):

```kotlin
package com.simplr.mykitta2.feature.notification

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.simplr.mykitta2.ui.common.MyKittaScaffold

@Composable
fun NotificationScreen(
    onBack: () -> Unit,
    onOpenPrincipal: (principalId: String, principalName: String) -> Unit,
) {
    MyKittaScaffold(title = "Notifications", onBack = onBack) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Text("Stub — UI lands in Task 23",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
```

Also update `MainShell.kt` to accept the new parameter (full hoisting comes in Task 24; for now just pass through):

```kotlin
@Composable
fun MainShell(
    onOpenSearch: () -> Unit = {},
    onOpenProfileDetail: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},     // NEW
    onLogout: () -> Unit = {},
) {
    // ... existing body ...
    HomeScreen(
        onOpenCart = { /* later */ },
        onOpenChat = { /* later */ },
        onOpenNotifications = onOpenNotifications,    // wired through (was no-op)
        onOpenRewards = { tabNavController.switchTab(MainTab.Rewards) },
        onOpenSearch = onOpenSearch,
    )
    // ...
}
```

- [ ] **Step 5: Compile + run app**

Run: `./gradlew :androidApp:assembleDevDebug`
Expected: `BUILD SUCCESSFUL`. Install and tap 🔔 → stub screen opens, back works.

Run: `./gradlew :shared:testAndroidHostTest`
Expected: all existing tests still pass.

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/simplr/mykitta2/ui/nav/Destination.kt \
        shared/src/commonMain/kotlin/com/simplr/mykitta2/ui/nav/AppNavHost.kt \
        shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/notification/NotificationScreen.kt \
        shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/main/MainShell.kt
git commit -m "feat(nav): add Notifications destination + stub screen + wire callback"
```

---

## Task 23: Build full `NotificationScreen` UI (list + states + infinite-scroll + label observer)

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/notification/NotificationScreen.kt`

No tests — Compose UI tests aren't in scope. Manual verification at end.

- [ ] **Step 1: Replace the stub with the full screen**

```kotlin
package com.simplr.mykitta2.feature.notification

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplr.mykitta2.domain.Notification
import com.simplr.mykitta2.ui.common.MyKittaScaffold
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun NotificationScreen(
    onBack: () -> Unit,
    onOpenPrincipal: (principalId: String, principalName: String) -> Unit,
    viewModel: NotificationViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Label observer — type-routed nav + snackbars.
    LaunchedEffect(viewModel) {
        viewModel.labels.collect { label ->
            when (label) {
                is NotificationStore.Label.NavigateToPrincipal ->
                    onOpenPrincipal(label.principalId, label.principalName)
                NotificationStore.Label.NavigateUnsupportedType ->
                    scope.launch { snackbarHostState.showSnackbar("Coming soon") }
                is NotificationStore.Label.ShowSnackbar ->
                    scope.launch { snackbarHostState.showSnackbar(label.text) }
            }
        }
    }

    // State.error → snackbar (same pattern as HomeScreen.kt:92-94).
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it) }
    }

    // Infinite-scroll trigger.
    LaunchedEffect(listState, state.endReached, state.items.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { lastVisible ->
                val items = state.items
                if (!state.endReached && !state.loadingMore && items.isNotEmpty()
                    && lastVisible >= items.lastIndex - 3) {
                    viewModel.accept(NotificationStore.Intent.LoadNextPage)
                }
            }
    }

    MyKittaScaffold(
        title = "Notifications",
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.firstLoadInFlight -> FullScreenLoader(padding)
            state.items.isEmpty() -> EmptyState(padding)
            else -> NotificationList(
                state = state,
                listState = listState,
                padding = padding,
                onTap = { viewModel.accept(NotificationStore.Intent.TapItem(it)) },
            )
        }
    }
}

@Composable
private fun NotificationList(
    state: NotificationStore.State,
    listState: LazyListState,
    padding: PaddingValues,
    onTap: (Notification) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(padding),
    ) {
        if (state.showingCache) {
            item("cache-banner") { OfflineCacheBanner() }
        }
        items(state.items, key = { it.id }) { notification ->
            NotificationRow(notification = notification, onClick = { onTap(notification) })
        }
        if (state.loadingMore) {
            item("loading-more") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
        if (state.endReached && state.items.isNotEmpty()) {
            item("end") {
                Text(
                    text = "You're all caught up",
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NotificationRow(
    notification: Notification,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = if (notification.isRead) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (notification.isRead) Color.Transparent
                                else MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                    ),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = notification.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (notification.isRead) FontWeight.Normal else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = notification.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = notification.createdAt,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun OfflineCacheBanner() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("📡", fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Showing cached notifications",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@Composable
private fun EmptyState(padding: PaddingValues) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("🔔", fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "No notifications yet",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "When you have notifications, they'll show up here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FullScreenLoader(padding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.Center,
    ) { CircularProgressIndicator() }
}
```

- [ ] **Step 2: Build + manual check**

Run: `./gradlew :androidApp:assembleDevDebug`
Expected: `BUILD SUCCESSFUL`.

Install + open the app → tap 🔔 → list should appear (or empty state). Scroll-load + tap behavior live.

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/notification/NotificationScreen.kt
git commit -m "feat(notification): build full NotificationScreen UI"
```

---

## Task 24: Hoist `HomeViewModel` to `MainShell` + wire Home-tab onClick to `RefreshNotifications` + first-render guard

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/main/MainShell.kt`
- Modify: `shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/home/HomeScreen.kt`

The refactor: `HomeViewModel` was created inside `HomeScreen` via `koinViewModel()`. We need the same instance accessible from `MainShell` so the bottom-bar can dispatch `RefreshNotifications`. Easiest correct approach: pass state + accept callback down from `MainShell` to `HomeScreen`.

- [ ] **Step 1: Update `MainShell` to own the `HomeViewModel`**

```kotlin
@Composable
fun MainShell(
    onOpenSearch: () -> Unit = {},
    onOpenProfileDetail: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onLogout: () -> Unit = {},
) {
    val tabNavController = rememberNavController()
    val currentDest = tabNavController.currentBackStackEntryAsState().value?.destination
    val uriHandler = LocalUriHandler.current
    val homeViewModel: HomeViewModel = koinViewModel()
    val hasFiredInitialRefresh = rememberSaveable { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            MainBottomBar(
                currentDest = currentDest,
                navController = tabNavController,
                onHomeSelected = { wasAlreadyOnHome ->
                    if (hasFiredInitialRefresh.value && !wasAlreadyOnHome) {
                        homeViewModel.accept(HomeStore.Intent.RefreshNotifications)
                    }
                    hasFiredInitialRefresh.value = true
                },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        NavHost(
            navController = tabNavController,
            startDestination = MainTab.Home,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            composable<MainTab.Home> {
                HomeScreen(
                    viewModel = homeViewModel,                    // pass the hoisted VM
                    onOpenCart = { /* later */ },
                    onOpenChat = { /* later */ },
                    onOpenNotifications = onOpenNotifications,
                    onOpenRewards = { tabNavController.switchTab(MainTab.Rewards) },
                    onOpenSearch = onOpenSearch,
                )
            }
            composable<MainTab.Principal> {
                PrincipalScreen(
                    onOpenCatalog = { principal ->
                        tabNavController.navigate(
                            MainTab.PrincipalCatalog(
                                principalId = principal.principalId,
                                principalName = principal.principalName,
                            )
                        )
                    },
                )
            }
            composable<MainTab.PrincipalCatalog> { backStackEntry ->
                val route = backStackEntry.toRoute<MainTab.PrincipalCatalog>()
                PrincipalCatalogStub(
                    principalName = route.principalName,
                    onBack = { tabNavController.popBackStack() },
                )
            }
            composable<MainTab.Rewards> { TabStub("Rewards") }
            composable<MainTab.Profile> {
                ProfileScreen(
                    onMenuClick = { id ->
                        when (id) {
                            "profile" -> onOpenProfileDetail()
                            "about" -> uriHandler.openUri("https://www.youtube.com/watch?v=phrPUil2_7E")
                        }
                    },
                    onLogout = onLogout,
                )
            }
        }
    }
}
```

Add imports if missing:

```kotlin
import com.simplr.mykitta2.feature.home.HomeStore
import com.simplr.mykitta2.feature.home.HomeViewModel
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import org.koin.compose.viewmodel.koinViewModel
```

- [ ] **Step 2: Update `MainBottomBar` signature**

In the same file, change `MainBottomBar`:

```kotlin
@Composable
private fun MainBottomBar(
    currentDest: NavDestination?,
    navController: NavController,
    onHomeSelected: (wasAlreadyOnHome: Boolean) -> Unit,
) {
    NavigationBar {
        NavigationBarItem(
            selected = currentDest?.hasRoute<MainTab.Home>() == true,
            onClick = {
                val wasAlreadyOnHome = currentDest?.hasRoute<MainTab.Home>() == true
                navController.switchTab(MainTab.Home)
                onHomeSelected(wasAlreadyOnHome)
            },
            icon = { Text("🏠", fontSize = 18.sp) },
            label = { Text("Home") },
        )
        NavigationBarItem(
            selected = currentDest?.hasRoute<MainTab.Principal>() == true,
            onClick = { navController.switchTab(MainTab.Principal) },
            icon = { Text("🏷️", fontSize = 18.sp) },
            label = { Text("Principal") },
        )
        NavigationBarItem(
            selected = currentDest?.hasRoute<MainTab.Rewards>() == true,
            onClick = { navController.switchTab(MainTab.Rewards) },
            icon = { Text("🎁", fontSize = 18.sp) },
            label = { Text("Rewards") },
        )
        NavigationBarItem(
            selected = currentDest?.hasRoute<MainTab.Profile>() == true,
            onClick = { navController.switchTab(MainTab.Profile) },
            icon = { Text("👤", fontSize = 18.sp) },
            label = { Text("My Profile") },
        )
    }
}
```

- [ ] **Step 3: Update `HomeScreen` to accept an injected ViewModel**

In `HomeScreen.kt`:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = koinViewModel(),    // moved to top of signature
    onOpenCart: () -> Unit = {},
    onOpenChat: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenRewards: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // ... rest unchanged ...
}
```

- [ ] **Step 4: Compile + run app**

Run: `./gradlew :androidApp:assembleDevDebug`
Expected: `BUILD SUCCESSFUL`.

Install. Switch away from Home → back to Home → confirm Chucker shows a `GetNotificationCount` call fires.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/main/MainShell.kt \
        shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/home/HomeScreen.kt
git commit -m "feat(home): hoist HomeViewModel; refresh notif count on Home tab-switch"
```

---

## Task 25: Wire `PendingNavStore` consumer in `MainShell`

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/main/MainShell.kt`

- [ ] **Step 1: Add the consumer**

Inside `MainShell` (after `val homeViewModel: HomeViewModel = koinViewModel()`):

```kotlin
val pendingNavStore: PendingNavStore = koinInject()
val pending by pendingNavStore.pendingPrincipal.collectAsState()

LaunchedEffect(pending) {
    val target = pending ?: return@LaunchedEffect
    tabNavController.switchTab(MainTab.Principal)
    tabNavController.navigate(MainTab.PrincipalCatalog(
        principalId = target.principalId,
        principalName = target.principalName,
    ))
    pendingNavStore.consume()
}
```

Add imports:

```kotlin
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import com.simplr.mykitta2.ui.nav.PendingNavStore
import org.koin.compose.koinInject
```

- [ ] **Step 2: Build + manual test**

Run: `./gradlew :androidApp:assembleDevDebug`
Expected: `BUILD SUCCESSFUL`.

Install and verify: from `NotificationScreen`, tap a `PRINCIPAL` notification → screen pops → bottom-nav switches to Principal tab → `PrincipalCatalog` screen is on top.

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/main/MainShell.kt
git commit -m "feat(nav): consume PendingNavStore in MainShell for deep-link"
```

---

## Task 26: Final verification — full test suite + manual checklist

**Files:** none

- [ ] **Step 1: Run all tests**

Run: `./gradlew :shared:allTests`
Expected: `BUILD SUCCESSFUL`. All tests green: `NotificationRepositoryTest` (12), `NotificationStoreTest` (13), `NotificationDtosTest` (3), `NotificationTypeTest` (2), `PrincipalRepositoryFindByIdTest` (2), `PendingNavStoreTest` (2), `MyKittaDatabaseWiperTest` (1), modified `HomeStoreTest` cases (3 new), and all previously-passing suites unchanged.

- [ ] **Step 2: Android emulator verification (devDebug)**

Run: `./gradlew :androidApp:installDevDebug`
Launch on a Pixel emulator. Walk through the checklist:

- [ ] Cold launch → Home shows badge with unread count.
- [ ] Tap 🔔 → `NotificationScreen` opens; first 20 rows render; full-screen spinner disappears.
- [ ] Scroll to bottom → bottom spinner appears → next page appends.
- [ ] Reach end of list → "You're all caught up" footer; no further fetches in Chucker.
- [ ] Tap a `Principal` row → screen pops; bottom-nav switches to Principal; `PrincipalCatalog` screen on top; row is now visually marked read; Home badge decremented by 1.
- [ ] Tap an `Order` row → "Coming soon" snackbar; row marked read; Home badge decremented by 1.
- [ ] Enable airplane mode + tap 🔔 with cached data → cached list + "Showing cached notifications" banner visible.
- [ ] Airplane mode + empty cache (fresh install) → error snackbar; empty state.
- [ ] Mark-read while offline → snackbar; row stays unread (cache update silently happened because optimistic dispatch went through). Actually re-check: if API fails, `_unreadCount` is NOT decremented. Confirm badge does NOT change in offline + fail case.
- [ ] Logout → log back in → `NotificationScreen` shows empty state on first open.
- [ ] Switch from Principal tab → Home → confirm `GetNotificationCount` fires (Chucker).

- [ ] **Step 3: iOS Simulator verification (Debug)**

Open `iosApp/iosApp.xcodeproj` in Xcode. Run on an iOS 16 simulator.

Walk through the same checklist (Chucker not available — use Proxyman or rely on visible behavior + cache state across re-opens).

- [ ] **Step 4: Tag the implementation complete**

Run: `git log --oneline 880c443..HEAD`
Expected: ~22-25 commits (one per task, plus the spec).

No final commit needed unless there are last-minute fixes from manual verification. If fixes are needed, commit them as `fix(notification): <whatever>` and re-run the checklist before declaring done.

---

## Spec coverage check

| Spec section | Implementing task(s) |
|---|---|
| Domain model (`Notification`, `NotificationType`) | Task 3 |
| DTOs (`NotificationListServerResponse`, `MarkNotificationReadRequest/Response`) | Task 4 |
| `CatalogApi` additions + `callPath` helper | Task 6 |
| SQLDelight `Notification.sq` | Task 5 |
| `NotificationRepository` (interface, impl, count flow, paging, mark-read, offline fallback, ignore-server-hasMoreRecords) | Tasks 7–11 |
| `LocalDataWiper` extension + transaction | Task 13 |
| `HomeStore` swap to `NotificationRepository.unreadCount` + `RefreshNotifications` intent | Task 14 |
| `NotificationStore`/`Factory`/`ViewModel` (bootstrap, `LoadNextPage`, `TapItem` type-routing with `PrincipalRepository.findById`, `Refresh`) | Tasks 15–19 |
| Koin `featureNotificationModule` + `PendingNavStore` | Tasks 20, 21 |
| `Destination.Notifications` + `AppNavHost` composable | Task 22 |
| `NotificationScreen` UI (list, row, infinite scroll, empty/loading/cache banner, label observer) | Task 23 |
| `MainShell` hoisting + tab-switch refresh + first-render guard | Task 24 |
| `MainShell` PendingNavStore consumer (cross-NavController deep-link) | Task 25 |
| `PrincipalRepository.findById` (spec gap surfaced during planning) | Task 2 |
| `MyKittaScaffold.snackbarHost` slot (spec open issue) | Task 1 |
| Manual verification checklist | Task 26 |

All spec sections have at least one implementing task. Manual `createdAt` verification is Task 0; if it fails the spec needs amending before Task 5.
