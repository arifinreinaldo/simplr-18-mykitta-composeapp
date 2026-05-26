# Chat (Messages) Feature — Phase 1 Design

**Date:** 2026-05-26
**Author:** reinaldo (with Claude)
**Phase:** 1 of N (chat list + principal picker, no thread yet)
**Status:** Spec — pending implementation plan

## Phases (decomposition)

The full chat scope (list + thread + send + polling + push) is split into independently revertible phases. **This spec covers Phase 1 only.**

| Phase | Scope | Status |
|---|---|---|
| **1** | Chat list + principal-picker screen (this spec) | Designing |
| 2 | Chat thread screen (read history, render bubbles, group by date) | Deferred |
| 3 | Send message + thread polling (legacy 10s loop) | Deferred |
| 4 | Mark-read semantics + per-row unread propagation | Deferred |
| 5 | FCM / APNS push for new-message events | Deferred |

## Goals (Phase 1)

- Make the chat icon on `HomeScreen` (currently a no-op — `MainShell.kt:117`) navigate to a real Chat List screen.
- Render the user's existing conversations as one row per principal, with last-message preview, relative timestamp, avatar, and an unread accent dot.
- Provide a principal-picker screen reachable from (a) the chat list empty-state CTA and (b) a "+ new chat" top-bar action when the list is non-empty.
- Open + pull-to-refresh data freshness with a 5-minute TTL backed by SQLDelight (matches History/Notification Phase 1 pattern).
- Phase-1 stubs: tapping a chat row OR a picker tile shows a `"Coming soon"` snackbar — no thread yet.

## Non-goals (deferred to later phases)

- No chat thread screen, no message send, no polling, no mark-read writes.
- No FCM/APNS push.
- No numeric chat badge on the Home top-bar (per-row dot only — see decision in Section 3).
- No UI pagination of the chat list — single `recordsize=100` fetch per refresh and replace-cache (Section 4).
- No Compose UI tests (project has none).

## Current state (what's already there)

- `HomeScreen.kt:71` declares `onOpenChat: () -> Unit = {}`.
- `HomeScreen.kt:109` renders `BadgedIconButton(glyph = "💬", onClick = onOpenChat)` — already wired to the callback.
- `MainShell.kt:117` passes `onOpenChat = { /* Chat destination lands in a later phase. */ }` — a no-op today.
- `PrincipalRepository.observeCache()` already exposes the active principal grid (inactive principals filtered out — see memory `10:27`). Phase 1 reuses this for the picker; no new principal repo needed.
- No chat-related Koin modules, repositories, DTOs, SQLDelight tables, or screens exist yet.

## Backend contract (legacy reference)

Source: `llm_wiki/features/chat.md` and `llm_wiki/deep/repository.md:180-182`.

| Operation | HTTP | Endpoint | functionName | Notes |
|---|---|---|---|---|
| **List conversations** (Phase 1) | POST | `User/GetObject` | `GetChatList` | Body: `search="all"`, `ts=<last-known-ts \| "">`, `sort`, `offset=0`, `recordsize=100`, `user`. Server returns the user's chat-list rows. |
| Read thread (Phase 2) | POST | `Chat/ReadChat` | (dedicated) | `functionName=GetChatVendor`, `recordsize=9999`, `ts=last-read-per-principal`. |
| Send message (Phase 3) | POST | `Chat/SendChat` | (dedicated) | Body: `ChatRequest { chat, principal }`. |

**Wire-format gotcha:** Legacy DTOs use PascalCase field names (`PrincipalID`, `LastMessage`, `Status`). The Notifications phase had the same gotcha and required a DTO fix after the first live response (memory `09:14`). The implementation plan includes a verification gate before merging — pull one real response, confirm field casing/types, fix DTO if needed.

## Architecture overview

Two screens, two stores, **one** new repository.

