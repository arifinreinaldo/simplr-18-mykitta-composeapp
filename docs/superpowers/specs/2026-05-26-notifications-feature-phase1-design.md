# Notifications Feature — Phase 1 Design

**Date:** 2026-05-26
**Author:** reinaldo (with Claude)
**Phase:** 1 of 3 (in-app list + badge)
**Status:** Spec — pending implementation plan

## Phases (decomposition)

The full notification scope (list + badge + Android FCM + iOS APNS) was decomposed into three independently revertible phases. **This spec covers Phase 1 only.** Phases 2 and 3 will each get their own spec/plan cycle.

| Phase | Scope | Status |
|---|---|---|
| **1** | In-app notification list + unread badge (this spec) | Designing |
| 2 | Android FCM push (google-services config, MessagingService, channel, token registration) | Deferred |
| 3 | iOS APNS push (entitlement, capability, AppDelegate hooks, token bridge) | Deferred |

## Goals (Phase 1)

- Real notification list page reachable via the existing `🔔` icon on `HomeScreen` top-bar.
- Unread badge on the bell icon driven by a single source of truth that updates immediately when the user marks a notification read on the list page — without coupling `HomeScreen` to the notification feature.
- Type-aware tap routing for the two known notification types (`Principal`, `Order`); graceful "Coming soon" fallback for `Order` and any unknown type until those detail screens exist.
- Offline-mirror semantics: SQLDelight cache shown when network fails for first page; deeper pages surface the network error.

## Non-goals (deferred to later phases)

- No system push (FCM / APNS) — Phases 2 & 3.
- No "Mark all as read" bulk action.
- No pull-to-refresh on the list screen (re-opening the screen already forces a fresh fetch).
- No Compose UI tests (project has none today; manual verification covers the UI).
- No notification-detail screen for `Order` or other unknown types — they toast "Coming soon".

## Current state (what's already there)

- `HomeScreen.kt:103-109` already renders a `BadgedBox` around the `🔔` icon and reads `state.notifCount`.
- `HomeStore.State.notifCount` exists.
- `HomeRepository.loadNotificationCount()` (`HomeRepository.kt:72-75`) already calls `CatalogApi.getNotificationCount(...)` (`CatalogApi.kt:31`) and returns `Outcome<Int>`. DTO `NotifCountServerResponse` is in `CatalogDtos.kt:131-138`.
- `HomeStore`'s bootstrapper fires this once at startup (`HomeStore.kt:166-170`) and never again.
- `MainShell.kt:70` passes `onOpenNotifications = { /* later phase */ }` — a no-op.

The list side, mark-read endpoint, SQLDelight cache, refresh-on-tab-switch, reactive count flow, and nav destination are all missing.

## Decisions (from clarifying questions)

| # | Question | Decision |
|---|---|---|
| 1 | Pagination | Infinite scroll, offset-paginated; page size = 20 |
| 2 | Mark-read trigger | On tap only; no bulk action |
| 3 | Tap routing for unsupported types | `Principal` → existing `MainTab.PrincipalCatalog`; `Order` / unknown → toast "Coming soon" (still mark-read) |
| 4 | Unread badge source | Dedicated `GetNotificationCount` endpoint via `NotificationRepository.unreadCount: StateFlow<Int>`; refresh on Home tab-switch only (not on app foreground) |
| 5 | Cache TTL | Always-fetch on screen open; SQLDelight as offline mirror only |
| 6 | Architecture | Repository owns `unreadCount` StateFlow (SSOT); `NotificationStore` for the list; `HomeStore` subscribes to repository's StateFlow directly |
| 7 | Pagination state location | `NotificationStore` owns `offset`/`endReached`/`loadingMore`; repository's `loadPage(offset)` is stateless |
| 8 | Mark-read endpoint shape | (Was deferred; resolved by wiki — see "Wiki-driven revisions" below) |

## Wiki-driven revisions

