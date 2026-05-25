---
title: Repository.kt
slug: repository
type: deep
verified: 2026-05-25
sources:
  - app/src/main/java/com/b2b/online/data/Repository.kt
  - app/src/main/java/com/b2b/online/data/RepositoryPHLocation.kt
  - app/src/main/java/com/b2b/online/data/api/ApiResult.kt
  - app/src/main/java/com/b2b/online/data/api/NetworkUtil.kt
  - app/src/main/java/com/b2b/online/data/api/ApiService.kt
  - app/src/main/java/com/b2b/online/data/api/ApiPostService.kt
  - app/src/main/java/com/b2b/online/data/api/request/GetRequest.kt
  - app/src/main/java/com/b2b/online/data/api/request/LoginRequest.kt
  - app/src/main/java/com/b2b/online/data/api/request/SendOTPRequest.kt
  - app/src/main/java/com/b2b/online/data/api/request/VerifyOTPRequest.kt
  - app/src/main/java/com/b2b/online/data/api/request/RegisterRequest.kt
  - app/src/main/java/com/b2b/online/data/api/request/CartRequest.kt
  - app/src/main/java/com/b2b/online/data/api/request/CartGroupPromotionRequest.kt
  - app/src/main/java/com/b2b/online/data/api/request/VendorPickerRequest.kt
  - app/src/main/java/com/b2b/online/data/api/request/AddressRequest.kt
  - app/src/main/java/com/b2b/online/data/api/request/ChatRequest.kt
  - app/src/main/java/com/b2b/online/data/api/request/NotificationRequest.kt
  - app/src/main/java/com/b2b/online/data/api/request/ReferralRequest.kt
  - app/src/main/java/com/b2b/online/data/api/request/RegisterPartnerRequest.kt
  - app/src/main/java/com/b2b/online/data/api/request/CustomerImageRequest.kt
  - app/src/main/java/com/b2b/online/data/api/request/VoucherRequest.kt
  - app/src/main/java/com/b2b/online/data/api/response/BaseResponse.kt
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

## API surface

Every Repository network call goes through `ApiPostService` (POST only). Reads against the catalog/profile/history/chat/notification system funnel through the overloaded `User/GetObject` endpoint and are differentiated by a `functionName` field on the request body. Writes (auth, cart, address, chat, notification mark-read, redemption) hit dedicated endpoints.

### Request envelope — `GetRequest` (request/GetRequest.kt:9)

Almost every read shares this body:

```json
{
  "functionName": "GetItem",
  "offset": 0,
  "recordsize": 15,
  "search": "all",
  "sort": "0",
  "user": "M1",
  "ts": "",
  "CustNo": "",
  "exclude": ""
}
```

Field origins:
- `user` — `getSupervisorRequest` reads `sp[C.SUPERVISOR]`; `getUserRequest` reads `sp[C.USER]` (Repository.kt:64-91). Both default to `"M1"`.
- `recordsize` — `sp[C.PAGINATION]` default `15`. Overridden inline: `1000` for principal queries (Repository.kt:189, 207, 1192), `9999` for vendor chat (770), `10` for suggestions (1085).
- `ts` — chat-only delta cursor (Repository.kt:732, 760).
- `exclude` — used by suggestions: `exclude="Code=$itemNo"` (Repository.kt:1089).
- `search` — single string `"all"` or filter expression `"key=value,key=value"`; `getHistoryList` is the only caller that takes a `List<Pair<String,String>>` and joins it itself (Repository.kt:641).

### Response envelope — `GetObjectResult<T>` (response/BaseResponse.kt:8)

```json
{
  "getObjectResult": {
    "errorData": { "code": 0, "description": "" },
    "hasMoreRecords": 1,
    "objectData": [ [ ... ] ]
  }
}
```

`objectData` is `List<List<T>>`. Almost all endpoints use `[0]` for rows. **History uses `[0]` for headers and `[1]` for details** (HistoryServerResponse.kt:119-130). Empty `errorData.description` or `code == 200` is success; `code == 401` short-circuits to `UIState.Expired`; anything else becomes `UIState.Error`.

### Repository functions by feature

Auth & session (dedicated endpoints):

