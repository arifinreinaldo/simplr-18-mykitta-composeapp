# Chat Feature — Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire the no-op chat icon on `HomeScreen` to a real Chat List screen + principal-picker screen, with SQLDelight-cached freshness and snackbar stubs for tap actions targeting the not-yet-built thread screen.

**Architecture:** Two MVI screens (`ChatListScreen`, `ChatContactScreen`) backed by one new repository (`ChatRepository`) + one new SQLDelight table (`ChatList.sq`). The picker reuses `PrincipalRepository.observeAll()` — no new principal data path. Top-level nav destinations sibling to `Search` / `ProfileDetail`. Mirrors the History/Notification Phase 1 pattern.

**Tech Stack:** Kotlin Multiplatform · Compose Multiplatform · MVIKotlin · SQLDelight 2.x · Koin · Ktor · kotlinx-serialization

**Spec:** `docs/superpowers/specs/2026-05-26-chat-list-feature-design.md`

---

## File map

**New files:**
- `shared/src/commonMain/sqldelight/com/simplr/mykitta2/shared/db/ChatList.sq`
- `shared/src/commonMain/kotlin/com/simplr/mykitta2/data/net/dto/ChatDtos.kt`
- `shared/src/commonMain/kotlin/com/simplr/mykitta2/domain/ChatSummary.kt`
- `shared/src/commonMain/kotlin/com/simplr/mykitta2/data/prefs/ChatListCacheStore.kt`
- `shared/src/commonMain/kotlin/com/simplr/mykitta2/data/repo/ChatRepository.kt`
- `shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/chat/list/ChatListStore.kt`
- `shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/chat/list/ChatListViewModel.kt`
- `shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/chat/list/ChatListScreen.kt`
- `shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/chat/contact/ChatContactStore.kt`
- `shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/chat/contact/ChatContactViewModel.kt`
- `shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/chat/contact/ChatContactScreen.kt`
- `shared/src/androidHostTest/kotlin/com/simplr/mykitta2/data/repo/ChatRepositoryTest.kt`
- `shared/src/commonTest/kotlin/com/simplr/mykitta2/feature/chat/list/ChatListStoreTest.kt`
- `shared/src/commonTest/kotlin/com/simplr/mykitta2/feature/chat/contact/ChatContactStoreTest.kt`

**Modified files:**
- `shared/src/commonMain/kotlin/com/simplr/mykitta2/data/net/api/CatalogApi.kt` — add `getChatList`
- `shared/src/commonMain/kotlin/com/simplr/mykitta2/data/repo/LocalDataWiper.kt` — wipe `ChatList`
- `shared/src/androidHostTest/kotlin/com/simplr/mykitta2/data/repo/MyKittaDatabaseWiperTest.kt` — assert `ChatList` cleared
- `shared/src/commonMain/kotlin/com/simplr/mykitta2/ui/nav/Destination.kt` — add `ChatList` + `ChatContact`
- `shared/src/commonMain/kotlin/com/simplr/mykitta2/ui/nav/AppNavHost.kt` — register chat composables
- `shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/main/MainShell.kt:117` — wire `onOpenChat`
- `shared/src/commonMain/kotlin/com/simplr/mykitta2/di/AppModule.kt` — add `featureChatModule`

---

## Section A — Data foundation

### Task 1: Add `ChatList.sq` schema

**Files:**
- Create: `shared/src/commonMain/sqldelight/com/simplr/mykitta2/shared/db/ChatList.sq`

- [ ] **Step 1: Create the schema file**

```sql
-- User-scoped chat list cache. Refreshed from `GetChatList` on screen open
-- when stale (TTL = 5 min) and on pull-to-refresh.
-- Wiped on logout via [com.simplr.mykitta2.data.repo.MyKittaDatabaseWiper].
CREATE TABLE IF NOT EXISTS ChatList (
    principalId   TEXT NOT NULL PRIMARY KEY,
    principalName TEXT NOT NULL,
    lastMessage   TEXT NOT NULL,
    lastMessageAt INTEGER NOT NULL,
    isUnread      INTEGER NOT NULL,
    imagePath     TEXT
);

selectAll:
SELECT * FROM ChatList ORDER BY lastMessageAt DESC;

upsert:
INSERT OR REPLACE INTO ChatList(principalId, principalName, lastMessage, lastMessageAt, isUnread, imagePath)
VALUES (?, ?, ?, ?, ?, ?);

clearAll:
DELETE FROM ChatList;

deleteAll:
DELETE FROM ChatList;
```

- [ ] **Step 2: Regenerate SQLDelight interfaces**

Run: `./gradlew :shared:generateSqlDelightInterface`
Expected: build success, generates `ChatListQueries` reachable as `db.chatListQueries`.

- [ ] **Step 3: Sanity-check compilation**

Run: `./gradlew :shared:compileKotlinAndroid`
Expected: BUILD SUCCESSFUL — schema parses, generated code compiles.

- [ ] **Step 4: Commit**

```bash
git add shared/src/commonMain/sqldelight/com/simplr/mykitta2/shared/db/ChatList.sq
git commit -m "feat(chat): add ChatList SQLDelight schema"
```

---

### Task 2: Add Chat DTOs

**Files:**
- Create: `shared/src/commonMain/kotlin/com/simplr/mykitta2/data/net/dto/ChatDtos.kt`

- [ ] **Step 1: Create the DTO file**

```kotlin
package com.simplr.mykitta2.data.net.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire model for a single row in `GetChatList`'s objectData.
 *
 * Field casing is best-effort from the legacy [ChatEntity]; the live response
 * is verified against this DTO in the final task of this plan and adjusted if
 * the server uses different keys.
 */
@Serializable
data class ChatListItemDto(
    @SerialName("PrincipalID") val principalId: String,
    @SerialName("PrincipalName") val principalName: String? = null,
    @SerialName("LastMessage") val lastMessage: String? = null,
    @SerialName("LastMessageTime") val lastMessageTime: String? = null,
    @SerialName("Status") val status: String? = null,
    @SerialName("MessageType") val messageType: String? = null,
    @SerialName("ImagePath") val imagePath: String? = null,
)

@Serializable
data class ChatListServerResponse(
    @SerialName("getObjectResult") val getObjectResult: GetObjectResult<ChatListItemDto>,
) {
    /** Flattens the legacy `objectData: List<List<T>>` envelope. */
    fun items(): List<ChatListItemDto> =
        getObjectResult.objectData.firstOrNull().orEmpty()
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :shared:compileKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/simplr/mykitta2/data/net/dto/ChatDtos.kt
git commit -m "feat(chat): add ChatListItemDto + ServerResponse envelope"
```

---

### Task 3: Add `domain/ChatSummary.kt`

**Files:**
- Create: `shared/src/commonMain/kotlin/com/simplr/mykitta2/domain/ChatSummary.kt`

- [ ] **Step 1: Create the domain type**

```kotlin
package com.simplr.mykitta2.domain

/**
 * UI-facing chat list row. Built from [com.simplr.mykitta2.data.net.dto.ChatListItemDto]
 * by the repository, persisted as a row in the `ChatList` SQLDelight table.
 */
data class ChatSummary(
    val principalId: String,
    val principalName: String,
    val lastMessage: String,
    val lastMessageAt: Long,
    val isUnread: Boolean,
    val imagePath: String?,
)
```

- [ ] **Step 2: Compile**