```
shared/src/commonMain/kotlin/com/simplr/mykitta2/
  feature/chat/
    list/
      ChatListStore.kt
      ChatListScreen.kt
    contact/
      ChatContactStore.kt
      ChatContactScreen.kt
  data/repo/
    ChatRepository.kt              (new)
  data/prefs/
    ChatListCacheStore.kt          (new — plain Settings, only fetchedAt)
  data/net/dto/
    ChatDtos.kt                    (new)
  data/net/api/
    CatalogApi.kt                  (extend — add getChatList)
  domain/
    ChatSummary.kt                 (new)
  ui/nav/
    Destination.kt                 (extend — add ChatList + ChatContact)
  ui/nav/AppNavHost.kt             (extend — register composables)
  di/AppModule.kt                  (extend — featureChatModule)

shared/src/commonMain/sqldelight/com/simplr/mykitta2/shared/db/
  ChatList.sq                      (new)
```

**Navigation topology.** `ChatList` and `ChatContact` are **top-level destinations**, sibling to `Search` and `ProfileDetail` — not bottom-nav tabs. The chat list opens as a full-screen push above `MainShell`; the picker pushes above the list. Back from picker → list; back from list → home tab. This matches how Search and ProfileDetail are mounted.

```
AppNavHost
├─ Splash (outside)
├─ LoginOtp / OtpVerify
├─ Home (= MainShell — owns its own inner NavController)
├─ Search
├─ ProfileDetail
├─ ChatList        ← new
└─ ChatContact     ← new
```

## Data layer

### DTOs (`data/net/dto/ChatDtos.kt`)

```kotlin
@Serializable
data class GetChatListRequest(
    val functionName: String = "GetChatList",
    val search: String = "all",
    val ts: String,                 // "" first time; else last cached lastMessageAt
    val sort: String = "0",
    val offset: Int = 0,
    val recordsize: Int = 100,
    val user: String,               // session user id
)

@Serializable
data class ChatListItemDto(
    val PrincipalID: String,
    val PrincipalName: String? = null,
    val LastMessage: String? = null,
    val LastMessageTime: String? = null,    // verify format on first live response
    val Status: String? = null,             // "0" = unread when MessageType=="Principal"
    val MessageType: String? = null,        // "Principal" | "User"
    val ImagePath: String? = null,
)

@Serializable
data class ChatListServerResponse(
    val Status: String,                     // "1" = success (existing convention)
    val Message: String? = null,
    val Data: List<ChatListItemDto> = emptyList(),
)
```

**Note:** Exact field names + casing are best-effort from the legacy `ChatEntity`. The implementation plan has a "verify wire format" task that gates the merge — pull one real response from the dev environment and adjust DTOs before any UI is wired.

### Domain (`domain/ChatSummary.kt`)

```kotlin
data class ChatSummary(
    val principalId: String,
    val principalName: String,
    val lastMessage: String,
    val lastMessageAt: Long,                // epoch ms; sort key
    val isUnread: Boolean,                  // derived from DTO Status+MessageType
    val imagePath: String?,
)
```

### SQLDelight (`ChatList.sq`)