| Repo fn | Endpoint | Request body | Response | Side-effect |
|---|---|---|---|---|
| `doLogin(username, password)` Repository.kt:366 | `POST Account/Login` | `LoginRequest{ Password, UserName, Firebase_Token }` | `LoginServerResponse` | `sp.setSession(Session(SupervisorCode, token, IsSupervisor=="True", userName))` |
| `doLoginOTP(username, countryCode)` :392 | `POST Account/LoginOTP` | `LoginOTPRequest{ userId, country }` | `MessageServerResponse` | none |
| `doVerifyLoginOTP(username, otp)` :409 | `POST Account/VerifyLoginOTP` | `VerifyOTPRequest{ otp, userId, firebase_token }` | `LoginServerResponse` | session write |
| `doCallOTP(username, country)` :453 | `POST Account/SendOTP` | `SendOTPRequest{ userId, country }` | `MessageServerResponse` | none |
| `doVerifyOTP(username, otp)` :472 | `POST Account/VerifyOTP` | `VerifyOTPRequest{ otp, userId }` (no token) | `MessageServerResponse` | **no session write** — signup-flow OTP |
| `doLogout()` :435 | `POST User/SignOut` | none | none | `sp.clearSession()` + `local.clearAllTables()` |
| `doRegister(outlet…, contact…, taxNumber, outlet PH-address fields)` :491 | `POST Account/Signup` | `RegisterRequest` (no photo, no fcm token) | `LoginServerResponse` | session written with `contactPhone` as code/username |
| `doRegisterWithPhoto(…, cameraFile: List<File>, lat, lng, referralCode)` :842 | `POST Account/CustomerSignup` (multipart) | parts `file0…fileN` (JPEG) + `data` = JSON `RegisterRequest{ …, firebase_token, store_lat, store_long, referral }` | `LoginServerResponse` | session with `IsSupervisor=true` hard-coded |
| `doRegisterReferral(partnerName, phone, email, supervisorCode, otp)` :984 | `POST Register/ReferralSave` | `RegisterPartnerRequest{ partnerName, phone, email, supervisor_code, firebase_Token, OTP }` | `LoginServerResponse` | session with `supervisor=false` |
| `doPrincipalSubmit(principal: List<PrincipalPicker>)` :536 | `POST User/RequestPrincipal` | `List<VendorPickerRequestItem{ principalId }>` (bare array, not wrapped) | `MessageServerResponse` | none |
| `getReferral()` :939 | `POST Register/ReferralGet` | `ReferralRequest{ code: supervisor }` | `OTPServerResponse{ otp }` | none |
| `getReferralDetail(value)` :971 | `POST Register/ReferralShow` | `ReferralCodeRequest{ otp: value }` | `ReferralServerResponse` | none |
| `getCustomerImage()` :1014 | `POST Account/ShowImage` | `CustomerImageRequest{ user: supervisor }` | `CustomerImageServerResponse` | none |
| `updateProfilePicture(image: File)` :823 | `POST Account/FileUpload` (multipart) | `file` (JPEG) + `data` = supervisor code (text/plain) | `ProfileResponse` | none (Flow is `collectLatest {}` — fire-and-forget) |

Catalog reads (`POST User/GetObject` — differentiated by `functionName`):

| Repo fn | functionName | Saves via | Domain return |
|---|---|---|---|
| `getHotItemList(sort, parameter, offset)` :93 | `GetItem` | `hotItemDao.inserts` | `UIState<Items>` |
| `getLastItemList(sort, parameter, offset)` :114 | `GetLastOrder` | `lastItemDao.inserts` | `UIState<Items>` |
| `getSearchItemList(sort, parameter, offset, initialSearch)` :139 | `GetItem` | `searchItemDao.refresh` if `initialSearch` else `.inserts` | `UIState<Items>` |
| `getPrincipalItemList(sort, parameter, offset)` :161 | `GetItem` | `principalItemDao.inserts` | `UIState<Items>` |
| `getCategoryData(param, key)` :1052 | `param` (caller passes) | `categoryItemDao.inserts` | `UIState<Pair<String, Items>>` (key kept for routing) |
| `getSuggestedItemList(itemNo)` :1075 | `GetItemSuggestion` | none | `UIState<Items>` (recordsize=10, exclude=`Code=$itemNo`) |
| `getRedemptionItemList(sort, parameter, offset, initialSearch)` :1203 | `GetRedemptionItems` | `searchItemDao.refresh`/`.inserts` (re-uses search table) | `UIState<Items>` |
| `getPrincipalList(sort, parameter, offset)` :201 | `GetPrincipal` (recordsize=1000) | `principalDao.refresh` (full table replace) | `UIState<List<Principal>>` |
| `getPrincipal(sort, parameter, offset)` :1186 | `GetPrincipal` (recordsize=1000) | none (read-through) | `UIState<List<Principal>>` |
| `getPrincipalPicker(sort, parameter, offset)` :178 | `GetPrincipalPicker` (recordsize=1000) | `principalPickerDao.refresh` (filters `IsProcess==0` out) | `UIState<List<PrincipalPicker>>` |
| `getBannerList(sort, parameter, offset)` :288 | `GetBanner` | `bannerDao.refresh` (full replace) | `UIState<List<Banner>>` |
| `getItemImageList(sort, parameter, offset)` :597 | `GetItemImages` | `imageDao.refresh` | `UIState<List<Image>>` |
| `getProfile(sort, parameter, offset)` :708 | `GetProfile` (via `getUserRequest`) | **none** (not cached locally) | `UIState<ProfileServerResponse>` |
| `getPartnerList(offset)` :956 | `GetPartnerList` (search="", sort="0") | none (passthrough) | `UIState<PartnerServerResponse>` |
| `getConfigList()` :1032 | `GetMultiListConfig` (search="all") | `configListDao.deleteTable()` then `inserts` | `UIState<List<ConfigListEntity>>` |
| `getLoyaltyPoints()` :1261 | `GetLoyaltyPoints` (search="all") | none | `UIState<Int>` (extracted from `objectData[0][0].points`) |
| `getNotificationList(offset)` :1101 | `GetNotificationData` (search="all", sort="0") | `notifDao.inserts` | `UIState<List<Notification>>` (`hasMore` forced `true`) |
| `getNotificationCount()` :1116 | `GetNotificationCount` | none | `UIState<NotifCountEntity>` |

