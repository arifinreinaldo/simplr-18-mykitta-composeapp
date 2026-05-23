---
title: Repository.kt
slug: repository
type: deep
verified: 2026-05-23
sources:
  - app/src/main/java/com/b2b/online/data/Repository.kt
  - app/src/main/java/com/b2b/online/data/RepositoryPHLocation.kt
  - app/src/main/java/com/b2b/online/data/api/ApiResult.kt
  - app/src/main/java/com/b2b/online/data/api/NetworkUtil.kt
  - app/src/main/java/com/b2b/online/data/api/ApiService.kt
---

# Repository.kt

**Repository.kt: the 1294-line facade through which all features talk to network + cache.**

## What it does
`Repository` is a single Hilt-injected class that fronts every Retrofit call and every Room DAO in the app. Each business operation — login, fetch items, send chat, redeem voucher, register with photo — is a one-shot `suspend` method that wraps a `safeApiCall { postService.X(...) }` inside the `doOnlineCall(...)` flow builder and persists results through `local.<dao>()`. ViewModels never see `ApiPostService` or `LocalDatabase` directly; they only collect `Flow<UIState<T>>` returned by Repository. A second, narrower `RepositoryPHLocation` exists for Philippines province/city/barangay lookups backed by bundled CSVs in `assets/mapping/`.

The class is annotated `@Module @InstallIn(ActivityRetainedComponent::class)` (Repository.kt:38-39) — meaning it is scoped per-activity-retained, not @Singleton, so it survives configuration changes but is rebuilt across activity recreation. State that must outlive the activity lives in `SharedPreferences` + Room, not in Repository fields.

## Where it lives
- `app/src/main/java/com/b2b/online/data/Repository.kt:40-45` — constructor injecting `ApiPostService`, `SharedPreferences`, `LocalDatabase`, `FirebaseCrashlytics`.
- `Repository.kt:64-91` — `getSupervisorRequest(...)` / `getUserRequest(...)` are the request-builders every list endpoint funnels through; they pull `SUPERVISOR` / `USER` / `PAGINATION` (default 15) out of SharedPreferences via the `read` extension.
- `Repository.kt:93-176` — `getHotItemList` / `getLastItemList` / `getSearchItemList` / `getPrincipalItemList`: canonical "fetch list → save to Room → re-emit as Flow" pattern, with a paired `getXFlow()` Room observer.
- `Repository.kt:218-258` — synchronous cart mutation surface: `insertCartData`, `deleteCartData`, `updateSelectedCartByPrincipal/Variant`. These do not hit the network and skip `doOnlineCall` entirely.
- `Repository.kt:316-358` — `getItemPromotionList` / `getItemPrincipalList` write a Triple to three DAOs (`promoDao`, `promoReqDao`, `promoBonusDao`) inside one `saveCallResult` block — promotions are the most tangled persistence path.
- `Repository.kt:366-451` — auth surface: `doLogin`, `doLoginOTP`, `doVerifyLoginOTP`, `doLogout`. `doLogout` calls `nukeTable()` (`local.clearAllTables()`) — a single line wipes every cached table.
- `Repository.kt:558-590` — `doCheckCart` / `doPostCart` are the cart-checkout pair; `doCheckCart` writes back via `cartDao().updateActive(it)` to reflect server-side availability decisions.
- `Repository.kt:620-677` — `getHistoryList` does *deletion before fetch* (lines 624-636) when `isInitial`, scoped by either `principalId` or `status` parameter — order matters: clear-then-insert, not insert-then-clear.
- `Repository.kt:727-797` — chat: `getLastChatList` and `getChatVendor` read a "last timestamp" out of Room (`chatDao().getLastChat()` / `getLastRead(principalId)`) and pass it back into the next request as `ts`. Server-driven delta sync.
- `Repository.kt:842-913` — `doRegisterWithPhoto` is the only multipart endpoint: serializes `RegisterRequest` as JSON via Gson, attaches N files as `file0`, `file1`, ... (line 888), and calls `postCustomerSignup(bodies, data)`.
- `Repository.kt:1131-1165` — `getHistoryDetailUnified` is the lone caller of `doUnifiedCall` (NetworkUtil.kt:70): tries Room first, only hits network when `data == null`.
- `Repository.kt:1225-1259` — `doCheckGroupPromotion` mutates `cartDetailDao` based on server response; large commented-out heuristic block (lines 1246-1257) hints at a recently-disabled local promotion rule.
- `app/src/main/java/com/b2b/online/data/RepositoryPHLocation.kt:59-133` — `insertCSV()` reads `assets/mapping/province.csv|city.csv|barangays.csv`, splits on `;`, strips `"`, and bulk-inserts. Silent `catch (e: Exception)` per line; bad rows just log to `TAG` and continue.
- `app/src/main/java/com/b2b/online/data/api/NetworkUtil.kt:23-44` — `safeApiCall` wraps every Retrofit call: `IOException` → `NetworkError`, `HttpException` → `OnFailure(code, parsed-body)`, anything else → `OnFailure(999, ...)`.
- `NetworkUtil.kt:46-68` — `convertToBody` first tries to parse the error body as `ErrorResponse`, then falls back to `QtyInsufficientResponse` (a domain-specific 4xx payload), then to a generic resource string.
- `NetworkUtil.kt:133-180` — `doOnlineCall` is the engine: emits `Loading` → `HasMore` → either `SuccessFromRemote` / `Expired` (on 401) / `Error`, catches at the flow level into Crashlytics, and always emits `Finish` after a 100 ms delay (line 178).
- `app/src/main/java/com/b2b/online/data/api/ApiResult.kt:6-12` — `ApiResult` sealed class: `OnSuccess` / `OnFailure(code, UITextState)` / `NetworkError`.
- `app/src/main/java/com/b2b/online/data/api/ApiService.kt:7-88` — a *separate* GET-based Retrofit interface with path-encoded `{sort}/{parameter}/{user}/{count}/{offset}`. Repository does **not** use this — it uses `ApiPostService` exclusively via `GetRequest` bodies. `ApiService` looks like a legacy/parallel surface.