Consulting `llm_wiki/deep/push-notifications.md`, `llm_wiki/deep/repository.md:144-187`, and `llm_wiki/features/orders.md` surfaced five diffs vs. the initial design. Two are load-bearing; three are confirmations.

| # | Diff | Impact | Resolution |
|---|---|---|---|
| 1 | Mark-read endpoint is `POST Notification/ReadNotification` with body `{NotifID: "<id>"}` — **not** routed through `User/GetObject` | Existing `KtorCatalogApi.call<R>` hardcodes `User/GetObject` (`CatalogApi.kt:39`); needs a sibling helper for arbitrary paths | Add `callPath<R>(baseUrl, path, body)` helper; `markNotificationRead` uses it with path `Notification/ReadNotification`. DTO has only `NotifID` (no `User` field). |
| 2 | Legacy backend's `hasMoreRecords` is unreliable; legacy client forced it to `true` | Trusting the field could pin `endReached` permanently to `true` (always-load-more) or `false` (never-load-more) | Drop `hasMore()` from response wrapper. `endReached` signal is exclusively "page returned fewer items than `pageSize`". |
| 3 | Principal tap requires a lookup: payload contains `principalId` only; `principalName` must be resolved from local cache | My initial design assumed payload carried both | `NotificationStore.Label.NavigateToPrincipal(principalId, principalName)` is published only after a successful `PrincipalRepository.findById(...)` lookup in the executor. Miss → `Label.NavigateUnsupportedType` + snackbar "Brand not available". `NotificationViewModel` gains a `PrincipalRepository` constructor dependency. |
| 4 | Legacy `Notif` domain has `payload` AND `payload_data` (parsed) | Two representations of the same data | **Keep `payload: String` only.** Parse on tap-time in the executor. Simpler. |
| 5 | Legacy `notifID: Int`; mark-read sends `notifID.toString()` | Type choice at our domain boundary | **Keep `id: String`** end-to-end. More forgiving (alphanumeric / future overflow safe); wire format is string regardless. |

## Architecture overview

```
┌────────────────────────────────────────────────────────────────────┐
│ HomeScreen (existing)                                              │
│   • renders 🔔 badge from HomeStore.State.notifCount               │
│   • on Home tab-switch dispatches HomeStore.Intent.RefreshNotifications │
└────────────┬───────────────────────────────────────────────────────┘
             │ subscribe to unreadCount: StateFlow
             ▼
┌────────────────────────────────────────────────────────────────────┐
│ NotificationRepository (Koin single — SSOT for unread count)       │
│   val unreadCount: StateFlow<Int>                                  │
│   suspend fun refreshCount(): Outcome<Int>                         │
│   suspend fun loadPage(offset: Int): Outcome<NotificationPage>     │
│   suspend fun markAsRead(id: String): Outcome<Unit>                │
└────────────▲───────────────────────────────────────────────────────┘
             │ tap → markAsRead (decrements unreadCount internally)
             │ scroll-to-end → loadPage(offset)
             │
┌────────────┴───────────────────────────────────────────────────────┐
│ NotificationStore (screen-scoped via ScreenViewModel)              │
│   state: items, offset, endReached, firstLoadInFlight,             │
│          loadingMore, error, showingCache                          │
│   intents: LoadNextPage, Refresh, TapItem, DismissError            │
│   labels: NavigateToPrincipal, NavigateUnsupportedType, ShowSnackbar│
└────────────────────────────────────────────────────────────────────┘
```

Key invariant: **`NotificationScreen` never imports `HomeStore`, and `HomeScreen` never imports `NotificationStore`.** Both consume the repository's `StateFlow`. The repository is the only shared mutable state.

### Pagination state lives in the Store, not the Repository