Promotions:

- `getItemPromotionList(sort, parameter, offset, principalId)` Repository.kt:316 — `functionName=GetItemPromotion`. Saves to **three DAOs in one `saveCallResult`**: `promoDao.refresh`, `promoReqDao.refresh`, `promoBonusDao.refresh`. No Room transaction wraps this (see Gotchas).
- `getItemPrincipalList(sort, parameter, offset, principalId)` :338 — `functionName=GetPromotion` (different from above). Same three-DAO write.
- `doCheckGroupPromotion(data: List<CartGroupPromotionDetailRequest>)` :1225 — `POST User/CheckItemCustomerPromotion`. Body: `CartGroupPromotionRequest{ details: [{productId, qty, discount, discountAmount, promoId, basePrice, subtotal, isUpdate}] }`. Response: `ItemCustomerPromotionResponse{ details }`. For each detail with non-empty `isUpdate`, calls `cartDetailDao.updateDiscount` or `updateDiscountAmount` per `productId`.

Cart (mostly local — 2 network calls):

- `doCheckCart(data: List<CartHeaderRequest>)` Repository.kt:558 — `POST User/CheckPromotion`. Body: `CartRequest{ CustAddr:"", Firebase_Token:"", cart }`. Response: `List<CheckPromotionResponseItem{ isValid, promoID }>`. Side-effect: `cartDao.updateActive(it)`. Promos starting with `PREFIX_DEF_PROMO` are forced active regardless of `isValid` (CheckPromotionResponse.kt:18-21).
- `doPostCart(data: List<CartHeaderRequest>, address: Address)` :575 — `POST User/SubmitOrder`. Body: `CartRequest{ CustAddr: address.addressId, Firebase_Token, cart }`. Response: `MessageServerResponse`.

`CartHeaderRequest` shape (request/CartRequest.kt:21):
```
{ promoId, principalId,
  details: [{productId, qty, discount, discountAmount, promoId, basePrice, subtotal}],
  bonus:   [{productId, qty, promoId}],
  subtotal, discount, gst, total }
```

History:

- `getHistoryList(sort, parameter: List<Pair<String,String>>, offset, isInitial)` Repository.kt:620 — `functionName=GetHistory` via `getUserRequest`. The `parameter` list is joined `"key=value,key=value"`. If `isInitial && parameter.size==1`, pre-deletes Room rows scoped by `principalId` or `status` (Repository.kt:624-636). Response: headers in `objectData[0]`, details in `objectData[1]` — manually re-Moshi-parsed via `adapterHistory`/`adapterHistoryDetail`. Saves both tables.
- `getHistoryDetailUnified(id)` :1131 — only caller of `doUnifiedCall`. Local-first via `historyDao.get(id)`; if null fires network with `parameter="OrderNo=$id"`. After saving, re-reads from Room for the final emit.
- `getHistoryDetail(id)` :668 — pure local via `doRoomCall` → `UIRoom<HistoryData>`.

Address / shipping:

- `getShipmentList(sort, parameter, offset)` Repository.kt:679 — `functionName=GetShipmentAddress`. Saves via `addressDao.onlineInsert(it)` **only if non-empty** (preserves locally-added rows on empty fetch).
- `updateAddress(request: AddressRequest)` :800 — `POST User/AddAddress`. Body: `AddressRequest{ customerAddressId, name, address1, address2, zipcode, city, phone, contact, Barangay, Province, Subdivision }`. Response: `MessageServerResponse`. **No local write** — caller must re-sync.
- `updateSelectedAddress(address)` :704 — local only (`addressDao.selectAddress(addressId)`).