```sql
CREATE TABLE ChatList (
    principalId   TEXT NOT NULL PRIMARY KEY,
    principalName TEXT NOT NULL,
    lastMessage   TEXT NOT NULL,
    lastMessageAt INTEGER NOT NULL,
    isUnread      INTEGER NOT NULL,         -- 0/1
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

`MyKittaDatabaseWiper.wipeAll()` is extended to include `chatListQueries.deleteAll()` inside the existing transaction.

### Cache TTL store (`data/prefs/ChatListCacheStore.kt`)

Plain Settings (not secure — list metadata, not credentials). Mirrors `ProfileCacheStore`'s shape but only stores `fetchedAt`:

```kotlin
class ChatListCacheStore(private val settings: Settings) {
    fun fetchedAt(): Long? = settings.getLongOrNull(KEY_FETCHED_AT)
    fun setFetchedAt(v: Long) = settings.putLong(KEY_FETCHED_AT, v)
    fun clear() = settings.remove(KEY_FETCHED_AT)
    companion object { val DEFAULT_TTL: Duration = 5.minutes }
}
```

Registered in `LocalDataWiper` alongside `ProfileCacheStore` so logout clears the timestamp.

### Repository (`data/repo/ChatRepository.kt`)

```kotlin
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
    private val clock: Clock = Clock.System,
) : ChatRepository {

    override fun observeChatList(): Flow<List<ChatSummary>> =
        queries.selectAll().asFlow().mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::toDomain) }

    override suspend fun refreshChatList(force: Boolean): Outcome<Unit> {
        val fetchedAt = cacheStore.fetchedAt()
        val now = clock.now().toEpochMilliseconds()
        if (!force && fetchedAt != null && now - fetchedAt < TTL.inWholeMilliseconds) {
            return Outcome.Success(Unit)
        }
        return runCatching {
            val baseUrl = BuildEnv.baseUrlFor(countryStore.read())
            val session = sessionStore.read() ?: error("No session")
            val response = api.getChatList(baseUrl, session.userId, ts = "")
            queries.transaction {
                queries.clearAll()
                response.Data.forEach { dto -> queries.upsert(...) }
            }
            cacheStore.setFetchedAt(now)
        }.toOutcome(ErrorMapper)
    }

    companion object { val TTL: Duration = 5.minutes }
}
```

**Conventions enforced:**
- All exceptions funnel through `ErrorMapper`. `CancellationException` is re-thrown.
- Each call resolves `BuildEnv.baseUrlFor(country)` at call time (per-country backend, per CLAUDE.md).
- DTO → domain mapping centralized in repository (`toDomain`), not in stores.

## MVI stores

### `ChatListStore` (`feature/chat/list/ChatListStore.kt`)

```kotlin
sealed interface Intent {
    data object Refresh : Intent                        // pull-to-refresh OR retry
    data object OpenContactPicker : Intent              // empty CTA + "+" action
    data class OpenThread(val principalId: String) : Intent  // Phase-1 stub
}

data class State(
    val isLoading: Boolean = false,                     // initial load, no cache yet
    val isRefreshing: Boolean = false,                  // pull-to-refresh indicator
    val chats: List<ChatSummary> = emptyList(),
    val error: String? = null,
)

sealed interface Label {
    data object NavigateToContactPicker : Label
    data class NavigateToThread(val principalId: String) : Label  // Phase 2 wires this
    data class ShowSnackbar(val message: String) : Label
}
```

**Executor bootstrap (on store create):**
1. Subscribe to `chatRepository.observeChatList()` → emit `Message.ChatsLoaded(list)`.
2. Call `chatRepository.refreshChatList(force = false)` — TTL-respecting; emits `Message.RefreshStarted` and `Message.RefreshFinished(error?)`.

**Intent handling:**
- `Refresh` → `chatRepository.refreshChatList(force = true)`.
- `OpenContactPicker` → publish `Label.NavigateToContactPicker`.
- `OpenThread` → publish `Label.ShowSnackbar("Chat opening soon")`. **Not** `NavigateToThread` yet — the next phase changes this single line.

**Error UX:**
- Initial load, no cache, refresh fails → renders centered error w/ retry.
- Refresh fails with cached chats present → inline banner above list, retry → `Intent.Refresh`. Chats stay on screen.

### `ChatContactStore` (`feature/chat/contact/ChatContactStore.kt`)

```kotlin
sealed interface Intent {
    data object Refresh : Intent
    data class SelectPrincipal(val principalId: String) : Intent
}

data class State(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val principals: List<Principal> = emptyList(),
    val error: String? = null,
)