Run: `./gradlew :shared:compileKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/simplr/mykitta2/domain/ChatSummary.kt
git commit -m "feat(chat): add ChatSummary domain type"
```

---

### Task 4: Extend `CatalogApi` with `getChatList`

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/simplr/mykitta2/data/net/api/CatalogApi.kt`

- [ ] **Step 1: Add import for the new response type**

In the import block at the top of `CatalogApi.kt`, add:

```kotlin
import com.simplr.mykitta2.data.net.dto.ChatListServerResponse
```

- [ ] **Step 2: Add the interface method**

In the `interface CatalogApi { ... }` block, add (alphabetically before `getConfigList`):

```kotlin
suspend fun getChatList(baseUrl: String, request: GetRequest): ChatListServerResponse
```

- [ ] **Step 3: Add the `KtorCatalogApi` implementation**

In the `class KtorCatalogApi : CatalogApi { ... }` block, alongside the other `override fun get...` lines, add:

```kotlin
override suspend fun getChatList(baseUrl: String, request: GetRequest) =
    call<ChatListServerResponse>(baseUrl, request)
```

- [ ] **Step 4: Compile**

Run: `./gradlew :shared:compileKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/simplr/mykitta2/data/net/api/CatalogApi.kt
git commit -m "feat(chat): wire CatalogApi.getChatList"
```

---

### Task 5: Add `ChatListCacheStore`

**Files:**
- Create: `shared/src/commonMain/kotlin/com/simplr/mykitta2/data/prefs/ChatListCacheStore.kt`

- [ ] **Step 1: Create the cache store**

```kotlin
package com.simplr.mykitta2.data.prefs

import com.russhwolf.settings.Settings
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Stores only the `fetchedAt` timestamp for the chat-list cache so the repository
 * can decide freshness without re-reading the SQLDelight table. Plain Settings
 * (not secure) — list metadata, not credentials.
 *
 * Wiped on logout via [com.simplr.mykitta2.data.repo.LocalDataWiper].
 */
class ChatListCacheStore(private val settings: Settings) {

    fun fetchedAt(): Long? =
        settings.getLong(KEY_FETCHED_AT, -1L).takeIf { it >= 0L }

    fun setFetchedAt(epochMillis: Long) {
        settings.putLong(KEY_FETCHED_AT, epochMillis)
    }

    fun clear() {
        settings.remove(KEY_FETCHED_AT)
    }