## Depends on
- [[data]]
- [[room-database]]
- [[uistate-pattern]]
- [[domain]]
- [[di]]
- [[di-graph]]
- [[base]]
- [[util]]
- [[authentication]]
- [[product-catalog]]
- [[shopping-cart]]
- [[orders]]
- [[chat]]
- [[address-management]]
- [[promotions]]
- [[push-notifications]]

## Depended on by
- [[address-management]]
- [[authentication]]
- [[chat]]
- [[di-graph]]
- [[localization]]
- [[orders]]
- [[product-catalog]]
- [[promotions]]
- [[push-notifications]]
- [[room-database]]
- [[shopping-cart]]
- [[uistate-pattern]]

## Gotchas
- **God-object risk is realized, not hypothetical.** 1294 lines, ~50 public methods, mixing auth, catalog, cart, orders, chat, promotions, notifications, loyalty, referrals, and CSV-backed PH locations (the last extracted into `RepositoryPHLocation`, but only that one). There is no per-feature repository split — every ViewModel injects the same `Repository`.
- **Scope is `ActivityRetainedComponent`, not `@Singleton`** (Repository.kt:39). DAO/API clients are presumably singletons elsewhere, but Repository itself is rebuilt per activity-retained lifecycle. Don't store request-coalescing or in-flight-call dedup state in fields — it will be dropped.
- **`doLogout()` calls `nukeTable()` which is `local.clearAllTables()`** (Repository.kt:313, 449). Every Room table — including PH location CSVs that were imported at first run — gets wiped on logout. Next login will re-trigger `RepositoryPHLocation.insertCSV()` cost.
- **`getHistoryList` deletes *before* fetching when `isInitial`** (Repository.kt:624-636), but only for single-parameter calls with key `principalId` or `status`. Multi-param or other-key initial loads silently skip the wipe and accumulate stale rows. Easy to break by adding a third filter.
- **`doOnlineCall` always emits `UIState.Finish` after a hard-coded `delay(100)`** (NetworkUtil.kt:177-179). `doUnifiedCall` emits `Finish` with no delay (line 129-131). Consumers that expect both to settle identically will see flicker on the unified path.
- **401 has special handling** (NetworkUtil.kt:103-105, 152-154) emitting `UIState.Expired`. Any non-empty `error.description` with `code != 200, != 401` becomes `UIState.Error` — including 200-with-error-message responses, which the server uses (the `errorData` field on `GetObjectResult`).
- **Promotion writes touch three tables atomically-ish** (Repository.kt:331-335, 353-357). There is no Room transaction wrapper — three sequential `refresh()` calls. A coroutine cancellation between calls leaves the promotion DAOs out of sync.
- **`doCheckGroupPromotion` has a large commented-out block** (Repository.kt:1246-1257) that appears to be a stubbed/disabled local-discount heuristic. Live behavior depends entirely on server response now; if the server stops returning `details[].isUpdate`, no cart discounts apply.
- **`ApiService` (the GET-path interface) is defined but unused by Repository.** Every Repository method goes through `postService: ApiPostService`. `ApiService.kt` looks like dead code or a legacy interface kept for some other caller — verify before deleting.
- **`insertCSV()` swallows per-line exceptions** (RepositoryPHLocation.kt:78-80, 100-102, 125-127). A malformed CSV row will be silently logged and skipped; no metric, no Crashlytics. Bad bundled data → silently missing provinces.
- **Two parameter-mapping conventions exist.** `getHistoryList` takes `List<Pair<String, String>>` and joins with `,` as `"key=value"` (Repository.kt:641). Every other endpoint takes a single `parameter: String` already in that format. Mixing these by mistake produces a request the server can't parse.
- **`updateProfilePicture` collects its own Flow internally** (`.collectLatest { }` at Repository.kt:839). It does not return state to the caller — fire-and-forget. Any failure is only visible in Crashlytics.
- **`getFirebaseToken()` prefers Huawei over Firebase** (Repository.kt:56-62). On dual-stack devices the FCM token is ignored. Test flows that mock only `FIRE_TOKEN` won't exercise the real selection logic.

## Open questions
- Is `ApiService.kt` actually wired into Hilt anywhere, or is it dead? Repository never references it; needs a grep across `di/` and `view-module` to confirm.
- Why is Repository `ActivityRetainedComponent`-scoped instead of `@Singleton`? With every dependency being singleton-scoped network/db, the per-activity rebuild looks like a holdover. Worth checking git history.
- `doCheckGroupPromotion`'s commented-out block (Repository.kt:1246-1257) — was that local fallback intentionally removed (server now authoritative) or is it pending re-enablement?
- The `idlingResourceName: String = "GLOBAL"` parameter on both `doOnlineCall` and `doUnifiedCall` (NetworkUtil.kt:134, 71) is accepted but never read inside the function bodies. Was an `IdlingResource` integration removed or never finished?
- `getCategoryData` writes into `categoryItemDao` keyed by `key` (Repository.kt:1066-1068), but the wipe semantics are unclear — does `inserts(it)` upsert by category or accumulate? Need to inspect `categoryItemDao` source.