sealed interface Label {
    data class NavigateToThread(val principalId: String) : Label    // Phase 2 wires this
    data class ShowSnackbar(val message: String) : Label
}
```

**Reuses `PrincipalRepository`** — no new repo, no new SQLDelight table.

- Bootstrap subscribes to `principalRepository.observeCache()` (already filtered to active principals per memory `10:27`).
- Bootstrap calls `principalRepository.refresh(...)` if its own TTL says so (Phase 1 doesn't introduce parallel TTL logic).
- `SelectPrincipal` → `Label.ShowSnackbar("Chat opening soon")`. Single-line swap to `NavigateToThread` in Phase 2.

### ViewModels

Both screens use `ScreenViewModel<Intent, State, Label>` adapters (the standard pattern). Names: `ChatListViewModel`, `ChatContactViewModel`. Each owns its store factory; `koinViewModel<ChatListViewModel>()` resolves through the standard Koin module.

## Screens & nav wiring

### `ChatListScreen`

- **App bar:** title "Messages", back via `PlatformBackButton`. Trailing "+" icon → `Intent.OpenContactPicker`. The "+" is **hidden** while `state.chats.isEmpty()` (the empty-state CTA covers that case — avoids two new-chat affordances on one screen).
- **Body:** `PullToRefreshBox` wrapping a `LazyColumn` of `ChatListRow`s.
- **Row anatomy:**
  - Avatar (Coil `AsyncImage`, hidden entirely when no/error image — per memory `09:29`, single-client `IMAGE_HTTP_CLIENT`).
  - Principal name (bold, primary text).
  - Last message preview (single line, ellipsized, secondary text).
  - Relative timestamp right-aligned ("2m", "1h", "Mon", "MMM d") — formatter lives in `ui/common/HelperUI.kt` (memory `09:17` already established that file).
  - Unread dot (small accent disc) trailing the timestamp when `isUnread`.
- **Empty state** (only after refresh succeeded with zero chats): centered illustration + headline "No messages yet" + primary button "Start a conversation" → `Intent.OpenContactPicker`.
- **Error banner:** when `state.error != null` AND `chats.isNotEmpty()` — inline retry row at top of list.
- **Error fullscreen:** when `state.error != null` AND `chats.isEmpty()` — centered icon + retry button.
- **Loading skeleton:** 5 row shimmers when `isLoading && chats.isEmpty()`.

### `ChatContactScreen`

- **App bar:** title "Select a principal", back via `PlatformBackButton`.
- **Body:** 2-column `LazyVerticalGrid` of principal tiles (mirror existing `PrincipalScreen` tile style — image + name).
- **Pull-to-refresh, loading/error states** mirror `ChatListScreen`.
- Tap a tile → `Intent.SelectPrincipal(principalId)` → snackbar stub.

### Nav (`ui/nav/Destination.kt` + `AppNavHost.kt`)

```kotlin
@Serializable data object ChatList : Destination
@Serializable data object ChatContact : Destination
```

- `MainShell.kt:117` `onOpenChat = { /* later phase */ }` is replaced with a hoisted callback that calls `navController.navigate(Destination.ChatList)`. `AppNavHost` adds two `composable<Destination.X>` entries.
- `ChatList → ChatContact`: `navigate(ChatContact)`. Simple stack push.
- `ChatContact → back`: `popBackStack()`.
- `ChatList → back`: `popBackStack()`. Lands back on `MainShell` Home tab.
- No `popUpTo` games — these are pure stack pushes, like `Search` and `ProfileDetail`.

### Koin (`di/AppModule.kt`)

New `featureChatModule`:

```kotlin
val featureChatModule = module {
    single<ChatRepository> { DefaultChatRepository(get(), get<MyKittaDatabase>().chatListQueries, get(), get(), get()) }
    single { ChatListCacheStore(plainSettings(name = "chat_list_cache")) }
    viewModel { ChatListViewModel(get()) }
    viewModel { ChatContactViewModel(get()) }
}
```

Appended to `commonModules()` next to `featureNotificationModule`.

### `LocalDataWiper`

- `MyKittaDatabaseWiper.wipeAll()` extended with `chatListQueries.deleteAll()` inside the existing transaction.
- `ChatListCacheStore.clear()` added to the user-scoped stores wipe list (alongside `ProfileCacheStore`).

## Error handling rules

- Repository exceptions funnel through `ErrorMapper.from(throwable)`; `CancellationException` re-thrown.
- Stores never see raw `Throwable`s — only `AppError` via `Outcome.Failure(AppError)`.
- User-facing strings come from `ErrorMapper.message(error)`. No hand-written messages in `ChatListStore` / `ChatContactStore`.

## Testing

### `shared/src/commonTest`

- `ChatRepositoryTest`
  - Cache-fresh path: returns Success without calling API.
  - Force refresh: calls API, replaces cache, updates `fetchedAt`.
  - Stale cache: calls API and refreshes.
  - Network failure: returns `Outcome.Failure`, cache untouched, `fetchedAt` unchanged.
  - `observeChatList` emits new rows after `refreshChatList`.
- `ChatListStoreTest`
  - Init: loads cached chats + triggers refresh.
  - Pull-to-refresh: sets `isRefreshing=true`, clears on result.
  - Refresh failure WITH cached chats: keeps chats, sets `error`.
  - Refresh failure WITHOUT cached chats: `isLoading=false`, `chats.isEmpty()`, `error` set.
  - `Intent.OpenContactPicker` → `Label.NavigateToContactPicker`.
  - `Intent.OpenThread` → `Label.ShowSnackbar(...)` (stub assertion — explicit regression guard so Phase 2 wiring is intentional).
- `ChatContactStoreTest`
  - Observes principals from `PrincipalRepository.observeCache`.
  - `Intent.SelectPrincipal` → `Label.ShowSnackbar(...)` (stub assertion).

### `shared/src/androidHostTest`

- `MyKittaDatabaseWiperTest` extended — inserts a row into `ChatList`, calls `wipeAll`, asserts table empty (mirrors existing test pattern for other tables).

### DTO verification gate

Final implementation-plan task: pull a real `GetChatList` response from dev (Chucker or device DB inspector), verify DTO field casing + types, adjust if needed, before merging. Notifications had this exact gotcha at memory `09:14`.

## Rollout

- **Single PR** — no flag. Chat icon is currently a no-op; turning it on is additive and reversible.
- **No Crashlytics instrumentation** beyond the global handler.
- Existing memory note about hiding inactive principals (`10:27`) covers the picker out of the box.

## Open questions (resolved in clarifications)

- **Q:** Which list first — existing chats or contact picker? → **A:** Existing chats.
- **Q:** Scope of Phase 1? → **A:** List + picker; thread is later.
- **Q:** Real-time strategy? → **A:** Open + pull-to-refresh + 5-min TTL. No polling.
- **Q:** Home chat-icon badge? → **A:** Per-row unread dot only. No Home badge.
- **Q:** Pagination? → **A:** Single `recordsize=100` fetch per refresh, replace-cache. No UI pagination.
- **Q:** Tap-to-thread / pick-principal behavior in Phase 1? → **A:** Snackbar stub, not silent no-op.
- **Q:** Empty-state CTA copy? → **A:** "Start a conversation".

## Future phases (sketch — not in scope)

- **Phase 2:** `ChatThreadScreen` reading `Chat/ReadChat`, `ChatMessage` domain, `ChatMessage.sq` table keyed by `(principalId, ts)`, reverse-layout `LazyColumn`, date separators. Single-line swap in both stub `Label.ShowSnackbar(...)` paths to `Label.NavigateToThread(principalId)`.
- **Phase 3:** `sendMessage` via `Chat/SendChat`, 10-second polling loop scoped to thread visibility (cancelled in `onCleared`).
- **Phase 4:** Mark-read semantics; `isUnread` flips locally on thread open; per-row dot updates via SQLDelight flow.
- **Phase 5:** FCM/APNS push for new messages — feeds the same repository's cache, no UI changes needed.