    companion object {
        val DEFAULT_TTL: Duration = 5.minutes
        private const val KEY_FETCHED_AT = "chat_list_fetched_at"
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :shared:compileKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/simplr/mykitta2/data/prefs/ChatListCacheStore.kt
git commit -m "feat(chat): add ChatListCacheStore (TTL fetchedAt)"
```

---

### Task 6: Write failing `ChatRepositoryTest` (TDD red)

**Files:**
- Create: `shared/src/androidHostTest/kotlin/com/simplr/mykitta2/data/repo/ChatRepositoryTest.kt`

> **Pattern:** Mirrors `HistoryRepositoryTest` / `AddressRepositoryTest` — real in-memory SQLite via `JdbcSqliteDriver` + `KtorCatalogApi` driven by a `MockEngine`. The project does **not** maintain a shared `FakeCatalogApi` class.

- [ ] **Step 1: Write the test**

```kotlin
package com.simplr.mykitta2.data.repo

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.russhwolf.settings.MapSettings
import com.simplr.mykitta2.core.result.Outcome
import com.simplr.mykitta2.data.net.api.KtorCatalogApi
import com.simplr.mykitta2.data.prefs.ChatListCacheStore
import com.simplr.mykitta2.data.prefs.SettingsCountryStore
import com.simplr.mykitta2.data.prefs.SettingsSessionStore
import com.simplr.mykitta2.domain.Country
import com.simplr.mykitta2.domain.Session
import com.simplr.mykitta2.shared.db.MyKittaDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ChatRepositoryTest {

    private val jsonHeaders =
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun threeRowsBody(): String = """
        {"getObjectResult":{"errorData":{"code":0,"description":""},"hasMoreRecords":0,"objectData":[[
          {"PrincipalID":"P_READ","PrincipalName":"Read","LastMessage":"x","LastMessageTime":"1","Status":"1","MessageType":"Principal","ImagePath":null},
          {"PrincipalID":"P_USER","PrincipalName":"UserLast","LastMessage":"x","LastMessageTime":"1","Status":"0","MessageType":"User","ImagePath":null},
          {"PrincipalID":"P_NEW","PrincipalName":"New","LastMessage":"hi","LastMessageTime":"1700000000000","Status":"0","MessageType":"Principal","ImagePath":"/img/p_new.png"}
        ]]}}
    """.trimIndent()

    private fun emptyBody(): String =
        """{"getObjectResult":{"errorData":{"code":0,"description":""},"hasMoreRecords":0,"objectData":[[]]}}"""

    private fun freshDb(): MyKittaDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MyKittaDatabase.Schema.create(driver)
        return MyKittaDatabase(driver)
    }

    private data class Harness(
        val repo: DefaultChatRepository,
        val cacheStore: ChatListCacheStore,
        val db: MyKittaDatabase,
        val callCount: () -> Int,
    )

    private suspend fun harness(
        nowMillis: Long = 1_000_000L,
        handler: MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData,
    ): Harness {
        val settings = MapSettings()
        val sessionStore = SettingsSessionStore(settings).also {
            it.write(Session(userName = "u", supervisorCode = "S1", isSupervisor = true))
        }
        val countryStore = SettingsCountryStore(settings).also { it.write(Country.PH) }
        val cacheStore = ChatListCacheStore(MapSettings())
        val db = freshDb()
        var calls = 0
        val client = HttpClient(MockEngine { request ->
            calls += 1
            handler(this, request)
        }) {
            expectSuccess = true
            install(ContentNegotiation) { json() }
        }
        val repo = DefaultChatRepository(
            api = KtorCatalogApi(client),
            queries = db.chatListQueries,
            cacheStore = cacheStore,
            sessionStore = sessionStore,
            countryStore = countryStore,
            now = { nowMillis },
        )
        return Harness(repo, cacheStore, db, { calls })
    }

    @Test
    fun cacheFreshSkipsNetwork() = runTest {
        val h = harness { respond(emptyBody(), HttpStatusCode.OK, jsonHeaders) }
        h.cacheStore.setFetchedAt(1_000_000L - 1_000L) // 1s old, well within 5 min
        val outcome = h.repo.refreshChatList(force = false)
        assertIs<Outcome.Success<Unit>>(outcome)
        assertEquals(0, h.callCount())
    }

    @Test
    fun forceRefreshCallsApiAndReplacesCache() = runTest {
        val h = harness { respond(threeRowsBody(), HttpStatusCode.OK, jsonHeaders) }
        h.cacheStore.setFetchedAt(1_000_000L) // "fresh" — force should still hit network
        val outcome = h.repo.refreshChatList(force = true)
        assertIs<Outcome.Success<Unit>>(outcome)
        assertEquals(1, h.callCount())
        val rows = h.repo.observeChatList().first()
        // P_READ (status=1) and P_USER (messageType=User) are still INCLUDED in
        // the list — only their `isUnread` flag differs. Total rows = 3.
        assertEquals(3, rows.size)
        val byId = rows.associateBy { it.principalId }
        assertEquals(true, byId.getValue("P_NEW").isUnread)
        assertEquals(false, byId.getValue("P_READ").isUnread)
        assertEquals(false, byId.getValue("P_USER").isUnread)
    }

    @Test
    fun staleCacheTriggersNetwork() = runTest {
        val h = harness(nowMillis = 1_000_000L) {
            respond(emptyBody(), HttpStatusCode.OK, jsonHeaders)
        }
        h.cacheStore.setFetchedAt(1_000_000L - (6 * 60_000L)) // 6 min old, beyond 5 min TTL
        val outcome = h.repo.refreshChatList(force = false)
        assertIs<Outcome.Success<Unit>>(outcome)
        assertEquals(1, h.callCount())
    }

    @Test
    fun networkFailureReturnsFailureAndLeavesCache() = runTest {
        val h = harness { respondError(HttpStatusCode.InternalServerError) }
        // Seed an existing row so we can assert it survives a failed refresh.
        h.db.chatListQueries.upsert(
            principalId = "P_EXISTING",
            principalName = "Alpha",
            lastMessage = "hi",
            lastMessageAt = 100L,
            isUnread = 1L,
            imagePath = null,
        )
        val outcome = h.repo.refreshChatList(force = true)
        assertIs<Outcome.Failure>(outcome)
        val rows = h.repo.observeChatList().first()
        assertEquals(1, rows.size, "existing cache must survive a failed refresh")
        assertEquals("P_EXISTING", rows[0].principalId)
    }

    @Test
    fun requestBodyUsesGetChatListFunctionName() = runTest {
        var capturedBody: String? = null
        val h = harness { request ->
            capturedBody = (request.body as io.ktor.http.content.TextContent).text
            respond(emptyBody(), HttpStatusCode.OK, jsonHeaders)
        }
        h.repo.refreshChatList(force = true)
        assertTrue(capturedBody!!.contains("\"functionName\":\"GetChatList\""))
        assertTrue(capturedBody!!.contains("\"recordsize\":100"))
        assertTrue(capturedBody!!.contains("\"user\":\"S1\""))
    }
}
```

- [ ] **Step 2: Confirm test fails (no `DefaultChatRepository` yet)**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.simplr.mykitta2.data.repo.ChatRepositoryTest"`
Expected: FAIL — compile error `Unresolved reference: DefaultChatRepository`.

- [ ] **Step 3: Commit the failing test**

```bash
git add shared/src/androidHostTest/kotlin/com/simplr/mykitta2/data/repo/ChatRepositoryTest.kt
git commit -m "test(chat): red — ChatRepositoryTest for cache/network/unread/request shape"
```

---

### Task 7: Implement `ChatRepository` (TDD green)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/simplr/mykitta2/data/repo/ChatRepository.kt`

- [ ] **Step 1: Implement the repository**

```kotlin
package com.simplr.mykitta2.data.repo

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.simplr.mykitta2.core.env.BuildEnv
import com.simplr.mykitta2.core.error.ErrorMapper
import com.simplr.mykitta2.core.result.Outcome
import com.simplr.mykitta2.data.net.api.CatalogApi
import com.simplr.mykitta2.data.net.dto.ChatListItemDto
import com.simplr.mykitta2.data.net.dto.GetRequest
import com.simplr.mykitta2.data.prefs.ChatListCacheStore
import com.simplr.mykitta2.data.prefs.CountryStore
import com.simplr.mykitta2.data.prefs.SessionStore
import com.simplr.mykitta2.domain.ChatSummary
import com.simplr.mykitta2.domain.Country
import com.simplr.mykitta2.shared.db.ChatListQueries
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val CHAT_LIST_PAGE_SIZE = 100
private const val CHAT_LIST_FALLBACK_USER = "0"

interface ChatRepository {
    fun observeChatList(): Flow<List<ChatSummary>>
    suspend fun refreshChatList(force: Boolean = false): Outcome<Unit>
}

class DefaultChatRepository(
    private val api: CatalogApi,
    private val queries: ChatListQueries,
    private val cacheStore: ChatListCacheStore,
    private val sessionStore: SessionStore,
    private val countryStore: CountryStore,
    /** Injected for tests; production wires [kotlinx.datetime.Clock.System]. */
    private val now: () -> Long = { kotlinx.datetime.Clock.System.now().toEpochMilliseconds() },
) : ChatRepository {

    override fun observeChatList(): Flow<List<ChatSummary>> =
        queries.selectAll().asFlow().mapToList(Dispatchers.Default).map { rows ->
            rows.map { row ->
                ChatSummary(
                    principalId = row.principalId,
                    principalName = row.principalName,
                    lastMessage = row.lastMessage,
                    lastMessageAt = row.lastMessageAt,
                    isUnread = row.isUnread == 1L,
                    imagePath = row.imagePath,
                )
            }
        }

    override suspend fun refreshChatList(force: Boolean): Outcome<Unit> {
        val fetchedAt = cacheStore.fetchedAt()
        val nowMs = now()
        val isFresh = fetchedAt != null &&
            (nowMs - fetchedAt) < ChatListCacheStore.DEFAULT_TTL.inWholeMilliseconds
        if (!force && isFresh) return Outcome.Success(Unit)

        return try {
            val country = countryStore.read() ?: Country.PH
            val baseUrl = BuildEnv.baseUrlFor(country)
            val user = sessionStore.read()?.supervisorCode ?: CHAT_LIST_FALLBACK_USER
            val response = api.getChatList(
                baseUrl = baseUrl,
                request = GetRequest(
                    functionName = "GetChatList",
                    offset = 0,
                    recordsize = CHAT_LIST_PAGE_SIZE,
                    search = "all",
                    sort = "0",
                    user = user,
                    ts = "",
                ),
            )
            val mapped = response.items().mapNotNull { it.toDomain() }
            queries.transaction {
                queries.clearAll()
                mapped.forEach {
                    queries.upsert(
                        principalId = it.principalId,
                        principalName = it.principalName,
                        lastMessage = it.lastMessage,
                        lastMessageAt = it.lastMessageAt,
                        isUnread = if (it.isUnread) 1L else 0L,
                        imagePath = it.imagePath,
                    )
                }
            }
            cacheStore.setFetchedAt(nowMs)
            Outcome.Success(Unit)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Outcome.Failure(ErrorMapper.from(t))
        }
    }
}

/**
 * DTO → domain. Drops rows that don't have a parseable timestamp; the server
 * has historically sent epoch-millis as a string. Returns `null` for malformed
 * rows so callers can mapNotNull.
 */
internal fun ChatListItemDto.toDomain(): ChatSummary? {
    val ts = lastMessageTime?.toLongOrNull() ?: return null
    val name = principalName ?: principalId
    return ChatSummary(
        principalId = principalId,
        principalName = name,
        lastMessage = lastMessage.orEmpty(),
        lastMessageAt = ts,
        isUnread = status == "0" && messageType == "Principal",
        imagePath = imagePath,
    )
}
```

- [ ] **Step 2: Run the test, confirm it passes**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.simplr.mykitta2.data.repo.ChatRepositoryTest"`
Expected: 5 tests PASS.

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/simplr/mykitta2/data/repo/ChatRepository.kt
git commit -m "feat(chat): ChatRepository with 5-min TTL + SQLDelight cache"
```

---

### Task 8: Extend `MyKittaDatabaseWiper` to clear `ChatList`

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/simplr/mykitta2/data/repo/LocalDataWiper.kt`
- Modify: `shared/src/androidHostTest/kotlin/com/simplr/mykitta2/data/repo/MyKittaDatabaseWiperTest.kt`

- [ ] **Step 1: Write the failing wiper test**

Open `shared/src/androidHostTest/kotlin/com/simplr/mykitta2/data/repo/MyKittaDatabaseWiperTest.kt`. Each test in that file is self-contained — it builds a fresh DB via `freshDb()` then calls `MyKittaDatabaseWiper(db).wipeAll()`. Add a new `@Test` modeled on the existing `wipeAll_emptiesNotificationTable` test:

```kotlin
@Test fun wipeAll_emptiesChatListTable() = runTest {
    val db = freshDb()
    db.chatListQueries.upsert(
        principalId = "P1",
        principalName = "Alpha",
        lastMessage = "hi",
        lastMessageAt = 100L,
        isUnread = 1L,
        imagePath = null,
    )
    assertTrue(db.chatListQueries.selectAll().executeAsList().isNotEmpty())

    MyKittaDatabaseWiper(db).wipeAll()

    assertEquals(emptyList(), db.chatListQueries.selectAll().executeAsList())
}
```

- [ ] **Step 2: Run it; confirm RED**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.simplr.mykitta2.data.repo.MyKittaDatabaseWiperTest.wipeAll_clearsChatList"`
Expected: FAIL — the row survives because the wiper doesn't know about `ChatList`.

- [ ] **Step 3: Extend the wiper**

Edit `LocalDataWiper.kt`'s `MyKittaDatabaseWiper.wipeAll` body. Inside the existing `database.principalQueries.transaction { ... }`, append:

```kotlin
database.chatListQueries.deleteAll()
```

So the transaction body becomes:

```kotlin
database.principalQueries.deleteAll()
database.notificationQueries.deleteAll()
database.historyQueries.deleteAll()
database.addressQueries.deleteAll()
database.chatListQueries.deleteAll()
```

- [ ] **Step 4: Re-run; confirm GREEN**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.simplr.mykitta2.data.repo.MyKittaDatabaseWiperTest"`
Expected: all tests PASS, including the new chat-list one.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/simplr/mykitta2/data/repo/LocalDataWiper.kt \
        shared/src/androidHostTest/kotlin/com/simplr/mykitta2/data/repo/MyKittaDatabaseWiperTest.kt
git commit -m "feat(chat): wipe ChatList on logout"
```

---

## Section B — MVI stores

### Task 9: Write failing `ChatListStoreTest` (TDD red)

**Files:**
- Create: `shared/src/commonTest/kotlin/com/simplr/mykitta2/feature/chat/list/ChatListStoreTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package com.simplr.mykitta2.feature.chat.list

import com.arkivanov.mvikotlin.core.utils.isAssertOnMainThreadEnabled
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import com.simplr.mykitta2.core.result.Outcome
import com.simplr.mykitta2.data.repo.ChatRepository
import com.simplr.mykitta2.domain.ChatSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatListStoreTest {

    private lateinit var fakeRepo: FakeChatRepository
    private lateinit var store: ChatListStore

    @BeforeTest
    fun setUp() {
        isAssertOnMainThreadEnabled = false
        fakeRepo = FakeChatRepository()
        store = ChatListStoreFactory(
            storeFactory = DefaultStoreFactory(),
            chatRepository = fakeRepo,
        ).create()
    }

    @AfterTest
    fun tearDown() {
        store.dispose()
    }

    @Test
    fun initLoadsCacheAndTriggersRefresh() = runTest {
        fakeRepo.cacheFlow.value = listOf(sample("P1", unread = true))
        // bootstrapper has already dispatched on construction — give the coroutine
        // a tick to settle.
        testScheduler.runCurrent()
        val s = store.state
        assertEquals(1, s.chats.size)
        assertEquals("P1", s.chats[0].principalId)
        assertEquals(1, fakeRepo.refreshCalls)
    }

    @Test
    fun refreshFailureWithCachedChatsKeepsListAndSetsError() = runTest {
        fakeRepo.cacheFlow.value = listOf(sample("P1"))
        fakeRepo.nextRefreshOutcome = Outcome.Failure(com.simplr.mykitta2.core.error.AppError.Network)
        store.accept(ChatListStore.Intent.Refresh)
        testScheduler.runCurrent()
        val s = store.state
        assertEquals(1, s.chats.size, "chats must survive a failed refresh")
        assertTrue(s.error != null, "error must be set")
    }

    @Test
    fun openContactPickerEmitsNavLabel() = runTest {
        val labels = mutableListOf<ChatListStore.Label>()
        val sub = com.arkivanov.mvikotlin.core.rx.observer<ChatListStore.Label> { labels += it }
        store.labels(sub)
        store.accept(ChatListStore.Intent.OpenContactPicker)
        testScheduler.runCurrent()
        assertTrue(labels.any { it is ChatListStore.Label.NavigateToContactPicker })
    }

    @Test
    fun openThreadEmitsComingSoonSnackbarNotNavigateToThread() = runTest {
        // Phase-1 contract: tapping a row must NOT navigate to a thread (none
        // exists). When Phase 2 lands, this test flips to expect NavigateToThread.
        val labels = mutableListOf<ChatListStore.Label>()
        val sub = com.arkivanov.mvikotlin.core.rx.observer<ChatListStore.Label> { labels += it }
        store.labels(sub)
        store.accept(ChatListStore.Intent.OpenThread("P1"))
        testScheduler.runCurrent()
        val snackbar = labels.filterIsInstance<ChatListStore.Label.ShowSnackbar>().singleOrNull()
        assertTrue(snackbar != null, "expected a ShowSnackbar label")
        assertTrue(
            labels.none { it is ChatListStore.Label.NavigateToThread },
            "Phase 1 must not emit NavigateToThread",
        )
    }

    private fun sample(id: String, unread: Boolean = false) = ChatSummary(
        principalId = id,
        principalName = "Name-$id",
        lastMessage = "msg",
        lastMessageAt = 1L,
        isUnread = unread,
        imagePath = null,
    )

    private class FakeChatRepository : ChatRepository {
        val cacheFlow = MutableStateFlow<List<ChatSummary>>(emptyList())
        var refreshCalls: Int = 0
        var nextRefreshOutcome: Outcome<Unit> = Outcome.Success(Unit)

        override fun observeChatList() = flow { cacheFlow.collect { emit(it) } }
        override suspend fun refreshChatList(force: Boolean): Outcome<Unit> {
            refreshCalls += 1
            return nextRefreshOutcome
        }
    }
}

```

- [ ] **Step 2: Confirm RED**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.simplr.mykitta2.feature.chat.list.ChatListStoreTest"`
Expected: FAIL — unresolved `ChatListStore`, `ChatListStoreFactory`.

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonTest/kotlin/com/simplr/mykitta2/feature/chat/list/ChatListStoreTest.kt
git commit -m "test(chat): red — ChatListStoreTest for init/refresh/labels"
```

---

### Task 10: Implement `ChatListStore` (TDD green)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/chat/list/ChatListStore.kt`

- [ ] **Step 1: Implement the store**

```kotlin
package com.simplr.mykitta2.feature.chat.list

import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineBootstrapper
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import com.simplr.mykitta2.core.error.ErrorMapper
import com.simplr.mykitta2.core.result.Outcome
import com.simplr.mykitta2.data.repo.ChatRepository
import com.simplr.mykitta2.domain.ChatSummary
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

interface ChatListStore :
    Store<ChatListStore.Intent, ChatListStore.State, ChatListStore.Label> {

    data class State(
        val isLoading: Boolean = true,         // initial-load spinner
        val isRefreshing: Boolean = false,     // pull-to-refresh
        val chats: List<ChatSummary> = emptyList(),
        val error: String? = null,
    )

    sealed interface Intent {
        data object Refresh : Intent
        data object OpenContactPicker : Intent
        data class OpenThread(val principalId: String) : Intent
    }

    sealed interface Label {
        data object NavigateToContactPicker : Label
        /** Phase 2 emits this; in Phase 1 [Intent.OpenThread] emits [ShowSnackbar] instead. */
        data class NavigateToThread(val principalId: String) : Label
        data class ShowSnackbar(val message: String) : Label
    }
}

class ChatListStoreFactory(
    private val storeFactory: StoreFactory,
    private val chatRepository: ChatRepository,
) {

    fun create(): ChatListStore = object : ChatListStore,
        Store<ChatListStore.Intent, ChatListStore.State, ChatListStore.Label>
        by storeFactory.create(
            name = "ChatListStore",
            initialState = ChatListStore.State(),
            bootstrapper = BootstrapperImpl(),
            executorFactory = { ExecutorImpl() },
            reducer = ReducerImpl,
        ) {}

    private sealed interface Action {
        data object SubscribeAndRefresh : Action
    }

    private sealed interface Message {
        data class ChatsLoaded(val chats: List<ChatSummary>) : Message
        data object RefreshStarted : Message
        data class RefreshFinished(val error: String?) : Message
    }

    private inner class BootstrapperImpl : CoroutineBootstrapper<Action>() {
        override fun invoke() {
            dispatch(Action.SubscribeAndRefresh)
        }
    }

    private inner class ExecutorImpl :
        CoroutineExecutor<ChatListStore.Intent, Action, ChatListStore.State, Message, ChatListStore.Label>() {

        override fun executeAction(action: Action) {
            when (action) {
                Action.SubscribeAndRefresh -> {
                    scope.launch {
                        chatRepository.observeChatList().collect { chats ->
                            dispatch(Message.ChatsLoaded(chats))
                        }
                    }
                    triggerRefresh(force = false)
                }
            }
        }

        override fun executeIntent(intent: ChatListStore.Intent) {
            when (intent) {
                ChatListStore.Intent.Refresh -> triggerRefresh(force = true)
                ChatListStore.Intent.OpenContactPicker ->
                    publish(ChatListStore.Label.NavigateToContactPicker)
                is ChatListStore.Intent.OpenThread ->
                    publish(ChatListStore.Label.ShowSnackbar("Chat opening soon"))
            }
        }

        private fun triggerRefresh(force: Boolean) {
            dispatch(Message.RefreshStarted)
            scope.launch {
                when (val outcome = chatRepository.refreshChatList(force = force)) {
                    is Outcome.Success -> dispatch(Message.RefreshFinished(error = null))
                    is Outcome.Failure ->
                        dispatch(Message.RefreshFinished(error = ErrorMapper.message(outcome.error)))
                    Outcome.Idle, Outcome.Loading -> Unit
                }
            }
        }
    }

    private object ReducerImpl : Reducer<ChatListStore.State, Message> {
        override fun ChatListStore.State.reduce(msg: Message): ChatListStore.State = when (msg) {
            is Message.ChatsLoaded -> copy(
                chats = msg.chats,
                // The first cache emission terminates the initial spinner even
                // when the list is empty — the empty-state composable handles
                // the zero-row case.
                isLoading = false,
            )
            Message.RefreshStarted -> copy(isRefreshing = true, error = null)
            is Message.RefreshFinished -> copy(
                isRefreshing = false,
                isLoading = false,
                error = msg.error,
            )
        }
    }
}
```

- [ ] **Step 2: Run tests, confirm GREEN**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.simplr.mykitta2.feature.chat.list.ChatListStoreTest"`
Expected: 4 tests PASS.

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/chat/list/ChatListStore.kt
git commit -m "feat(chat): ChatListStore (MVI) with cache subscribe + refresh"
```

---

### Task 11: Add `ChatListViewModel`

**Files:**
- Create: `shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/chat/list/ChatListViewModel.kt`

- [ ] **Step 1: Add the view-model adapter**

```kotlin
package com.simplr.mykitta2.feature.chat.list

import com.simplr.mykitta2.core.mvi.ScreenViewModel

class ChatListViewModel(factory: ChatListStoreFactory) :
    ScreenViewModel<ChatListStore.Intent, ChatListStore.State, ChatListStore.Label>(factory.create())
```

- [ ] **Step 2: Compile**

Run: `./gradlew :shared:compileKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/chat/list/ChatListViewModel.kt
git commit -m "feat(chat): ChatListViewModel adapter"
```

---

### Task 12: Write failing `ChatContactStoreTest` (TDD red)

**Files:**
- Create: `shared/src/commonTest/kotlin/com/simplr/mykitta2/feature/chat/contact/ChatContactStoreTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package com.simplr.mykitta2.feature.chat.contact

import com.arkivanov.mvikotlin.core.utils.isAssertOnMainThreadEnabled
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import com.simplr.mykitta2.core.result.Outcome
import com.simplr.mykitta2.data.repo.PrincipalRepository
import com.simplr.mykitta2.domain.Principal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatContactStoreTest {

    private lateinit var fakeRepo: FakePrincipalRepository
    private lateinit var store: ChatContactStore

    @BeforeTest
    fun setUp() {
        isAssertOnMainThreadEnabled = false
        fakeRepo = FakePrincipalRepository()
        store = ChatContactStoreFactory(
            storeFactory = DefaultStoreFactory(),
            principalRepository = fakeRepo,
        ).create()
    }

    @AfterTest
    fun tearDown() { store.dispose() }

    @Test
    fun initSubscribesAndTriggersRefresh() = runTest {
        fakeRepo.flow.value = listOf(principal("P1"), principal("P2"))
        testScheduler.runCurrent()
        assertEquals(2, store.state.principals.size)
        assertEquals(1, fakeRepo.refreshCalls)
    }

    @Test
    fun selectPrincipalEmitsSnackbarNotNavigate() = runTest {
        val labels = mutableListOf<ChatContactStore.Label>()
        val sub = com.arkivanov.mvikotlin.core.rx.observer<ChatContactStore.Label> { labels += it }
        store.labels(sub)
        store.accept(ChatContactStore.Intent.SelectPrincipal("P1"))
        testScheduler.runCurrent()
        assertTrue(labels.any { it is ChatContactStore.Label.ShowSnackbar })
        assertTrue(labels.none { it is ChatContactStore.Label.NavigateToThread })
    }

    private fun principal(id: String) = Principal(
        principalId = id,
        principalName = "Name-$id",
        principalImg = "",
        isActive = true,
    )

    private class FakePrincipalRepository : PrincipalRepository {
        val flow = MutableStateFlow<List<Principal>>(emptyList())
        var refreshCalls: Int = 0
        override fun observeAll(): Flow<List<Principal>> = flow
        override suspend fun refresh(): Outcome<Unit> {
            refreshCalls += 1
            return Outcome.Success(Unit)
        }
        override suspend fun findById(id: String): Principal? = flow.value.firstOrNull { it.principalId == id }
    }
}
```

> **Note:** Real `Principal` shape (`domain/Principal.kt`) is `Principal(principalId, principalName, principalImg, isActive)` — no `principalLogo`. If `PrincipalRepository` ever gains methods beyond the three implemented by the fake, override the new ones with `error("not used in ChatContactStoreTest")` so a future contract change surfaces here.

- [ ] **Step 2: Confirm RED**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.simplr.mykitta2.feature.chat.contact.ChatContactStoreTest"`
Expected: FAIL — unresolved `ChatContactStore`.

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonTest/kotlin/com/simplr/mykitta2/feature/chat/contact/ChatContactStoreTest.kt
git commit -m "test(chat): red — ChatContactStoreTest"
```

---

### Task 13: Implement `ChatContactStore` + ViewModel (TDD green)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/chat/contact/ChatContactStore.kt`
- Create: `shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/chat/contact/ChatContactViewModel.kt`

- [ ] **Step 1: Implement the store**

```kotlin
package com.simplr.mykitta2.feature.chat.contact

import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineBootstrapper
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import com.simplr.mykitta2.core.error.ErrorMapper
import com.simplr.mykitta2.core.result.Outcome
import com.simplr.mykitta2.data.repo.PrincipalRepository
import com.simplr.mykitta2.domain.Principal
import kotlinx.coroutines.launch

interface ChatContactStore :
    Store<ChatContactStore.Intent, ChatContactStore.State, ChatContactStore.Label> {

    data class State(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val principals: List<Principal> = emptyList(),
        val error: String? = null,
    )

    sealed interface Intent {
        data object Refresh : Intent
        data class SelectPrincipal(val principalId: String) : Intent
    }

    sealed interface Label {
        /** Phase 2 emits this; Phase 1 emits [ShowSnackbar] instead. */
        data class NavigateToThread(val principalId: String) : Label
        data class ShowSnackbar(val message: String) : Label
    }
}

class ChatContactStoreFactory(
    private val storeFactory: StoreFactory,
    private val principalRepository: PrincipalRepository,
) {
    fun create(): ChatContactStore = object : ChatContactStore,
        Store<ChatContactStore.Intent, ChatContactStore.State, ChatContactStore.Label>
        by storeFactory.create(
            name = "ChatContactStore",
            initialState = ChatContactStore.State(),
            bootstrapper = BootstrapperImpl(),
            executorFactory = { ExecutorImpl() },
            reducer = ReducerImpl,
        ) {}

    private sealed interface Action {
        data object SubscribeAndRefresh : Action
    }

    private sealed interface Message {
        data class PrincipalsLoaded(val list: List<Principal>) : Message
        data object RefreshStarted : Message
        data class RefreshFinished(val error: String?) : Message
    }

    private inner class BootstrapperImpl : CoroutineBootstrapper<Action>() {
        override fun invoke() {
            dispatch(Action.SubscribeAndRefresh)
        }
    }

    private inner class ExecutorImpl :
        CoroutineExecutor<ChatContactStore.Intent, Action, ChatContactStore.State, Message, ChatContactStore.Label>() {

        override fun executeAction(action: Action) {
            when (action) {
                Action.SubscribeAndRefresh -> {
                    scope.launch {
                        principalRepository.observeAll().collect { list ->
                            dispatch(Message.PrincipalsLoaded(list.filter { it.isActive }))
                        }
                    }
                    triggerRefresh()
                }
            }
        }

        override fun executeIntent(intent: ChatContactStore.Intent) {
            when (intent) {
                ChatContactStore.Intent.Refresh -> triggerRefresh()
                is ChatContactStore.Intent.SelectPrincipal ->
                    publish(ChatContactStore.Label.ShowSnackbar("Chat opening soon"))
            }
        }

        private fun triggerRefresh() {
            dispatch(Message.RefreshStarted)
            scope.launch {
                when (val outcome = principalRepository.refresh()) {
                    is Outcome.Success -> dispatch(Message.RefreshFinished(error = null))
                    is Outcome.Failure ->
                        dispatch(Message.RefreshFinished(error = ErrorMapper.message(outcome.error)))
                    Outcome.Idle, Outcome.Loading -> Unit
                }
            }
        }
    }

    private object ReducerImpl : Reducer<ChatContactStore.State, Message> {
        override fun ChatContactStore.State.reduce(msg: Message): ChatContactStore.State = when (msg) {
            is Message.PrincipalsLoaded -> copy(principals = msg.list, isLoading = false)
            Message.RefreshStarted -> copy(isRefreshing = true, error = null)
            is Message.RefreshFinished -> copy(
                isRefreshing = false,
                isLoading = false,
                error = msg.error,
            )
        }
    }
}
```

- [ ] **Step 2: Implement the view-model**

```kotlin
package com.simplr.mykitta2.feature.chat.contact

import com.simplr.mykitta2.core.mvi.ScreenViewModel

class ChatContactViewModel(factory: ChatContactStoreFactory) :
    ScreenViewModel<ChatContactStore.Intent, ChatContactStore.State, ChatContactStore.Label>(factory.create())
```

- [ ] **Step 3: Run tests, confirm GREEN**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.simplr.mykitta2.feature.chat.contact.ChatContactStoreTest"`
Expected: 2 tests PASS.

- [ ] **Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/chat/contact/ChatContactStore.kt \
        shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/chat/contact/ChatContactViewModel.kt
git commit -m "feat(chat): ChatContactStore + ViewModel (reuses PrincipalRepository)"
```

---

## Section C — UI, nav, DI

### Task 14: Build `ChatListScreen`

**Files:**
- Create: `shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/chat/list/ChatListScreen.kt`

- [ ] **Step 1: Implement the screen**

```kotlin
package com.simplr.mykitta2.feature.chat.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.simplr.mykitta2.domain.ChatSummary
import com.simplr.mykitta2.ui.common.PlatformBackButton
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onBack: () -> Unit,
    onOpenContactPicker: () -> Unit,
    viewModel: ChatListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.labels.collect { label ->
            when (label) {
                ChatListStore.Label.NavigateToContactPicker -> onOpenContactPicker()
                is ChatListStore.Label.NavigateToThread -> Unit // Phase 2 wires this
                is ChatListStore.Label.ShowSnackbar ->
                    snackbarHostState.showSnackbar(label.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Messages") },
                navigationIcon = { PlatformBackButton(onClick = onBack) },
                actions = {
                    if (state.chats.isNotEmpty()) {
                        IconButton(onClick = { viewModel.accept(ChatListStore.Intent.OpenContactPicker) }) {
                            Icon(Icons.Default.Add, contentDescription = "New chat")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.isLoading && state.chats.isEmpty() -> CircularProgressIndicator()
                !state.isLoading && state.chats.isEmpty() && state.error == null ->
                    EmptyState(onStartConversation = { viewModel.accept(ChatListStore.Intent.OpenContactPicker) })
                state.chats.isEmpty() && state.error != null ->
                    ErrorState(message = state.error!!, onRetry = { viewModel.accept(ChatListStore.Intent.Refresh) })
                else -> ChatList(
                    state = state,
                    onRefresh = { viewModel.accept(ChatListStore.Intent.Refresh) },
                    onRowTap = { viewModel.accept(ChatListStore.Intent.OpenThread(it)) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatList(
    state: ChatListStore.State,
    onRefresh: () -> Unit,
    onRowTap: (String) -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (state.error != null) {
                item { ErrorBanner(message = state.error, onRetry = onRefresh) }
            }
            items(state.chats, key = { it.principalId }) { chat ->
                ChatListRow(chat = chat, onClick = { onRowTap(chat.principalId) })
            }
        }
    }
}

@Composable
private fun ChatListRow(chat: ChatSummary, onClick: () -> Unit) {
    // Avatar intentionally omitted when imagePath is null/blank — matches the
    // app-wide rule (memory 09:29): no placeholder slot, no error glyph.
    androidx.compose.material3.ListItem(
        headlineContent = { Text(chat.principalName) },
        supportingContent = { Text(chat.lastMessage, maxLines = 1) },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(formatRelative(chat.lastMessageAt), style = MaterialTheme.typography.labelSmall)
                if (chat.isUnread) {
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier.size(8.dp),
                        content = {
                            Icon(
                                imageVector = Icons.Default.Add, // any solid glyph; use Material 3 Brightness1 if available
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
    androidx.compose.foundation.layout.Spacer(Modifier.height(0.dp)) // keep import alive
}

@Composable
private fun EmptyState(onStartConversation: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(32.dp),
    ) {
        Text("No messages yet", style = MaterialTheme.typography.titleMedium)
        Button(onClick = onStartConversation) { Text("Start a conversation") }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(32.dp),
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(12.dp),
        ) {
            Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}

/**
 * Lightweight relative formatter — "1m" / "2h" / "Mon" / "MMM d".
 * Replace with [androidx.compose.material3.DatePickerDefaults] / kotlinx-datetime
 * if the project picks a single helper later.
 */
private fun formatRelative(epochMillis: Long): String {
    val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
    val deltaMin = (now - epochMillis) / 60_000L
    return when {
        deltaMin < 1 -> "now"
        deltaMin < 60 -> "${deltaMin}m"
        deltaMin < 24 * 60 -> "${deltaMin / 60}h"
        deltaMin < 7 * 24 * 60 -> "${deltaMin / (24 * 60)}d"
        else -> {
            val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(epochMillis)
            val date = instant.toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
            "${date.month.name.take(3)} ${date.dayOfMonth}"
        }
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :shared:compileKotlinAndroid`
Expected: BUILD SUCCESSFUL.

> **If the unread dot's `Icons.Default.Add` glyph looks wrong** (placeholder choice — Material 3 doesn't always ship `Brightness1`), replace the trailing `Box` content with a colored circle via `Spacer(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))`. Both options compile; the spec wants "a small accent disc".

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/chat/list/ChatListScreen.kt
git commit -m "feat(chat): ChatListScreen with pull-refresh, empty/error states"
```

---

### Task 15: Build `ChatContactScreen`

**Files:**
- Create: `shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/chat/contact/ChatContactScreen.kt`

- [ ] **Step 1: Implement the screen**

```kotlin
package com.simplr.mykitta2.feature.chat.contact

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.simplr.mykitta2.domain.Principal
import com.simplr.mykitta2.ui.common.PlatformBackButton
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatContactScreen(
    onBack: () -> Unit,
    viewModel: ChatContactViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.labels.collect { label ->
            when (label) {
                is ChatContactStore.Label.NavigateToThread -> Unit // Phase 2 wires this
                is ChatContactStore.Label.ShowSnackbar ->
                    snackbarHostState.showSnackbar(label.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select a principal") },
                navigationIcon = { PlatformBackButton(onClick = onBack) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.isLoading && state.principals.isEmpty() -> CircularProgressIndicator()
                state.principals.isEmpty() && state.error != null ->
                    ErrorState(message = state.error!!, onRetry = { viewModel.accept(ChatContactStore.Intent.Refresh) })
                state.principals.isEmpty() ->
                    Text("No principals available", style = MaterialTheme.typography.bodyMedium)
                else -> Grid(
                    state = state,
                    onRefresh = { viewModel.accept(ChatContactStore.Intent.Refresh) },
                    onSelect = { viewModel.accept(ChatContactStore.Intent.SelectPrincipal(it)) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Grid(
    state: ChatContactStore.State,
    onRefresh: () -> Unit,
    onSelect: (String) -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.principals, key = { it.principalId }) { p ->
                PrincipalTile(p, onClick = { onSelect(p.principalId) })
            }
        }
    }
}

@Composable
private fun PrincipalTile(principal: Principal, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(12.dp),
        ) {
            // Avatar/logo omitted when not present — same rule as ChatListRow.
            Text(principal.principalName, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(32.dp),
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onRetry) { Text("Retry") }
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :shared:compileKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/chat/contact/ChatContactScreen.kt
git commit -m "feat(chat): ChatContactScreen — principal picker grid"
```

---

### Task 16: Register destinations + wire nav

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/simplr/mykitta2/ui/nav/Destination.kt`
- Modify: `shared/src/commonMain/kotlin/com/simplr/mykitta2/ui/nav/AppNavHost.kt`

- [ ] **Step 1: Add new destinations**

In `Destination.kt`, alongside existing entries like `Notifications`, add:

```kotlin
@Serializable data object ChatList : Destination
@Serializable data object ChatContact : Destination
```

- [ ] **Step 2: Register composables in `AppNavHost.kt`**

After the existing `composable<Destination.Notifications> { ... }` block, add:

```kotlin
composable<Destination.ChatList> {
    com.simplr.mykitta2.feature.chat.list.ChatListScreen(
        onBack = { navController.popBackStack() },
        onOpenContactPicker = { navController.navigate(Destination.ChatContact) },
    )
}
composable<Destination.ChatContact> {
    com.simplr.mykitta2.feature.chat.contact.ChatContactScreen(
        onBack = { navController.popBackStack() },
    )
}
```

- [ ] **Step 3: Wire `MainShell` to push `ChatList`**

In `AppNavHost.kt`, inside the existing `composable<Destination.Home> { ... }` block, find the `MainShell(...)` call site. It currently passes `onOpenNotifications = { navController.navigate(Destination.Notifications) }`. Add the chat callback symmetrically (the param name may need to be introduced — see Step 4 in `MainShell`):

```kotlin
onOpenChat = { navController.navigate(Destination.ChatList) },
```

- [ ] **Step 4: Plumb `onOpenChat` through `MainShell`**

Open `shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/main/MainShell.kt`. Find the function signature — it should accept `onOpenNotifications` and `onOpenSearch` already. Add a sibling:

```kotlin
onOpenChat: () -> Unit,
```

Then at line ~117 replace:

```kotlin
onOpenChat = { /* Chat destination lands in a later phase. */ },
```

with:

```kotlin
onOpenChat = onOpenChat,
```

(The `HomeScreen` already declares `onOpenChat` — see `HomeScreen.kt:71` — so no changes there.)

- [ ] **Step 5: Compile**

Run: `./gradlew :shared:compileKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/simplr/mykitta2/ui/nav/Destination.kt \
        shared/src/commonMain/kotlin/com/simplr/mykitta2/ui/nav/AppNavHost.kt \
        shared/src/commonMain/kotlin/com/simplr/mykitta2/feature/main/MainShell.kt
git commit -m "feat(chat): nav — ChatList + ChatContact destinations, wire HomeScreen icon"
```

---

### Task 17: Add `featureChatModule` to Koin

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/simplr/mykitta2/di/AppModule.kt`

- [ ] **Step 1: Add the imports**

In the import block at the top of `AppModule.kt`, add (alphabetically grouped with existing imports):

```kotlin
import com.simplr.mykitta2.data.prefs.ChatListCacheStore
import com.simplr.mykitta2.data.repo.ChatRepository
import com.simplr.mykitta2.data.repo.DefaultChatRepository
import com.simplr.mykitta2.feature.chat.contact.ChatContactStoreFactory
import com.simplr.mykitta2.feature.chat.contact.ChatContactViewModel
import com.simplr.mykitta2.feature.chat.list.ChatListStoreFactory
import com.simplr.mykitta2.feature.chat.list.ChatListViewModel
```

(`module`, `viewModelOf`, `MyKittaDatabase`, `SettingsFactory` are already imported.)

- [ ] **Step 2: Add the module**

Below the existing `val featureNotificationModule = module { ... }` block, add:

```kotlin
val featureChatModule = module {
    single { ChatListCacheStore(get<SettingsFactory>().plainSettings("chat_list_cache")) }
    single<ChatRepository> {
        DefaultChatRepository(
            api = get(),
            queries = get<MyKittaDatabase>().chatListQueries,
            cacheStore = get(),
            sessionStore = get(),
            countryStore = get(),
        )
    }
    factory {
        ChatListStoreFactory(
            storeFactory = get(),
            chatRepository = get(),
        )
    }
    viewModelOf(::ChatListViewModel)
    factory {
        ChatContactStoreFactory(
            storeFactory = get(),
            principalRepository = get(),
        )
    }
    viewModelOf(::ChatContactViewModel)
}
```

- [ ] **Step 3: Append to `commonModules()`**

Find the `fun commonModules(): List<Module> = listOf(...)` declaration (around `AppModule.kt:282`). Add `featureChatModule` to the list, next to `featureNotificationModule`.

- [ ] **Step 4: Compile + run all tests**

Run: `./gradlew :shared:testAndroidHostTest`
Expected: BUILD SUCCESSFUL, all tests PASS (previous + new chat tests, ~76+ tests).

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/simplr/mykitta2/di/AppModule.kt
git commit -m "feat(chat): Koin module — ChatRepository + stores + view-models"
```

---

### Task 18: Add chat-list wiper coverage to host test

> **Note:** This task overlaps with Task 8. If Task 8 was completed properly, Step 1 here is a no-op verification. Keep it as a checkpoint to confirm the wiper test still passes after all the new wiring in Tasks 9–17.

- [ ] **Step 1: Re-run the host-test suite**

Run: `./gradlew :shared:testAndroidHostTest`
Expected: 100% PASS. If `MyKittaDatabaseWiperTest.wipeAll_clearsChatList` fails now (regression from Task 17's nav wiring touching anything unexpected), bisect via `git log` and fix.

---

### Task 19: Verify wire format against a live dev response

**Why this task exists:** Notifications had a wire-format mismatch caught only after deploying to a real device (memory note `09:14` — `isRead -1` sentinel, snake_case fields). The DTO in Task 2 is best-effort from the legacy `ChatEntity` shape. Catch any drift now.

- [ ] **Step 1: Build the dev APK**

Run: `./gradlew :androidApp:assembleDevDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Install + log in to dev environment**

Use a test account that has at least one existing conversation. If no conversations exist yet, escalate to the backend team for seed data — do NOT merge with an empty live response, because empty payloads hide field-casing bugs.

- [ ] **Step 3: Capture the raw response**

Open the chat icon on Home. The Chucker interceptor (Android dev build) will record the `User/GetObject` call with `functionName=GetChatList`. Copy the raw JSON response.

- [ ] **Step 4: Diff response keys against the DTO**

Compare every key in the response's `objectData[0][i]` rows against the `@SerialName(...)` values in `ChatDtos.kt`. Any mismatch (case, alternate names, type) is a bug — update the DTO and add a one-line comment explaining the live name.

Common drifts to watch for:
- `PrincipalID` vs `PrincipalId` vs `principalId`.
- `LastMessageTime` may be ISO-8601 instead of epoch millis. If so, change the parser in `toDomain` accordingly:
  ```kotlin
  val ts = lastMessageTime?.let { runCatching { kotlinx.datetime.Instant.parse(it).toEpochMilliseconds() }.getOrNull() } ?: return null
  ```
- `Status` may be an integer literal (`0` / `1`) rather than a string — switch the DTO field to `Int?` and the comparison to `status == 0`.

- [ ] **Step 5: If anything changed, update tests**

Adjust `ChatRepositoryTest.unreadDerivedFromStatusAndMessageType` and any fixture rows so they match the new wire format. Re-run:

`./gradlew :shared:testAndroidHostTest --tests "com.simplr.mykitta2.data.repo.ChatRepositoryTest"`
Expected: PASS.

- [ ] **Step 6: Commit (if changes were needed)**

```bash
git add shared/src/commonMain/kotlin/com/simplr/mykitta2/data/net/dto/ChatDtos.kt \
        shared/src/commonMain/kotlin/com/simplr/mykitta2/data/repo/ChatRepository.kt \
        shared/src/commonTest/kotlin/com/simplr/mykitta2/data/repo/ChatRepositoryTest.kt
git commit -m "fix(chat): align ChatListItemDto with live wire format"
```

If the DTO matched on the first shot, commit nothing — the absence of a commit here is itself proof the format was correct.

---

### Task 20: End-to-end smoke on device

- [ ] **Step 1: Run the dev APK**

Already installed in Task 19. Force-stop + relaunch to ensure a clean Koin graph.

- [ ] **Step 2: Drive the happy path manually**

Confirm in order:
1. Home → chat icon → ChatList opens.
2. Pull down → spinner appears, refresh runs, list updates.
3. Tap a row → snackbar "Chat opening soon" appears, no nav.
4. Tap "+" (only visible if conversations exist) → ChatContact opens.
5. Tap a principal tile → snackbar "Chat opening soon" appears, no nav.
6. Back from ChatContact → back to ChatList.
7. Back from ChatList → back to Home.
8. Log out → log back in → ChatList shows fresh data (cache was wiped).

- [ ] **Step 3: Drive empty-state path**

If account has no conversations: confirm "No messages yet" + "Start a conversation" CTA navigates to ChatContact. Verify the `+` is **not** rendered when the list is empty.

- [ ] **Step 4: Drive failure path**

Enable airplane mode mid-session → pull-to-refresh → confirm error banner appears above the cached list (not full-screen) and chats remain visible. Disable airplane mode → retry → list refreshes cleanly.

- [ ] **Step 5: iOS sanity (if simulator handy)**

Open `iosApp/iosApp.xcodeproj` and run on simulator. Smoke the same flow — at minimum confirm the screen opens and a refresh succeeds. Differences vs. Android (back chevron glyph, snackbar style) are expected per `PlatformBackButton` and Material 3 cross-platform defaults; flag any real regressions.

- [ ] **Step 6: Commit nothing — this is a sign-off step**

If smoke passes, the work is done. If any step fails, open a ticket or fix inline before declaring Phase 1 complete.

---

## Verification summary

After all 20 tasks complete:

| Verification | Command | Expected |
|---|---|---|
| All shared tests | `./gradlew :shared:testAndroidHostTest` | BUILD SUCCESSFUL, all tests pass |
| Android dev build | `./gradlew :androidApp:assembleDevDebug` | BUILD SUCCESSFUL |
| iOS framework | `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` | BUILD SUCCESSFUL |
| Device smoke | (manual, Task 20) | All 4 flows pass |

## Out of scope (later phases — DO NOT implement)

- Chat thread screen
- `Chat/ReadChat` API call
- Send message (`Chat/SendChat`)
- 10-second polling
- Mark-read writes
- FCM/APNS push
- Numeric badge on the Home chat icon
- UI pagination of the chat list

If a reviewer asks "where's the thread?" — point them at the spec's phase decomposition. Phase 2 is its own brainstorm + spec + plan cycle.
