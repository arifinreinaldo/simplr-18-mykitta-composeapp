# History feature — design

**Status:** Draft, awaiting implementation plan.
**Author:** Claude + Reinaldo, 2026-05-26.
**Scope:** First shippable cut of order history in MyKitta CMP. List-only.

## Summary

Add a paginated, status-tabbed order-history screen reachable from the Profile tab's "History" menu row. Backed by SQLDelight cache with a 5-minute TTL plus pull-to-refresh. Lives on the tab NavController under `MainTab.History` so the bottom nav stays visible while History is open.

Detail screen, reorder, principal filtering, and any cross-tab actions are explicit non-goals — see [§9 Non-goals](#9-non-goals).

## Why now

The Profile tab already advertises a `MenuItem("history", "🧾", "History")` (`ProfileScreen.kt:66`) that does nothing today — `MainShell` drops the click on the floor for `id == "history"`. The legacy app's order-history surface is one of the most-used screens for partners on flaky rural networks (the user-base mental model from `llm_wiki/features/orders.md`), so the offline-capable, cache-paint-first behavior matters.

## 1. Decisions log

| Decision | Choice | Why |
|---|---|---|
| Scope | List only (no detail, no reorder) | Smallest shippable slice. Detail/reorder need OrderDetail + promo-purchase dialog + cart write path, none of which exist today. |
| Tabs | Hardcoded enum: `WAITING`, `PROCESSED`, `ON-DELIVERY`, `FINISHED` | Type-safe, testable. Unknown server statuses are dropped defensively (legacy app's known gotcha). |
| Caching | SQLDelight `History.sq` | Instant paint, offline reads, matches `Principal.sq` pattern. UX gap vs in-memory is large for partners on slow connections. |
| Refresh | 5 min TTL + pull-to-refresh | Mirrors `ProfileRepository` 24h-TTL pattern. Lower network chatter than legacy's "refetch every onResume." |
| Nav placement | Nested on the tab NavController (`MainTab.History`) | Bottom nav stays visible, back returns to Profile. Same pattern as `MainTab.PrincipalCatalog`. |
| Store shape | One `HistoryStore` with `State = Map<OrderStatus, TabState>` | Switching tabs doesn't dispose state; no 4× Koin/intent fan-out; trivial cross-tab actions later if needed. |
| Tab fetch | Lazy — refresh on first view per tab (TTL-gated thereafter) | Conservative network; cold start is `currentTab` only. |

## 2. Architecture

```
data/net/dto/             +HistoryDtos.kt           GetHistory response; objectData[0] only
data/net/api/CatalogApi   +getHistory()             new function on existing User/GetObject endpoint
data/repo/                +HistoryRepository.kt     TTL gate; observe (Flow) + refresh + loadMore
                          ~LocalDataWiper.kt        extend MyKittaDatabaseWiper to clear History
domain/                   +Order.kt, +OrderStatus.kt
feature/history/          +HistoryStore.kt, +HistoryViewModel.kt, +HistoryScreen.kt
ui/nav/MainTab.kt         +MainTab.History
feature/main/MainShell.kt ~wire onMenuClick("history") + register composable<MainTab.History>
                          ~bottom-bar selection check (see §6 for the indicator fix)
shared/sqldelight/.../db/ +History.sq               1 table, indexed by (status, invDate)
di/AppModule.kt           +featureHistoryModule()
```

`ProfileScreen.kt` requires **no changes** — it already raises `onMenuClick("history")`; the missing wiring is in `MainShell`.

The architectural layering matches the existing Profile / Principal slices. No new platform `expect`/`actual` surfaces; everything lives in `commonMain`.

## 3. Data layer

### 3.1 Network request

Reuses the existing `GetRequest` envelope and `KtorCatalogApi`. New `CatalogApi.getHistory(...)` posts to `POST <baseUrl>/User/GetObject` with:

```kotlin
GetRequest(
    functionName = "GetHistory",
    offset = <0 for initial, currentCount for loadMore>,
    recordsize = 15,
    search = "status=<wire>",   // single-status query; joined "k=v,k=v" supported by legacy
    sort = "0",
    user = sp[C.USER] ?: "M1",
    ts = "",
    custNo = "",
    exclude = "",
)
```

`baseUrl` is resolved per call: `BuildEnv.baseUrlFor(countryStore.read())`. Bearer token attaches via the existing `Auth` plugin.

### 3.2 DTO

`shared/src/commonMain/kotlin/com/simplr/mykitta2/data/net/dto/HistoryDtos.kt`:

```kotlin
@Serializable
data class HistoryServerResponse(
    val getObjectResult: GetObjectResult<HistoryDto>,
)

@Serializable
data class HistoryDto(
    @SerialName("InvNo") val invNo: String,
    @SerialName("InvDate") val invDate: String,
    @SerialName("InvStatus") val invStatus: String,
    @SerialName("CustName") val custName: String,
    @SerialName("Total") val total: Double,
    @SerialName("Currency") val currency: String,
    @SerialName("ItemCount") val itemCount: Int,
    @SerialName("IsCancel") val isCancel: Boolean = false,
)
```

The legacy response uses `objectData[0]` for headers and `objectData[1]` for details (per `llm_wiki/deep/repository.md:101`). We consume `[0]` only.

**Risk:** Field names (`InvNo`, `InvStatus`, etc.) are inferred from the wiki narrative — the wiki doesn't include raw JSON for GetHistory. Confirm via Chucker against staging before merging. If they drift, only this DTO file changes; domain/store/UI are stable.

**Risk:** `Total` typed as `Double` because legacy Room exposes it that way. If backend emits a string, add a `kotlinx.serialization` adapter; isolated change.

### 3.3 Domain

`shared/src/commonMain/kotlin/com/simplr/mykitta2/domain/Order.kt`:

```kotlin
data class Order(
    val invNo: String,
    val invDate: String,       // raw server string; UI-layer formatting in PR2
    val status: OrderStatus,
    val custName: String,
    val total: Double,
    val currency: String,
    val itemCount: Int,
)

enum class OrderStatus(val wire: String, val label: String) {
    WAITING("Waiting", "Waiting"),
    PROCESSED("Processed", "Processed"),
    ON_DELIVERY("On-Delivery", "On Delivery"),
    FINISHED("Finished", "Finished");

    companion object {
        fun fromWire(s: String): OrderStatus? =
            entries.firstOrNull { it.wire.equals(s, ignoreCase = true) }
    }
}
```

`fromWire` is nullable so a future backend status (`"Refunded"`, `"PartialShip"`, etc.) is dropped from the list rather than mis-grouped or crashing. The legacy app crashes/misroutes on novel statuses — this is the load-bearing safeguard.

**`IsCancel` handling:** legacy overrides the displayed status to `"Cancel"` when `IsCancel=true`. We don't have a Cancel tab; cancelled rows will appear under their raw `InvStatus` (likely `FINISHED`). DTO carries the field so a future slice can layer a "Cancelled" badge or a 5th tab without re-fetching. Not in scope here.

### 3.4 SQLDelight

`shared/src/commonMain/sqldelight/com/simplr/mykitta2/shared/db/History.sq`:

```sql
CREATE TABLE History (
    invNo TEXT NOT NULL PRIMARY KEY,
    invDate TEXT NOT NULL,
    status TEXT NOT NULL,
    custName TEXT NOT NULL,
    total REAL NOT NULL,
    currency TEXT NOT NULL,
    itemCount INTEGER NOT NULL,
    fetchedAt INTEGER NOT NULL
);

CREATE INDEX history_status_date ON History(status, invDate DESC);

selectByStatus:
SELECT * FROM History WHERE status = :status ORDER BY invDate DESC, invNo DESC;

countByStatus:
SELECT COUNT(*) FROM History WHERE status = :status;

oldestFetchedAtByStatus:
SELECT MIN(fetchedAt) FROM History WHERE status = :status;

upsert:
INSERT OR REPLACE INTO History(invNo, invDate, status, custName, total, currency, itemCount, fetchedAt)
VALUES (?, ?, ?, ?, ?, ?, ?, ?);

deleteByStatus:
DELETE FROM History WHERE status = :status;

deleteAll:
DELETE FROM History;
```

`fetchedAt` is per-row (not per-tab) so the TTL check just reads `MIN(fetchedAt) WHERE status = ?`. Tab switches don't drag a stale tab's clock onto a fresh one.

### 3.5 Repository

```kotlin
interface HistoryRepository {
    fun observe(status: OrderStatus): Flow<List<Order>>
    suspend fun refresh(status: OrderStatus): Outcome<HasMore>
    suspend fun loadMore(status: OrderStatus, currentCount: Int): Outcome<HasMore>
}

data class HasMore(val hasMore: Boolean)
```

- `observe()` — straight SQLDelight `selectByStatus(status.wire).asFlow().mapToList(...)`, then `mapNotNull` rows whose status doesn't `fromWire` (defensive; would only matter if the DB row was inserted with an unknown status, which we prevent on insert).
- `refresh()` — TTL-gated. If `now - oldestFetchedAtByStatus(status) <= 5 min` and `countByStatus(status) > 0`, returns `Outcome.Success(HasMore(true))` no-op. Else fires `getHistory(offset=0)`, then transactionally `deleteByStatus(status)` + upsert the new batch with `fetchedAt = now`. Wrapped in `try/catch (t: Throwable)` funneled through `ErrorMapper.from(t)`; `CancellationException` rethrown.
- `loadMore(currentCount)` — fires `getHistory(offset = currentCount)`. Appends via `upsert` (no delete). Returns `HasMore` derived from `getObjectResult.hasMoreRecords == 1`.

Pull-to-refresh bypasses TTL by going through `refresh()` with the TTL gate explicitly skipped via a `force: Boolean` parameter on `refresh(...)`. (The interface above is the simple shape; the implementation distinguishes `refresh(force=false)` for tab-open vs `refresh(force=true)` for pull-to-refresh.) Final interface:

```kotlin
suspend fun refresh(status: OrderStatus, force: Boolean = false): Outcome<HasMore>
```

### 3.6 LocalDataWiper

Extend `MyKittaDatabaseWiper.wipeAll()` to call `historyQueries.deleteAll()` inside the existing transaction. Without this, account switching on the same device leaks the prior user's orders.

## 4. MVI

### 4.1 Shape

```kotlin
data class HistoryState(
    val currentTab: OrderStatus = OrderStatus.WAITING,
    val tabs: Map<OrderStatus, TabState> =
        OrderStatus.entries.associateWith { TabState() },
)

data class TabState(
    val orders: List<Order> = emptyList(),
    val initialLoad: Outcome<Unit> = Outcome.Idle,
    val pagination: Outcome<Unit> = Outcome.Idle,
    val hasMore: Boolean = true,
    val visited: Boolean = false,
)

sealed interface HistoryIntent {
    data class SelectTab(val status: OrderStatus) : HistoryIntent
    data object Refresh : HistoryIntent      // pull-to-refresh on current tab
    data object LoadMore : HistoryIntent     // scroll-to-end on current tab
}

sealed interface HistoryLabel {
    data class Error(val message: String) : HistoryLabel
}
```

### 4.2 Bootstrap

On store creation:
1. For each `OrderStatus`, launch a collector on `repository.observe(status)`. Each emission patches `tabs[status].orders`. These flows are cheap (lazy SQLDelight cursors), so subscribing to all four upfront has negligible cost.
2. Dispatch `SelectTab(WAITING)` to trigger the initial refresh for the default tab.

### 4.3 Intent handlers

- **`SelectTab(status)`**
  - Set `currentTab = status`. Mark `tabs[status].visited = true`.
  - Fire `repository.refresh(status, force = false)` — the repo's TTL gate decides whether it's a real network call or a no-op.
  - `tabs[status].initialLoad = Loading` until completion; `Success`/`Failure` on result. `Failure` → emit `HistoryLabel.Error(ErrorMapper.message(err))`.
- **`Refresh`**
  - Fire `repository.refresh(currentTab, force = true)`. Same state-machine as above.
- **`LoadMore`**
  - Guard: skip if `tabs[currentTab].pagination is Loading`, if `!hasMore`, or if `initialLoad !is Success`.
  - Fire `repository.loadMore(currentTab, tabs[currentTab].orders.size)`. Updates `pagination` + `hasMore` on completion.

### 4.4 Concurrency

- Refresh per-status is **latest-wins**: a second `SelectTab(status)` while a prior refresh for the same status is in flight cancels the prior. Tab-switch spam → at most one network call lands.
- `LoadMore` is opportunistically idempotent via the `pagination == Loading` guard — duplicate scroll-end events resolve to a single call.
- The four per-status `observe()` collectors run for the lifetime of the store; cancelled in `store.dispose()` via the store's `scope`.

### 4.5 ViewModel

`HistoryViewModel` is the standard `ScreenViewModel<HistoryIntent, HistoryState, HistoryLabel>` adapter. Koin: `viewModelOf(::HistoryViewModel)` inside `featureHistoryModule()`.

## 5. UI

`HistoryScreen.kt`:

```
Scaffold(
  topBar = TopAppBar("History", navigationIcon = PlatformBackButton(onBack)),
) {
  PrimaryTabRow(selectedTabIndex = state.currentTab.ordinal) {
    OrderStatus.entries.forEach { status ->
      Tab(selected = currentTab == status, onClick = { accept(SelectTab(status)) },
          text = { Text(status.label) })
    }
  }
  HorizontalPager(state = pagerState, count = 4) { page ->
    val status = OrderStatus.entries[page]
    val tab = state.tabs.getValue(status)
    PullToRefreshBox(
      isRefreshing = tab.initialLoad is Outcome.Loading,
      onRefresh = { accept(Refresh) },
    ) {
      when {
        tab.initialLoad is Outcome.Failure && tab.orders.isEmpty() -> ErrorState(...)
        tab.initialLoad is Outcome.Success && tab.orders.isEmpty() -> EmptyState(...)
        else -> LazyColumn { ... }
      }
    }
  }
}
```

- **Tab/pager sync** — bidirectional. Tab tap dispatches `SelectTab` and `pagerState.animateScrollToPage`. Pager swipe → `LaunchedEffect(pagerState.currentPage) { accept(SelectTab(OrderStatus.entries[it])) }`.
- **Infinite scroll** — `LaunchedEffect(listState) { snapshotFlow { ... } }` watches `lastVisibleItem.index >= orders.size - 3` and fires `LoadMore`. Same shape as `HomeScreen`'s carousel.
- **Status pills** on row chips derive from theme: WAITING = `surfaceVariant`, PROCESSED = `tertiaryContainer`, ON_DELIVERY = `primaryContainer`, FINISHED = `secondaryContainer`. Defensible defaults; brand-specific palette is a follow-up.
- **Labels** — `LaunchedEffect(viewModel.labels)` collects `HistoryLabel.Error` and feeds a `SnackbarHostState`. Snackbar text is already context-rich via `ErrorMapper.message`.
- **No row click** in this slice. Row card layout reserves space for a chevron but the click handler is a TODO — added when OrderDetail lands.
- **i18n** — English-only, matching the rest of the app today.

## 6. Navigation

### 6.1 Route registration

`MainTab.kt`:

```kotlin
@Serializable data object History : MainTab
```

`MainShell.kt`:

```kotlin
composable<MainTab.History> {
    HistoryScreen(onBack = { tabNavController.popBackStack() })
}

// in the onMenuClick callback:
"history" -> tabNavController.navigate(MainTab.History)
```

### 6.2 Bottom-nav selection indicator

History sits on the tab NavController as a *child* destination of the Profile tab conceptually. The current selection check is `currentDest?.hasRoute<MainTab.Profile>()` — which evaluates false on History, dropping the selected indicator entirely.

**Fix:** extend the Profile bottom-bar item's selection predicate:

```kotlin
selected = currentDest?.hasRoute<MainTab.Profile>() == true ||
           currentDest?.hasRoute<MainTab.History>() == true,
```

**Same bug exists today for `MainTab.PrincipalCatalog` under Principal.** Bundling the analogous fix into this PR is cheap and avoids leaving the codebase with a known half-fixed pattern:

```kotlin
selected = currentDest?.hasRoute<MainTab.Principal>() == true ||
           currentDest?.hasRoute<MainTab.PrincipalCatalog>() == true,
```

### 6.3 Back-stack behavior

- Back from History pops to Profile. Switching to Home then back to Profile via the bottom nav restores the History view via existing `saveState` / `restoreState` plumbing in `switchTab`.
- System back gesture on History → popBackStack. On Profile root → exits app (the auth back-stack was already dropped by the OTP→Home navigate).

## 7. Logout & error handling

- **`LocalDataWiper`** — `MyKittaDatabaseWiper.wipeAll()` adds `historyQueries.deleteAll()` inside the existing transaction.
- **Error mapping** — `HistoryRepository` funnels Ktor throwables through `ErrorMapper.from(throwable)`. `CancellationException` rethrown. 401 propagates as `AppError.Unauthorized` (de-facto session expiry signal; no automatic logout — same as the rest of the app).
- **No new `AppError` cases** required — the existing taxonomy covers network/server/unauthorized.

## 8. Tests

Land in `shared/src/androidHostTest/` (JVM SQLite available) and `shared/src/commonTest/` for store logic that doesn't need a DB.

| Suite | Test | What it proves |
|---|---|---|
| `HistoryRepositoryTest` | `refreshClearsAndUpserts` | `refresh(WAITING, force=true)` wipes WAITING rows and inserts the fetched batch transactionally. |
| `HistoryRepositoryTest` | `refreshHonorsTtlWhenNotForced` | Second `refresh(force=false)` within 5 min issues no MockEngine call. |
| `HistoryRepositoryTest` | `refreshBypassesTtlWhenForced` | `refresh(force=true)` always hits the network. |
| `HistoryRepositoryTest` | `loadMoreAppends` | `loadMore(status, 15)` appends rows; existing rows preserved; correct `offset` in request body. |
| `HistoryRepositoryTest` | `unknownStatusFiltered` | Domain row with `InvStatus = "Refunded"` is dropped from `observe()`'s output. |
| `HistoryRepositoryTest` | `networkFailureReturnsFailure` | MockEngine `respondError(500)` → `Outcome.Failure(AppError.Server)`. |
| `HistoryStoreTest` | `selectTabFetchesOnce` | First `SelectTab(WAITING)` triggers refresh; second within TTL is a no-op. |
| `HistoryStoreTest` | `loadMoreGuardedWhileLoading` | Two rapid `LoadMore` intents fire only one network call. |
| `HistoryStoreTest` | `errorBubblesAsLabel` | A `Failure` from refresh emits a `HistoryLabel.Error` with the mapped message. |
| `DatabaseWiperTest` | `includesHistory` | Extend existing wiper test — `wipeAll()` empties History. |

Out of test scope: `PullToRefreshBox` visual behavior, `PrimaryTabRow` scroll-into-view, `HorizontalPager` swipe — Compose-UI tests are not worth wiring for this slice.

## 9. Non-goals

- **OrderDetail screen.** Separate spec; depends on invoice grouping logic + payment breakdown.
- **Reorder flow.** Requires OrderDetail + promo-purchase dialog + cart write path.
- **Filter by principal.** Legacy `OrderFragment` supports a `principalId` arg; no caller in this slice.
- **Dead filter button.** Legacy ships an unwired filter button; we don't reproduce it.
- **`onResume` refresh.** TTL handles it.
- **i18n** for tab labels and empty/error copy.
- **Cart-success → History deep link.** Cart doesn't exist yet.
- **Cross-tab actions** ("mark all read" etc.). State map already supports them; nothing to wire in this slice.

## 10. Risks & open items

| # | Risk | Mitigation |
|---|---|---|
| 1 | Server status string drift — backend changes "Finished" to "Completed". | `fromWire` is `equalsIgnoreCase`; novel statuses drop silently. **Verify the 4 strings via Chucker against staging before merge.** |
| 2 | DTO field names inferred from wiki, not raw JSON. | Confirm via Chucker; only `HistoryDtos.kt` changes if drift. |
| 3 | `Total` numeric type — server may send string. | If hit, add `kotlinx.serialization` adapter; isolated. |
| 4 | `MyKittaDatabaseWiper` extension is easy to forget when adding tables. | `DatabaseWiperTest.includesHistory` is the regression guard. |

Country-scoping is N/A: `CountryStore` is logout-only (no mid-session toggle), and logout already wipes the History table.

## 11. Implementation order (sketch)

The implementation plan (writing-plans) will own the actual sequencing. Rough order:

1. `History.sq` + SQLDelight regen + the new `HistoryQueries` consumer in `DatabaseFactory`.
2. `domain/Order.kt`, `domain/OrderStatus.kt`.
3. `data/net/dto/HistoryDtos.kt`.
4. `CatalogApi.getHistory(...)` + Ktor impl.
5. `HistoryRepository` + tests (TTL, loadMore, ErrorMapper, unknown-status filter).
6. `LocalDataWiper` extension + `DatabaseWiperTest.includesHistory`.
7. `HistoryStore` + `HistoryViewModel` + store tests (single-fetch, guards, labels).
8. `HistoryScreen` + Koin `featureHistoryModule()`.
9. `MainTab.History` + `MainShell` wiring + bottom-bar selection fix (and the analogous `PrincipalCatalog` fix bundled in).
10. Smoke test on Android dev flavor + iOS sim. Verify Chucker against staging to confirm DTO field names + status strings.