Chat:

- `getLastChatList(sort, offset)` Repository.kt:727 — `functionName=GetChatList`, `search="all"`, `ts = chatDao.getLastChat() ?: ""`. Saves: `chatDao.clearLast()` + `chatDao.replaceData(it)`.
- `getChatVendor(principalId, sort="0", offset=0)` :753 — `POST Chat/ReadChat` (not `User/GetObject`). Hand-built `GetRequest("GetChatVendor", offset, 9999, "principalId=$principalId", sort, user, ts, CustNo=principalId)`. `ts` from `chatDao.getLastRead(principalId) ?: "0"`. Saves: `chatDao.replaceData` + `chatDao.updateRead(principalId)`.
- `sendChat(message, principal)` :915 — `POST Chat/SendChat`. Body: `ChatRequest{ chat: message, principal }`. Response: `MessageServerResponse`.

Notifications:

- `getNotificationList(offset)` / `getNotificationCount()` — see catalog-reads table above.
- `updateReadNotification(notifID: Int)` Repository.kt:1173 — `POST Notification/ReadNotification`. Body: `NotificationRequest{ NotifID: notifID.toString() }`. Side-effect: `notifDao.updateSelected(true, notifID)`.

Loyalty:

- `doPostRedeem(itemNo, qty, points)` Repository.kt:1276 — `POST User/RedeemPoints`. Body: `VoucherRequest{ custNo: salesUser, itemNo, qty, points }`. Response: `MessageServerResponse`.

Local-only readers (no network — Room Flow):

`getHotItemFlow`, `getLastItemFlow`, `getSearchFlow`, `getPrincipalItemFlow(principalId)`, `getCategoryItemDataFlow`, `getConfigFlow`, `getConfigListFlow`, `getPrincipalFlow`, `getPrincipalFlowAll`, `getPrincipalPickerFlow`, `getAllPrincipal`, `getPrincipalById(id)`, `getBannerFlow`, `getBannerFlow(id)`, `getVariantFlow(item)`, `getPromoData(promo)`, `getPromoByPrincipal(principalId)`, `getCartFlow`, `getCartById(variantId)`, `getShipmentFlow`, `getImageFlow(item)`, `getHistoryFlow(parameter)`, `getHistoryFlowByPrincipal(parameter)`, `getHistoryDetail(id)`, `getChatData`, `getChatDataByVendor(vendor)`, `getFlowUnread`, `getFlowUnreadperPrincipal(principalId)`, `getNotificationFlow`.

Local-only writers (no network): `insertCartData`, `deleteCartData`, `updateSelectedCartByPrincipal`, `updateSelectedCartByVariant`, `updatePrincipalPicker`, `deleteSearch`, `deletePrincipal(principalId)`, `deleteNotification`, `nukeTable`.

### Endpoint → callers cheat-sheet

| Endpoint | Repo function(s) |
|---|---|
| `Account/Login` | `doLogin` |
| `Account/LoginOTP` | `doLoginOTP` |
| `Account/VerifyLoginOTP` | `doVerifyLoginOTP` |
| `Account/SendOTP` | `doCallOTP` |
| `Account/VerifyOTP` | `doVerifyOTP` |
| `Account/Signup` | `doRegister` |
| `Account/CustomerSignup` *(multipart)* | `doRegisterWithPhoto` |
| `Account/FileUpload` *(multipart)* | `updateProfilePicture` |
| `Account/ShowImage` | `getCustomerImage` |
| `User/SignOut` | `doLogout` |
| `User/GetObject` | **17 reads** dispatched by `functionName` (see catalog/promo/profile/chat/history/notification/loyalty/partner/config tables) |
| `User/RequestPrincipal` | `doPrincipalSubmit` |
| `User/AddAddress` | `updateAddress` |
| `User/SubmitOrder` | `doPostCart` |
| `User/CheckPromotion` | `doCheckCart` |
| `User/CheckItemCustomerPromotion` | `doCheckGroupPromotion` |
| `User/RedeemPoints` | `doPostRedeem` |
| `Chat/SendChat` | `sendChat` |
| `Chat/ReadChat` | `getChatVendor` |
| `Notification/ReadNotification` | `updateReadNotification` |
| `Register/ReferralGet` | `getReferral` |
| `Register/ReferralShow` | `getReferralDetail` |
| `Register/ReferralSave` | `doRegisterReferral` |

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