Three options were considered (Store-owned cursor / Repository-owned cursor / Paging library). Store-owned wins because:
- The repository is a Koin `single` — holding accumulated pages there leaks memory across screen lifetimes.
- "Always-fetch on screen open" (decision #5) explicitly says don't preserve pagination state across screen lifetimes. A repository-held cursor preserves exactly what we said not to preserve.
- The repository's only persistent state across screens is `unreadCount` — deliberately, because the *badge* needs to be reactive across screens; the list does not.
- Pure functions on the repository are trivial to test.

### Nav topology

- `Destination.Notifications` is a new top-level destination on the outer `AppNavHost`, sibling to `Search` and `ProfileDetail`. Bottom-nav hides while the user is on it — matches the `ProfileDetail` pattern.
- Outer NavController owns notifications; inner NavController (in `MainShell`) owns the bottom-nav tabs. Tapping a `Principal` notification needs a cross-NavController deep-link: pop `Notifications` and switch the inner NavController to the Principal tab + push `MainTab.PrincipalCatalog`. See "Open issue: cross-NavController deep-link" below.

## Detailed design

### Files touched / added

```
shared/src/commonMain/kotlin/com/simplr/mykitta2/
  domain/Notification.kt                                 NEW
  data/net/dto/NotificationDtos.kt                       NEW
  data/net/dto/CatalogDtos.kt                            (unchanged — count DTO already there)
  data/net/api/CatalogApi.kt                             EDIT — add getNotificationList, markNotificationRead, callPath helper
  data/repo/NotificationRepository.kt                    NEW
  data/repo/HomeRepository.kt                            EDIT — drop loadNotificationCount + count CatalogApi call
  data/repo/LocalDataWiper.kt                            EDIT — wipe Notification table on logout
  feature/notification/NotificationStore.kt              NEW
  feature/notification/NotificationViewModel.kt          NEW
  feature/notification/NotificationScreen.kt             NEW
  feature/home/HomeStore.kt                              EDIT — subscribe to NotificationRepository.unreadCount + add RefreshNotifications intent + remove one-shot count load
  feature/main/MainShell.kt                              EDIT — wire onOpenNotifications callback + fire RefreshNotifications on Home tab-switch
  ui/nav/Destination.kt                                  EDIT — add Notifications destination
  ui/nav/AppNavHost.kt                                   EDIT — register composable + pass onOpenNotifications + cross-nav deep-link wiring
  ui/nav/PendingNavStore.kt                              NEW (small Koin single — see open issue)
  di/AppModule.kt                                        EDIT — add featureNotificationModule + NotificationRepository single + PendingNavStore single

shared/src/commonMain/sqldelight/com/simplr/mykitta2/shared/db/
  Notification.sq                                        NEW

shared/src/commonTest/kotlin/com/simplr/mykitta2/data/NotificationRepositoryTest.kt           NEW
shared/src/commonTest/kotlin/com/simplr/mykitta2/feature/notification/NotificationStoreTest.kt NEW
shared/src/commonTest/kotlin/com/simplr/mykitta2/feature/home/HomeStoreTest.kt                EDIT
shared/src/androidHostTest/kotlin/com/simplr/mykitta2/db/MyKittaDatabaseWiperTest.kt          EDIT
```

~18 files (11 new, 7 edits).

### Domain model

```kotlin
// shared/src/commonMain/kotlin/com/simplr/mykitta2/domain/Notification.kt
data class Notification(
    val id: String,
    val title: String,
    val description: String,
    val type: NotificationType,
    val payload: String,        // raw JSON; parsed on tap, not stored as typed
    val isRead: Boolean,
    val createdAt: String,      // sortable string (ISO8601 assumed — verify before impl)
)

enum class NotificationType {
    PRINCIPAL, ORDER, UNKNOWN;
    companion object {
        fun fromWire(raw: String?): NotificationType = when (raw?.uppercase()) {
            "PRINCIPAL" -> PRINCIPAL
            "ORDER" -> ORDER
            else -> UNKNOWN
        }
    }
}
```

### DTOs (`NotificationDtos.kt`, new file)

```kotlin
// List endpoint — uses User/GetObject envelope (same path/shape as every other list call)
@Serializable
data class NotificationListServerResponse(
    @SerialName("getObjectResult") val getObjectResult: GetObjectResult<NotificationDto>,
) {
    fun items(): List<NotificationDto> = getObjectResult.objectData.firstOrNull().orEmpty()
    // NOTE: hasMoreRecords is unreliable on this endpoint per legacy. Don't expose it.
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

// Mark-read — dedicated endpoint, NOT through User/GetObject
@Serializable
data class MarkNotificationReadRequest(
    @SerialName("NotifID") val notifId: String,
)

@Serializable
data class MarkNotificationReadResponse(
    val errorData: ErrorData = ErrorData(),
)
```

### `CatalogApi` additions

```kotlin
interface CatalogApi {
    // ... existing ...
    suspend fun getNotificationList(baseUrl: String, request: GetRequest): NotificationListServerResponse
    suspend fun markNotificationRead(baseUrl: String, request: MarkNotificationReadRequest): MarkNotificationReadResponse
}

class KtorCatalogApi(private val client: HttpClient) : CatalogApi {
    // ... existing call<R>(baseUrl, request) stays unchanged ...

    /** Sibling of [call] for endpoints not routed through `User/GetObject`. */
    private suspend inline fun <reified Body : Any, reified R> callPath(
        baseUrl: String,
        path: List<String>,
        body: Body,
    ): R {
        val url = URLBuilder().takeFrom(baseUrl).appendPathSegments(*path.toTypedArray()).build()
        return client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()
    }

    override suspend fun getNotificationList(baseUrl: String, request: GetRequest) =
        call<NotificationListServerResponse>(baseUrl, request)

    override suspend fun markNotificationRead(baseUrl: String, request: MarkNotificationReadRequest) =
        callPath<MarkNotificationReadRequest, MarkNotificationReadResponse>(
            baseUrl, listOf("Notification", "ReadNotification"), request,
        )
}
```

### SQLDelight schema (`Notification.sq`, new file)

```sql
-- User-scoped notification cache. Refreshed from `GetNotificationData` (offset=0
-- only). Used as offline mirror only — NotificationRepository.loadPage prefers
-- network; cache reads kick in when network fails for offset=0.
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

**Open assumption:** `createdAt` is lexically sortable (ISO8601 like `"2026-05-26T14:32:00Z"`). To verify before implementation. If the backend returns a regional format (`"26/05/2026 14:32"`) we'll either negotiate ISO with the backend or add an `epochMs INTEGER` derived column populated at upsert.

### `NotificationRepository`

```kotlin
interface NotificationRepository {
    val unreadCount: StateFlow<Int>
    suspend fun refreshCount(): Outcome<Int>
    suspend fun loadPage(offset: Int): Outcome<NotificationPage>
    suspend fun markAsRead(id: String): Outcome<Unit>
}

data class NotificationPage(
    val items: List<Notification>,
    val hasMore: Boolean,             // computed: items.size >= PAGE_SIZE
    val fromCache: Boolean = false,
)

private const val PAGE_SIZE = 20

class DefaultNotificationRepository(
    private val catalogApi: CatalogApi,
    private val sessionStore: SessionStore,
    private val countryStore: CountryStore,
    private val db: MyKittaDatabase,
) : NotificationRepository {

    private val _unreadCount = MutableStateFlow(0)
    override val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    override suspend fun refreshCount(): Outcome<Int> = runCall {
        val count = catalogApi
            .getNotificationCount(baseUrl(), supervisorRequest("GetNotificationCount"))
            .count()
        _unreadCount.value = count
        count
    }

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
                hasMore = items.size >= PAGE_SIZE,   // server's hasMoreRecords is unreliable; size-short is truth
                fromCache = false,
            )
        }
        return if (networkOutcome is Outcome.Failure && offset == 0) {
            val cached = readCacheFirstPage()
            if (cached.isNotEmpty()) {
                Outcome.Success(NotificationPage(items = cached, hasMore = false, fromCache = true))
            } else {
                networkOutcome
            }
        } else networkOutcome
    }

    override suspend fun markAsRead(id: String): Outcome<Unit> = runCall {
        catalogApi.markNotificationRead(baseUrl(), MarkNotificationReadRequest(notifId = id))
        db.notificationQueries.markRead(id)
        _unreadCount.update { (it - 1).coerceAtLeast(0) }
    }

    private fun upsertCache(items: List<Notification>) {
        db.notificationQueries.transaction {
            items.forEach {
                db.notificationQueries.upsert(
                    id = it.id, title = it.title, description = it.description,
                    type = it.type.name, payload = it.payload,
                    isRead = if (it.isRead) 1 else 0, createdAt = it.createdAt,
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

    private suspend fun baseUrl(): String =
        BuildEnv.baseUrlFor(countryStore.read() ?: Country.PH)

    private suspend fun supervisorRequest(
        functionName: String, parameter: String = "all", sort: String = "0", offset: Int = 0,
    ) = GetRequest(
        functionName = functionName,
        offset = offset,
        recordsize = sessionStore.pagination(),
        search = parameter, sort = sort,
        user = sessionStore.read()?.supervisorCode ?: FALLBACK_USER,
    )

    private inline fun <T> runCall(block: () -> T): Outcome<T> = try {
        Outcome.Success(block())
    } catch (t: Throwable) {
        Outcome.Failure(ErrorMapper.from(t))
    }

    private companion object { const val FALLBACK_USER = "M1" }
}
```

### `LocalDataWiper` extension

`MyKittaDatabaseWiper.wipeAll()` adds `notificationQueries.deleteAll()` inside the existing transaction. Per `CLAUDE.md`'s standing rule: "Add a new `deleteAll()` call inside `MyKittaDatabaseWiper` when a new `.sq` table lands."

### `NotificationStore`

```kotlin
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
    fun create(): NotificationStore = object : NotificationStore,
        Store<NotificationStore.Intent, NotificationStore.State, NotificationStore.Label>
        by storeFactory.create(
            name = "NotificationStore",
            initialState = NotificationStore.State(),
            bootstrapper = BootstrapperImpl(),
            executorFactory = { ExecutorImpl() },
            reducer = ReducerImpl,
        ) {}
    // ... Action, Message, BootstrapperImpl, ExecutorImpl, ReducerImpl mirror HomeStoreFactory shape
}

class NotificationViewModel(factory: NotificationStoreFactory) :
    ScreenViewModel<NotificationStore.Intent, NotificationStore.State, NotificationStore.Label>(factory.create())
```

(Mirrors the `HomeStore` / `HomeStoreFactory` / `HomeViewModel` triplet pattern verbatim — see `feature/home/HomeStore.kt:52-65` for the canonical shape.)

**Tap flow:**
1. `TapItem(notif)` → executor launches:
   - `repository.markAsRead(notif.id)` — fire-and-observe; failure → snackbar but still navigate (fail-open).
   - Dispatch `Message.MarkedRead(notif.id)` so reducer flips the row's `isRead=true` locally.
   - Type dispatch:
     - `PRINCIPAL` → parse `payload` for `principalId`; call `principalRepository.findById(principalId)`:
       - Hit → `Label.NavigateToPrincipal(principalId, principal.name)`.
       - Miss → `Label.NavigateUnsupportedType` + `Label.ShowSnackbar("Brand not available")`.
     - `ORDER`, `UNKNOWN` → `Label.NavigateUnsupportedType`.

**Pagination flow:**
- Bootstrapper dispatches `Action.LoadFirstPage` → `loadPage(0)`.
- `LoadNextPage` intent → early-return if `loadingMore || endReached`; else `loadPage(state().offset)`.
- Page success → reducer appends + advances offset; if `!page.hasMore` sets `endReached=true`.
- Page failure → `loadingMore=false`; `error` set; items retained.
- `Refresh` → resets state to `State()` then dispatches `LoadFirstPage` action.

### `HomeStore` modifications

```kotlin
class HomeStoreFactory(
    private val storeFactory: StoreFactory,
    private val homeRepository: HomeRepository,
    private val notificationRepository: NotificationRepository,    // NEW dep
) { ... }

// In BootstrapperImpl.invoke:
scope.launch {
    notificationRepository.unreadCount.collect { count ->
        dispatch(Action.NotifCountObserved(count))
    }
}
dispatch(Action.LoadAll)
dispatch(Action.RefreshNotifCount)    // initial refresh — replaces the old one-shot call

// New Action variants:
data object RefreshNotifCount : Action
data class NotifCountObserved(val count: Int) : Action

// In executeAction:
Action.NotifCountObserved -> dispatch(Message.NotifCountLoaded(action.count))
Action.RefreshNotifCount -> scope.launch { notificationRepository.refreshCount() /* failure silently ignored — badge stays at prior value */ }

// New Intent:
data object RefreshNotifications : Intent

// In executeIntent:
HomeStore.Intent.RefreshNotifications -> dispatch(Action.RefreshNotifCount)
```

The existing `scope.launch { ... homeRepository.loadNotificationCount() ... }` at `HomeStore.kt:166-170` is **removed**. `HomeRepository.loadNotificationCount()` (and its `CatalogApi.getNotificationCount` call) is also removed — that's now `NotificationRepository`'s job.

### `MainShell` modifications

- New parameter `onOpenNotifications: () -> Unit = {}`; pass to `HomeScreen` (replacing the no-op).
- Hoist `HomeViewModel: HomeViewModel = koinViewModel()` to `MainShell` so the bottom-bar can dispatch `RefreshNotifications` when the Home tab is selected. Pass `state` and `accept` down to `HomeScreen` (one-line plumbing).
- `MainBottomBar` Home tab `onClick`:
  ```kotlin
  val wasAlreadyOnHome = currentDest?.hasRoute<MainTab.Home>() == true
  navController.switchTab(MainTab.Home)
  if (!wasAlreadyOnHome) homeViewModel.accept(HomeStore.Intent.RefreshNotifications)
  ```
- First-render guard: in `MainShell`, use a `rememberSaveable` boolean (`hasFiredInitialRefresh`) to prevent the first composition from re-firing `RefreshNotifications` (bootstrapper already did it).

### Nav wiring

**`Destination.kt`** — add:
```kotlin
@Serializable
data object Notifications : Destination
```

**`AppNavHost.kt`** — add composable + pass callback down:
```kotlin
composable<Destination.Home> {
    MainShell(
        onOpenSearch = { navController.navigate(Destination.Search) },
        onOpenProfileDetail = { navController.navigate(Destination.ProfileDetail) },
        onOpenNotifications = { navController.navigate(Destination.Notifications) },   // NEW
        onLogout = { /* unchanged */ },
    )
}

composable<Destination.Notifications> {
    NotificationScreen(
        onBack = { navController.popBackStack() },
        onOpenPrincipal = { principalId, principalName ->
            // Hand off to MainShell via PendingNavStore — see "cross-NavController deep-link"
            pendingNavStore.requestPrincipalCatalog(principalId, principalName)
            navController.popBackStack()
        },
    )
}
```

### Cross-NavController deep-link (`PendingNavStore`)

`NotificationScreen` lives on the outer `AppNavHost`. `MainTab.PrincipalCatalog` lives on the inner `MainShell` NavController. Tapping a `PRINCIPAL` notification needs to (a) pop `Notifications`, (b) switch the inner controller to the `Principal` tab, (c) push `MainTab.PrincipalCatalog(principalId, principalName)`.

Solution: a tiny Koin `single`:

```kotlin
class PendingNavStore {
    private val _pendingPrincipal = MutableStateFlow<PendingPrincipal?>(null)
    val pendingPrincipal: StateFlow<PendingPrincipal?> = _pendingPrincipal.asStateFlow()
    fun requestPrincipalCatalog(id: String, name: String) {
        _pendingPrincipal.value = PendingPrincipal(id, name)
    }
    fun consume() { _pendingPrincipal.value = null }
    data class PendingPrincipal(val principalId: String, val principalName: String)
}
```

In `MainShell`:
```kotlin
val pending by pendingNavStore.pendingPrincipal.collectAsState()
LaunchedEffect(pending) {
    pending?.let {
        tabNavController.switchTab(MainTab.Principal)
        tabNavController.navigate(MainTab.PrincipalCatalog(it.principalId, it.principalName))
        pendingNavStore.consume()
    }
}
```

Reusable for future Phase 2 push deep-links (FCM message → `pendingNavStore.requestX(...)` → same consumer).

### `NotificationScreen` (UI)

`MyKittaScaffold(title = "Notifications", onBack = onBack)` wraps a `LazyColumn` with:

- Optional offline-cache banner (when `state.showingCache`).
- One `NotificationRow` per item. Unread visual signals (three of them, for theme + a11y resilience):
  - Filled primary-color leading dot (8.dp).
  - Title weight `SemiBold` (vs. `Normal` for read).
  - Background `surfaceVariant` (vs. `surface`).
- Bottom spinner (when `loadingMore`).
- "You're all caught up" footer (when `endReached && items.isNotEmpty()`).

Empty state: centered `🔔` glyph + "No notifications yet" + "When you have notifications, they'll show up here."

Full-screen loader: single centered `CircularProgressIndicator` while `firstLoadInFlight`.

Infinite scroll trigger: `snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }` collected in a `LaunchedEffect`; fires `Intent.LoadNextPage` when last visible index ≥ `items.lastIndex - 3`.

Label observer (in `LaunchedEffect(viewModel)`):
- `NavigateToPrincipal` → invokes `onOpenPrincipal(id, name)` callback.
- `NavigateUnsupportedType` → snackbar "Coming soon".
- `ShowSnackbar` → snackbar with the supplied text.

State.error observer (separate `LaunchedEffect(state.error)`) shows error via snackbar — same pattern as `HomeScreen.kt:92-94`.

### Koin wiring (`AppModule.kt`)

```kotlin
// New module
fun featureNotificationModule() = module {
    single { PendingNavStore() }
    single<NotificationRepository> {
        DefaultNotificationRepository(get(), get(), get(), get())
    }
    factory {
        NotificationStoreFactory(
            storeFactory = get(),
            notificationRepository = get(),
            principalRepository = get(),
        )
    }
    viewModel { NotificationViewModel(get()) }
}

// HomeStoreFactory gains NotificationRepository dependency in its existing factory binding.

fun commonModules() = listOf(
    // ... existing ...
    featureNotificationModule(),
)
```

## Testing strategy

### `NotificationRepositoryTest` (commonTest, MockEngine, fake `MyKittaDatabase`)

12 cases:
1. `refreshCount` success → `unreadCount` updates; correct URL = `User/GetObject`.
2. `refreshCount` 500 → `Failure`; `unreadCount` unchanged.
3. `loadPage(0)` 20 items → `Success`, `hasMore=true`, 20 rows in cache.
4. `loadPage(0)` 7 items → `hasMore=false`.
5. `loadPage(20)` success → cache **not** upserted (deep page).
6. `loadPage(0)` network 500 + empty cache → `Failure`.
7. `loadPage(0)` network 500 + cached rows → `Success(fromCache=true, hasMore=false)`.
8. `loadPage(20)` network 500 → `Failure` (no offline fallback).
9. `markAsRead` success → URL = `Notification/ReadNotification`; `unreadCount -= 1`; cache row `isRead=1`.
10. `markAsRead` when `unreadCount==0` → clamped to 0.
11. `markAsRead` server 500 → `Failure`; no state changes.
12. Server returns `hasMoreRecords=1` with 5 items → `hasMore=false` (server field ignored).

### `NotificationStoreTest` (commonTest, fake `NotificationRepository` + `PrincipalRepository`)

14 cases (see Section 5 of design conversation for full table). Highlights:
- Bootstrap success / short page / network failure / cache fallback.
- `LoadNextPage` idempotency (no-op while loading or at end).
- `TapItem(PRINCIPAL)` cache hit → `NavigateToPrincipal` label; cache miss → `NavigateUnsupportedType` + snackbar.
- `TapItem(ORDER)` / `(UNKNOWN)` → `NavigateUnsupportedType`; still calls `markAsRead`.
- `TapItem` with mark-read failure → label still published (fail-open); snackbar also fired.
- `Refresh` resets state and re-fetches page 0.

### `HomeStoreTest` (edit existing)

4 added cases:
- H1: bootstrap → `state.notifCount` matches fake repository's `unreadCount.value`.
- H2: fake's `unreadCount` flow emits new value mid-test → `state.notifCount` updates without intent.
- H3: `Intent.RefreshNotifications` triggers `notificationRepository.refreshCount()` exactly once.
- H4: regression — existing banners/rails/points cases still pass.

### `MyKittaDatabaseWiperTest` (edit existing, androidHostTest)

W1: After `wipeAll()`, `Notification` table has 0 rows (seed 3 first).

### Out of test scope (manual verification)

- Compose UI rendering — no Compose-test harness in project today.
- Nav graph registration — exercised via end-to-end manual test.
- Cross-NavController deep-link — manual verification on both Android + iOS.

### Manual verification checklist (post-implementation)

Run on Android (Pixel emulator, devDebug) AND iOS (Simulator, Debug):

- [ ] Cold launch → Home shows badge with unread count.
- [ ] Tap 🔔 → `NotificationScreen` opens; first 20 rows render; spinner disappears.
- [ ] Scroll to bottom → spinner → next 20 rows append.
- [ ] Reach end → "You're all caught up" footer.
- [ ] Tap `PRINCIPAL` row → brand catalog opens; row visually marked read; Home badge decrements after back.
- [ ] Tap `ORDER` row → "Coming soon" toast; row marked read; Home badge decrements after back.
- [ ] Airplane mode + open with cache → cached list + offline banner.
- [ ] Airplane mode + open with empty cache → error snackbar; empty state.
- [ ] Mark-read while offline → snackbar; row stays unread; badge unchanged.
- [ ] Logout → log back in → notifications cache wiped (empty state on first open).
- [ ] Switch from another tab back to Home → count refreshes (verify via Chucker on Android).

## Open issues (deferred resolution)

1. **`createdAt` format** — assumed lexically sortable ISO8601. Verify before implementation by inspecting one live response via Chucker. If regional, add derived `epochMs` column populated at upsert.
2. **`MyKittaScaffold` snackbar slot** — needs verification that the scaffold exposes a `snackbarHost` parameter. If not, `NotificationScreen` uses plain Material3 `Scaffold` (one-off; matches no existing screen but the convention can grow from here).
3. **Mark-read response error envelope** — assumed `{errorData: {code, description}}`. If the backend returns the bare HTTP status (no body), the `MarkNotificationReadResponse` parser may error on empty body — Ktor `ContentNegotiation` behavior to confirm.
4. **Mark-read failure UX policy: fail-open** — proposal navigates anyway and shows snackbar. Alternative is fail-closed (block nav, leave row unread). Legacy fails open. Locked unless objected.

## Revertibility

Phase 1 is independently revertable:
- Removing every "NEW" file + reverting every "EDIT" returns the project to its current state.
- The only externally visible artifact is `Destination.Notifications` — easy to delete.
- No external dependency added (no Firebase, no APNS, no `google-services.json`).
- Repository singleton lifetime / SQLDelight schema add a single table — `drop table Notification` is a trivial rollback (and `MyKittaDatabaseWiper` would silently no-op a missing table on next logout if we forgot to revert that too).

Phases 2 and 3, when planned, build on top — their specs will reference this one as the foundation.
