---
title: MyKitta Foundation — Design
slug: mykitta-foundation-design
status: approved
sub_project: 1 of N
created: 2026-05-23
owner: reinaldo
related_wiki:
  - llm_wiki/index.md
  - llm_wiki/SCHEMA.md
  - llm_wiki/deep/repository.md
  - llm_wiki/deep/uistate-pattern.md
  - llm_wiki/deep/di-graph.md
  - llm_wiki/deep/room-database.md
  - llm_wiki/deep/localization.md
---

# MyKitta Foundation — Design

## 0. Purpose & Scope

Migrate the B2B-SIMPLR Android codebase (documented in `llm_wiki/`) to a
Compose Multiplatform project targeting Android + iOS, re-architected on a
modern KMP stack while preserving 100% of the **backend contract** described in
the wiki.

This document specifies **sub-project 1**: the shared foundation **plus a
thin auth slice** that exercises the foundation against the real backend.

The auth slice is intentionally narrow: **a single combined login screen
with an embedded country switcher → real `doLoginOTP` backend call**.
WhatsApp / Telegram-style: the country chip (flag + prefix) sits above the
phone input, tappable to open a bottom sheet of supported countries (PH,
SG). Country is auto-selected from `CountryDetector` on first launch
(silent, no confirm dialog) and persisted to `CountryStore` only on
successful submit.

The slice explicitly **stops at the backend call** — no OTP verify screen,
no token issuance, no register, no Waiting screen, no SMS auto-read, no
Google phone-hint, no Huawei detection, no PH CSV. Those land in later
sub-projects.

The deliverable is: `:shared` + `:androidApp` + `iosApp/` boot, run the auth
slice end-to-end on both platforms (login screen with country chip → phone
entry → submit fires the real `doLoginOTP` against the staging backend;
success shows a placeholder "OTP sent to +XX..." screen), with all
foundation cross-cutting components in place (Ktor, SQLDelight wired, Koin,
MVIKotlin, logging, encrypted Settings, flavors).

## 1. Confirmed Inputs

- **Re-architect, not transcribe.** Stack is selected for "best CMP fit" rather than
  matching the legacy Hilt / Room / Retrofit / XML stack.
- **Backend is frozen.** Endpoints, payloads, auth flow, and error semantics
  match B2B-SIMPLR exactly. The wiki's repository surface
  (`llm_wiki/deep/repository.md`) is canonical.
- **Solo developer.** No team-coordination constraints. Rigor and observability
  are weighted higher than team-onboarding ergonomics.
- **First serious CMP project.** Stack choices favor longevity and learnability
  over short-term velocity.
- **Module layout: single `:shared`** with internal packages. Split into
  multiple modules only if build times demand it.
- **New app identity.** "MyKitta" is a new application id. **No migration of
  legacy Room data** from B2B-SIMPLR.
- **Auth slice bundled.** Single combined login screen with an embedded
  country switcher (calls the real `doLoginOTP` endpoint) is in scope as the
  smoke test for sub-project 1. WhatsApp-style UX (chip + bottom-sheet),
  silent auto-detect on first launch. OTP verify + token issuance + register
  + waiting are explicitly deferred.

## 2. Stack Decisions

| Concern | Choice | Rationale |
|---|---|---|
| iOS UI layer | **Compose Multiplatform** | Project is scaffolded for it; matches "re-architect" intent. SwiftUI shell rejected — costlier per screen for full feature parity. |
| Navigation | **Compose Navigation Multiplatform** (`androidx.navigation:navigation-compose` KMP) | Official, stable, pairs cleanly with the lifecycle-viewmodel-compose KMP `ViewModel`. Tradeoff: iOS state restoration on backgrounded multi-step flows is incomplete; revisit if checkout/OTP demand it. |
| State holder | **MVIKotlin `Store`** wrapped in a KMP `ViewModel` per screen | Pure reducer; observable intents/states/labels; first-class logging interceptor; deterministic test harness. |
| Side-effect channel | **MVIKotlin `Label`** | One-shot events (navigate, snackbar) ride the Label channel; no ad-hoc `SharedFlow<Effect>` plumbing. |
| Per-store logging | `LoggingStoreFactory` wrapping `DefaultStoreFactory` (debug also wraps `TimeTravelStoreFactory`) | Every intent / message / state / label funneled through Kermit. Solo-friendly observability. |
| Store testing | `TestStoreFactory` in `commonTest` | Deterministic, coroutine-aware unit tests for every reducer + executor. |
| DI | **Koin** (`koin-core`, `koin-compose-viewmodel`) | KMP-first, no codegen, lightweight. Service-locator style is acceptable trade for fewer moving parts. |
| Networking | **Ktor Client** + `kotlinx.serialization` | Engines: OkHttp on Android, Darwin on iOS. Auth plugin handles bearer + refresh. |
| Persistence | **SQLDelight 2.x** | Native iOS driver; the legacy Room schema is **not** carried over — fresh schema. |
| Key-value | **Multiplatform Settings** with platform encryption (`EncryptedSharedPreferences` on Android, Keychain on iOS) | Backs `TokenStore` and small prefs. |
| Date/time | **kotlinx-datetime** | Locale-aware formatting via `expect`/`actual` helpers. |
| Logging | **Kermit** | KMP-native; Crashlytics bridge available. |
| Crash reporting | **Firebase Crashlytics** both sides | Android via `google-services`; iOS via Firebase Apple SDK (SPM) in `iosApp`. |
| i18n | `compose.components.resources` with `composeResources/values-{en,fil}/strings.xml` | Supports PH/SG; runtime locale switch via Settings. |
| Phone formatting | `expect`/`actual` — libphonenumber on Android, native iOS wrap | Avoids a heavy pure-Kotlin port. Pinned at foundation level; actuals filled when auth feature lands. |
| Flavors | **dev / staging / prod** via Gradle product flavors + xcconfig + `expect`/`actual` `BuildEnv` | Android: Gradle flavors. iOS: xcconfig files. Shared: `BuildEnv` expect class. |
| Push notifications | **Deferred** | Foundation defines `NotificationDispatcher` interface only. FCM / HMS / APNs ship in a later sub-project. |
| Background jobs | **Deferred** | No WorkManager / BGTaskScheduler in foundation. Add when a feature needs it. |
| Image loading | **Deferred** | Coil-Multiplatform ships when catalog feature lands. |
| Analytics | **Deferred** | Not required for foundation. |
| App identity | New app id; no legacy data migration | Confirmed: MyKitta is a new product. |

## 3. Module & Package Layout

```
:shared
  src/commonMain/kotlin/com/mykitta/
    core/
      env/        BuildEnv (expect), Flavor enum
      logging/    Logger wrapper around Kermit, MVI ↔ Kermit bridge
      error/      AppError sealed, ErrorMapper
      result/     Outcome<T> sealed (Idle / Loading / Success / Failure)
      mvi/        BaseStoreFactory, ScreenViewModel<I, S, L>
    data/
      net/        KtorClientFactory, AuthPlugin config, defaults
      net/dto/    LoginOtpRequest, LoginOtpResponse, ErrorEnvelope
      net/api/    AuthApi (loginOtp only in this slice)
      db/         SqlDriverFactory (expect), DatabaseFactory
      prefs/      SettingsFactory (expect), TokenStore, CountryStore
      repo/       AuthRepository (loginOtp only in this slice)
    domain/
      Country enum { PH, SG }  — internal name; serializes to backend
                                wire-format "PHILIPPINE" / "SINGAPORE"
                                per legacy SharedPreferences contract
      PhonePrefix (PH +63, SG +65)
    feature/
      auth/
        LoginOtpScreen        composable (chip + bottom sheet + phone field)
        LoginOtpStore         MVIKotlin store (country + phone state machine)
        LoginOtpViewModel     ScreenViewModel wrapper
        CountrySelectorSheet  ModalBottomSheet composable used by LoginOtpScreen
        CountryDetector       expect — detect device country (PH/SG/null)
        AuthCountryFormatter  pure-Kotlin phone format + validation per country
        OtpSentPlaceholder    terminal screen of this slice
        AuthGraph             nav routes (LoginOtp start, OtpSent leaf)
    ui/
      theme/      MyKittaTheme, colors, typography
      nav/        AppNavHost, Destination sealed
      common/     OutcomeRenderer, LoadingState, ErrorView, MyKittaScaffold
      locale/     LocaleController (read / switch / persist)
    di/
      coreModule, networkModule, databaseModule, prefsModule,
      mviModule, uiModule, repositoryModule, featureAuthModule
    AppRoot.kt    @Composable — called by both platforms

  src/commonTest/kotlin/com/mykitta/
    core/         reducer tests, Outcome tests, ErrorMapper tests
    data/         AuthRepository tests (Ktor MockEngine), TokenStore + CountryStore contracts
    feature/      LoginOtpStore reducer/executor tests, AuthCountryFormatter tests
    di/           Koin module verification

  src/androidMain/kotlin/com/mykitta/
    actuals: BuildEnv, SqlDriverFactory, SettingsFactory,
             CountryDetector, http engine, encrypted Settings delegate

  src/iosMain/kotlin/com/mykitta/
    actuals: BuildEnv, SqlDriverFactory, SettingsFactory,
             CountryDetector, http engine, keychain Settings delegate

:androidApp
  MyKittaApplication       — Koin start, Crashlytics init, BuildEnv read
  MainActivity             — setContent { AppRoot() }
  flavors: dev / staging / prod

iosApp/iosApp/
  iOSApp.swift             — Firebase configure, Crashlytics start
  ContentView.swift        — ComposeUIViewController { AppRoot() }
  Configuration/
    Dev.xcconfig, Staging.xcconfig, Prod.xcconfig
```

## 4. Cross-Cutting Components

### 4.1 BuildEnv (`expect` / `actual`)

Single source for `flavor`, `baseUrl`, `versionName`, `isDebug`, `appName`.
Android: reads `BuildConfig`. iOS: reads `Info.plist` injected by xcconfig.

```kotlin
expect object BuildEnv {
  val flavor: Flavor
  val baseUrl: String
  val versionName: String
  val isDebug: Boolean
}
```

### 4.2 Ktor `HttpClient` factory

One configured client provided by Koin:

- Engine selected via `expect fun httpEngine(): HttpClientEngineFactory<*>`
  (OkHttp on Android, Darwin on iOS).
- `ContentNegotiation` with `kotlinx.serialization` JSON
  (`ignoreUnknownKeys = true`, `isLenient = false`).
- `Logging` plugin wired to Kermit; redacts the `Authorization` header.
- `DefaultRequest` sets `baseUrl` from `BuildEnv` and `Accept: application/json`.
- `HttpTimeout`: connect 10s, request 30s, socket 30s.
- `Auth` plugin: `bearer { loadTokens, refreshTokens }` — both delegate to
  `TokenStore`; refresh hits the existing B2B-SIMPLR refresh endpoint.

### 4.3 TokenStore

Sealed read/write surface backed by Multiplatform Settings with platform encryption:

```kotlin
interface TokenStore {
  suspend fun read(): TokenPair?
  suspend fun write(pair: TokenPair)
  suspend fun clear()
}
data class TokenPair(val access: String, val refresh: String, val expiresAt: Instant)
```

Sole writer in production code is the auth feature (future). Foundation
provides the interface, the implementation, and a contract test.

### 4.4 AppError + ErrorMapper

```kotlin
sealed interface AppError {
  data object Network : AppError                                    // no connectivity
  data class Http(val status: Int, val body: String?) : AppError    // 4xx / 5xx
  data object Unauthorized : AppError                               // 401 after refresh
  data class Parse(val cause: Throwable) : AppError                 // serialization failed
  data class Unknown(val cause: Throwable) : AppError               // catch-all
}

object ErrorMapper {
  fun from(throwable: Throwable): AppError = /* single funnel */
}
```

Every repository call site routes exceptions through `ErrorMapper.from()`.
ViewModels never see raw `Throwable`.

### 4.5 Outcome<T>

```kotlin
sealed interface Outcome<out T> {
  data object Idle : Outcome<Nothing>
  data object Loading : Outcome<Nothing>
  data class Success<T>(val value: T) : Outcome<T>
  data class Failure(val error: AppError) : Outcome<Nothing>
}
```

Repository / use-case layer returns `Outcome<T>` directly. Screen-level state
is the MVIKotlin `State` — the screen's `Executor` translates `Outcome<T>`
into its `Message` shape.

### 4.6 BaseStoreFactory

```kotlin
class BaseStoreFactory(
  private val isDebug: Boolean,
  private val logger: Logger,                  // Kermit-backed
) {
  fun create(): StoreFactory {
    val base = DefaultStoreFactory()
    val logged = LoggingStoreFactory(base, logger = KermitMVILogger(logger))
    return if (isDebug) TimeTravelStoreFactory(logged) else logged
  }
}
```

Single Koin binding. Every screen's `Store` is built through this factory.

### 4.7 ScreenViewModel<I, S, L>

```kotlin
abstract class ScreenViewModel<I : Any, S : Any, L : Any>(
  protected val store: Store<I, S, L>,
) : ViewModel() {
  val state: StateFlow<S> = store.stateFlow
  val labels: Flow<L> = store.labels
  fun accept(intent: I) = store.accept(intent)
  override fun onCleared() = store.dispose()
}
```

Every screen extends this. Compose collects state, listens to labels in
`LaunchedEffect`.

### 4.8 Navigation

`AppNavHost(navController)` with one sealed `Destination` class. Foundation
ships exactly one route: `Destination.Placeholder`. Real destinations land
with their features.

```kotlin
sealed interface Destination {
  data object Placeholder : Destination
}
```

### 4.9 Theme + Locale

- `MyKittaTheme` wraps Material3 with brand colors + typography.
- `LocaleController` reads device locale, lets the user choose between
  `en-PH` and `en-SG` (and `fil-PH` if/when translated), persists in Settings,
  recomposes the tree on change.
- Currency / number formatting via `kotlinx-datetime` + platform `expect`s.

### 4.10 Network observability (Chucker, Android-only)

**Goal:** in-app HTTP traffic inspection during development without an
external proxy.

**Choice:** [Chucker](https://github.com/ChuckerTeam/chucker) v4.x as an
OkHttp `Interceptor`. Captures every request/response (headers, body,
timing) and exposes them via a notification + in-app activity.

**Wiring:**

- `:shared/androidMain` exposes `AndroidNetworkConfig.interceptors: MutableList<Interceptor>`.
- The platform Ktor builder (`createPlatformHttpClient(...)` `expect`/`actual`)
  applies this list to OkHttp's engine config at construction time.
- `MyKittaApplication.onCreate()` adds a `ChuckerInterceptor` to that list
  **before** `initKoin {}` runs, so the singleton `HttpClient` picks it up.
- Gradle: `debugImplementation(libs.chucker)` + `releaseImplementation(libs.chucker.noOp)`.
  The no-op artifact ships a stub `ChuckerInterceptor` with the same API
  surface — `MyKittaApplication` compiles unchanged for release; the
  interceptor is a pass-through in production.

**iOS counterpart:** **none.** No equivalent in-app inspector exists for
the Darwin engine. Out-of-band tooling (Charles, Proxyman, mitmproxy) is
the path. Ktor's `Logging` plugin still writes to the iOS console via
`AppLogger`, which is enough for most cases.

**Trade-off:** Chucker requires `POST_NOTIFICATIONS` permission on API 33+
(its manifest merges this in automatically). The notification is the only
UI affordance — without it, the user wouldn't know inspection was running.
Acceptable for a dev convenience.

## 5. Smoke-Test Data Flow (Auth Slice)

The end-to-end flow exercised by sub-project 1:

```
AppRoot
  └─ AppNavHost
      └─ AuthGraph
          Start destination = LoginOtp (always; no separate picker screen)

          1. LoginOtpScreen
             └─ LoginOtpVM : ScreenViewModel<Intent, State, Label>
                  Store.bootstrapper (one-shot at create):
                    - read CountryStore → if present, set state.country = saved
                    - else call CountryDetector → if PH/SG, set; else default PH
                  State: { country, countrySelectorOpen, phoneRaw,
                           phoneFormatted, isValid, submitting, error }
                  Intent.{
                    PhoneChanged(raw),
                    OpenCountrySelector,
                    CloseCountrySelector,
                    SelectCountry(Country),     // from bottom sheet
                    Submit
                  }
                  Reducer: SelectCountry → recompute phoneFormatted + isValid
                                            against the new country, close sheet
                  Executor on Submit:
                    → AuthRepository.loginOtp(phoneE164, country)
                    → AuthApi POST /login/otp (exact path TBD from legacy)
                    on Outcome.Success: write CountryStore(country)
                                        emit Label.NavigateToOtpSent(phoneE164)
                    on Outcome.Failure: set state.error
                  emits Message.SubmitStarted / SubmitOk / SubmitFailed
                          / CountryChanged / CountrySelectorToggled

          2. OtpSentPlaceholderScreen
             Static screen showing "OTP sent to <phoneE164>". Has a Back
             affordance only. No verify input, no token expected, no auto-nav.
```

This flow exercises the full foundation: DI, Ktor with the correct flavor
`baseUrl`, `Auth` plugin (unused on `/login/otp` since it's unauthenticated —
still wired), error mapping, MVI store with logging interceptor, Outcome →
Message → State translation, CountryStore read (bootstrapper) / write
(executor on success), CountryDetector platform `expect`, encrypted Settings,
pure-Kotlin phone formatting.

`TokenStore` is wired and tested in isolation, but never written by this
slice (token issuance is part of OTP verify, which is a later sub-project).

## 6. Error Handling

- Every network call returns `Outcome<T>` from the repository layer; ViewModels
  / Stores never see raw exceptions.
- Ktor `Auth` plugin handles `401`: refresh once, retry once. On second 401,
  surface `AppError.Unauthorized`; `TokenStore.clear()` is invoked by an
  `AuthSessionObserver` (interface only in foundation; concrete observer ships
  with the auth feature).
- Crashlytics records non-fatals for `AppError.Unknown` and `AppError.Parse`.
- No silent catches. `ErrorMapper.from(throwable)` is the only translator.

## 7. Testing Strategy

`commonTest` — foundation:

- `ErrorMapper` cases: timeout, no-connectivity, 4xx, 5xx, malformed JSON, unknown.
- `Outcome` transitions and equality.
- `TokenStore` contract: write / read / clear / overwrite (fake Settings backend).
- `CountryStore` contract: read default (null), write, read back, overwrite.
- `LoggingStoreFactory` smoke: assert captured logger sees intents and states.

`commonTest` — auth slice:

- `AuthRepository.loginOtp` with Ktor `MockEngine`: Success (200), HTTP 4xx
  (invalid phone), HTTP 5xx, network failure, parse failure. Asserts
  `Outcome.Success` / `Outcome.Failure(AppError.*)` shapes.
- `LoginOtpStore` bootstrapper: with saved country in CountryStore → state
  initialized to saved; with no saved country and detector returns PH →
  initialized to PH; with no saved country and detector returns null → falls
  back to PH default.
- `LoginOtpStore` reducer: PhoneChanged with valid PH number → state.isValid
  true; with invalid → false; OpenCountrySelector → selectorOpen true;
  SelectCountry(SG) → state.country == SG, phoneFormatted recomputed against
  SG mask, selectorOpen false; Submit while submitting → no-op.
- `LoginOtpStore` executor: Submit → AuthRepository.loginOtp called →
  on success: CountryStore.write(country) called, then
  Label.NavigateToOtpSent(phoneE164) emitted; on failure: state.error set,
  CountryStore.write NOT called, no Label.
- `LoginOtpStore` invariant: country switch mid-typing keeps digits but
  rebuilds the masked format string; isValid recomputes against the new
  country length rule.
- `AuthCountryFormatter` tests: PH/SG masking, prefix injection, validation,
  E.164 normalization for both countries.

`androidUnitTest` / `iosTest`:

- Smoke instantiation of platform actuals (SQL driver, Settings, HTTP engine,
  PhoneFormatter, CountryDetector). Proves wiring; does not exercise behavior.

No UI tests in this sub-project. First UI tests ship in the next sub-project
(OTP verify, where state-machine complexity warrants them).

CI: out of scope — local build only. Add CI in a later sub-project.

## 8. Build / CI

- Gradle: enable parallel + configuration cache.
- Java toolchain: 21.
- Android product flavors: `dev`, `staging`, `prod`. Only `dev` fully wired in
  foundation v1; `staging` and `prod` are placeholders.
- iOS framework: dynamic `.framework`, linked from `iosApp` via the
  Compose plugin's default integration.
- Verification commands:
  - `./gradlew :androidApp:assembleDevDebug`
  - `./gradlew :shared:linkPodDebugFrameworkIosSimulatorArm64`
    (or the equivalent Compose Multiplatform iOS framework task)
  - `./gradlew :shared:testAndroidHostTest`
  - `./gradlew :shared:iosSimulatorArm64Test`
- All four must succeed before foundation v1 is "done".

## 9. Explicit Non-Goals

The following are **out** of sub-project 1 and ship in later sub-projects:

**Out of the auth slice (deferred to a follow-up auth sub-project):**

- OTP verify screen (4-digit input, validation, retry timer).
- Token issuance / persistence (no token write happens in this slice).
- `Repository.doVerifyLoginOTP` integration.
- Registration flows (`RegisterPicker`, PH owner, SG owner, partner).
- Verification dialog bottom sheet (used by PH + partner registration).
- `WaitingFragment` analog (principal-pending poll loop).
- SMS auto-read (`SmsRetriever` API on Android; no iOS equivalent).
- Google Play Services phone-hint picker (Android-only).
- Huawei device detection / HMS fallback.
- PH location CSV import (province / city / barangay).
- Legacy username/password `LoginFragment` (dead-ish per wiki).
- "Resend OTP" countdown timer (lives in OTP verify).
- Auth-session observer / token-clear-on-401 behavior (interface only; no
  consumer in this slice).

**Out of all wiki features for this sub-project:**

- Catalog, cart, chat, orders, address, promotions.
- Push notifications (FCM / HMS / APNs).
- Background jobs (WorkManager / BGTaskScheduler).
- Image loading (Coil-Multiplatform).
- Legacy Room data import.
- Analytics SDK.
- App branding polish (final logo, dark-mode tuning, animations).
- CI / CD pipeline.
- Crashlytics dashboards / alerts beyond default install.

## 10. Risks & Open Items

1. **`androidx.navigation` KMP maturity.** Multiplatform support is recent;
   iOS state restoration via `rememberSaveable` is incomplete. **Mitigation:**
   keep nav state minimal in foundation. **Re-decision point:** when the
   first multi-step feature (auth OTP or cart checkout) is designed, evaluate
   whether to swap to Decompose for that feature or for the whole app.

2. **MVIKotlin × Kotlin 2.3.21 × Compose 1.11.0 compatibility.** Verify the
   latest MVIKotlin (≥ 4.3.x at time of design) builds clean against this
   toolchain. **Mitigation:** if it doesn't, pin MVIKotlin to the highest
   compatible version and document the constraint. Decide at first compile.

3. **Per-screen MVI boilerplate.** ~150-200 LOC per screen including Intent /
   State / Label / Message / Reducer / Executor / VM / Composable. **Mitigation:**
   IntelliJ live templates + a `ScreenViewModel` base. Comfort-check after
   feature 1 — if velocity feels untenable, dial back to plain
   `StateFlow<UIState<T>>` per screen.

4. **iOS iteration speed.** Compose Hot Reload is Android-only; iOS requires
   framework rebuild + app restart. **Mitigation:** primary dev loop on
   Android emulator; iOS verification once per session.

5. **Phone number formatting on iOS — resolved for this slice.** PH and SG
   have fixed-length rules (10 and 8 digits, simple masks). The slice
   implements `AuthCountryFormatter` in pure common Kotlin (no
   `expect`/`actual`, no libphonenumber). When a future feature needs
   real international phone validation (e.g., partner registration), a
   proper `PhoneFormatter` `expect` surface ships then, with platform
   actuals (libphonenumber on Android, hand-rolled or PhoneNumberKit via
   SPM on iOS).

6. **Exact `doLoginOTP` endpoint contract — RESOLVED (2026-05-24).**
   Confirmed against `B2B.postman_collection.json` (saved at the repo root):
   - **Path:** `POST {baseUrl}Account/LoginOTP` (mixed case, not `/login/otp`).
   - **Request body:** `{ "userId": "<local-digits>", "country": "<dial-code-no-plus>" }`,
     e.g. `{ "userId": "9171234567", "country": "63" }`. **Not** E.164,
     **not** the `"PHILIPPINE"` legacy wire-format. `Country.apiCountryCode`
     holds the right value (`"63"` / `"65"`); `Country.wireFormat` is
     retained for `CountryStore` persistence only.
   - **Response shape:** the collection's saved-response array is empty
     for this endpoint. HTTP 200 is treated as success without body parsing
     (`KtorAuthApi.loginOtp` returns `Unit`). `ErrorMapper` translates
     non-2xx. **Revise to parse the envelope once VerifyLoginOTP lands** —
     that endpoint will need fields back (token, principal-pending state),
     at which point the standard `{ XxxResult: { errorData: {code,description}, ... } }`
     B2B envelope visible elsewhere in the collection should be modeled.

7. **Country detection on iOS.** Android uses `TelephonyManager.networkCountryIso`.
   iOS equivalent is `CTTelephonyNetworkInfo` + `CTCarrier.isoCountryCode`,
   but `CTCarrier` is deprecated in iOS 16+ and returns "--" in most cases.
   **Mitigation:** fall back to `Locale.current.region` on iOS; accept that
   auto-suggest will be less reliable on iOS than Android. Manual picker
   always works.

8. **Backend rate-limit on `doLoginOTP`.** Testing the slice repeatedly will
   send real SMS messages. **Mitigation:** use a known test phone number
   (TBD with backend owner) and rate-limit local testing. Spec assumes the
   backend tolerates dev-flavor traffic; verify before heavy iteration.

7. **Crashlytics on iOS.** Requires Firebase Apple SDK in the Xcode project
   (SPM). Adds CocoaPods or SPM tooling to `iosApp/`. **Decision:** SPM
   preferred (cleaner; no CocoaPods Ruby dependency). Confirm before
   wiring.

8. **Single-maintainer concentration (MVIKotlin).** Both MVIKotlin and the
   library family it sits in are Arkivanov-led. **Mitigation:** acceptable
   risk for solo project; library is small enough to fork if abandoned.

## 11. Decision Points / Future Sub-Projects

The foundation explicitly punts the following decisions to later sub-projects;
each is a deliberate re-decision moment, not an oversight:

- **After this sub-project ships:** comfort-check on the stack. Honest
  reassessment of:
  - MVIKotlin boilerplate vs. project velocity
  - Compose Nav iOS state restoration quality
  - Build/iteration times across both platforms
  - Whether to escalate to Decompose, dial back to plain `StateFlow`, or
    stay the course.

- **Sub-project 2 = auth completion:** OTP verify screen, token issuance,
  `AuthSessionObserver` (the 401 clear-token consumer), Resend OTP timer.
  Probably also `WaitingFragment` analog if the backend still requires the
  principal-pending poll. Register flows (PH / SG owner, partner) likely
  ship in sub-project 3 — large surface area, can be deferred until first
  real users actually need to register.

- **Sub-project 3+:** the remaining wiki features in roughly the order users
  hit them: catalog → cart → orders → address → promotions → chat.

- **Push / background-jobs sub-project:** when the first feature requires it
  (likely chat for push, or orders for background sync).

- **CI/CD sub-project:** before sub-project 3 lands, so the second feature
  ships with green CI.

## 12. Auth Slice — UX & Contract Detail

### 12.1 Screen 1: Login (combined country chip + phone entry)

**Trigger:** unconditional start destination of the app.

**Bootstrapper (runs once when store is created):**
- Read `CountryStore`:
  - If a saved country exists, use it.
  - Else call `CountryDetector.detect()`:
    - Returns `Country.PH` or `Country.SG` → use it.
    - Returns `null` (unsupported region or detection failed) → default to
      `Country.PH`.
- This is **silent** — no dialog, no toast. The chip just appears
  pre-filled.
- Persistence happens later, on submit success — not during bootstrap.

**Layout:**
- `MyKittaScaffold` with no back affordance (this is the start destination).
- Optional small logo at the top.
- Title: "Sign in".
- Subtitle: "Enter your phone number to receive an OTP".
- **Country chip** (tappable):
  - Format: `🇵🇭  +63  ▾` (flag, prefix, small down-arrow indicating it's
    tappable).
  - Lives directly above (or as a leading element of) the phone input.
- **Phone text field**: numeric keyboard, masked live per the selected
  country's format (`### ### ####` PH / `#### ####` SG). Prefix is NOT
  shown inside the input — it lives on the chip.
- **"Send OTP" primary button**: disabled until `state.isValid == true`;
  shows a spinner when `state.submitting == true`.
- Below the button: error text (red) when `state.error != null`.

**Country bottom sheet (overlay):**
- Triggered when `state.countrySelectorOpen == true`.
- Material 3 `ModalBottomSheet` with title "Select country" and two list
  rows:
  - 🇵🇭 Philippines · +63
  - 🇸🇬 Singapore · +65
- The currently selected country shows a check mark or filled radio.
- Tap a row → dismisses the sheet and switches the country.
- Tap outside / system back → dismisses with no change.

**Behavior:**
- **Phone edit.** Each keystroke fires `Intent.PhoneChanged(raw)`. Reducer
  derives `phoneFormatted`, `isValid`, clears `error`.
- **Open country selector.** Tapping the chip fires
  `Intent.OpenCountrySelector` → reducer sets
  `state.countrySelectorOpen = true`.
- **Country switch.** Tapping a row in the sheet fires
  `Intent.SelectCountry(c)` → reducer:
  - `state.country = c`
  - **Keeps `state.phoneRaw` unchanged** (user's typed digits preserved).
  - Re-runs `AuthCountryFormatter.format(c, raw)` and `isValid(c, raw)` to
    rebuild `phoneFormatted` and `isValid` against the new country.
  - `state.countrySelectorOpen = false`.
  - Does **not** persist to `CountryStore` here — only on submit success.
- **Dismiss selector.** Tap outside / system back fires
  `Intent.CloseCountrySelector` → reducer sets
  `state.countrySelectorOpen = false`.
- **Submit.** `Intent.Submit` runs the executor:
  - Sets `state.submitting = true`.
  - Computes E.164: `"+63" + cleanedDigits` (PH, strip any leading "0") or
    `"+65" + cleanedDigits` (SG).
  - Calls `AuthRepository.loginOtp(phoneE164, country) → Outcome<Unit>`.
  - On `Outcome.Success`:
    - `CountryStore.write(country)` — country is committed now.
    - Emit `Label.NavigateToOtpSent(phoneE164)`.
    - Set `submitting = false`.
  - On `Outcome.Failure(error)`:
    - Set `submitting = false`, `error = <human message from ErrorMapper>`.
    - **Do not** persist country (user might still switch and retry).
    - No label.

**Persistence model:**
- `CountryStore` is written **once**, on the first successful submit. On
  subsequent app launches the bootstrapper finds it and uses it without
  redetection.
- Detector failures and the PH default are **not** persisted on first
  launch — only an explicit success persists. Avoids locking in a wrong
  default for users who would switch before submitting.

### 12.2 Screen 2: OTP Sent (placeholder terminal)

**Trigger:** `Label.NavigateToOtpSent(phoneE164)` from the login store.

**Layout:**
- `MyKittaScaffold` with back affordance.
- Centered: "An OTP has been sent to <phoneE164>".
- Sub-text: "OTP verification will be added in the next release."
- A "Back" secondary button that pops to the login screen.

**Behavior:** completely static. No timers, no inputs, no retry. The slice
terminates here.

### 12.3 AuthCountryFormatter contract

```kotlin
object AuthCountryFormatter {
  fun format(country: Country, raw: String): String       // masked display
  fun clean(country: Country, raw: String): String        // digits-only, no prefix
  fun toE164(country: Country, raw: String): String       // "+63XXXXXXXXXX"
  fun isValid(country: Country, raw: String): Boolean     // length + digit rules
}
```

- PH: 10 digits after prefix (e.g., 9171234567). Display mask
  "### ### ####". Strip leading "0" if user typed one.
- SG: 8 digits after prefix (e.g., 81234567). Display mask "#### ####".

`clean(country, raw)` is the pivot used on country switch: keep the cleaned
digits, then re-`format()` against the new country. If the digits exceed
the new country's expected length, truncate to the max (e.g., 10 PH digits
typed → switch to SG → keep first 8).

### 12.4 AuthApi & AuthRepository contracts (this slice)

**Confirmed against `B2B.postman_collection.json` (2026-05-24).**

```kotlin
// data/net/api — wire-level contract
interface AuthApi {
  // HTTP 200 = SMS sent. No body parsing — the collection's saved response
  // is empty and this slice doesn't need anything back. ErrorMapper translates non-2xx.
  suspend fun loginOtp(request: LoginOtpRequest)
}

@Serializable data class LoginOtpRequest(
  val userId: String,   // local digits only, no "+", no country code, no leading "0"
  val country: String,  // dial code without "+" — e.g. "63" (PH) or "65" (SG)
)

// data/repo — domain-level contract
interface AuthRepository {
  // userIdDigits = country-local digits only; AuthRepository wraps it into
  // the LoginOtpRequest and maps Country.apiCountryCode into `country`.
  suspend fun loginOtp(userIdDigits: String, country: Country): Outcome<Unit>
}
```

**Path:** `POST {baseUrl}Account/LoginOTP` (mixed-case; matches the
Postman collection verbatim).

**Wire-level example:**

```http
POST http://simplronline.in:8800/api/Account/LoginOTP
Content-Type: application/json

{ "userId": "9171234567", "country": "63" }
```

The repository:
1. Wraps `(userIdDigits, country)` into `LoginOtpRequest(userId = userIdDigits, country = country.apiCountryCode)`.
2. Calls `AuthApi.loginOtp(request)`.
3. On exception, routes through `ErrorMapper.from(throwable)` → `Outcome.Failure(AppError.*)`.
4. On 2xx, returns `Outcome.Success(Unit)`.

ViewModels never touch `AuthApi` directly — `AuthRepository` is the only
caller.

**The `Country.wireFormat` field (`"PHILIPPINE"` / `"SINGAPORE"`) is NOT
sent on the wire.** It remains the persistence format for `CountryStore`
(preserving the legacy `SharedPreferences` contract). The API uses
`Country.apiCountryCode` (`"63"` / `"65"`).

**Open for next sub-project (VerifyLoginOTP):** the response envelope.
Postman shows other endpoints follow `{ XxxResult: { errorData: {code,description}, objectData: ... } }` —
when the verify endpoint is wired, model that envelope and surface
`errorData.code != 0` as `AppError.Http`-equivalent.

### 12.5 Navigation graph

```kotlin
sealed interface Destination {
  @Serializable data object LoginOtp : Destination
  @Serializable data class OtpSent(val phoneE164: String) : Destination
}
```

Start destination: **always `LoginOtp`**. The bootstrapper inside
`LoginOtpStore` decides which country pre-fills the chip (no separate
picker screen).

Argument passing for `OtpSent` uses Compose Navigation's type-safe routes
(`kotlinx.serialization`-backed `@Serializable` route objects, supported in
the current `androidx.navigation` multiplatform artifact — verify version
at first compile per §10 risk #2).

## 13. Glossary

- **B2B-SIMPLR** — the legacy Android codebase being migrated. Documented in
  `llm_wiki/`.
- **MyKitta** — this project. New product brand, new app identity.
- **CMP** — Compose Multiplatform.
- **KMP** — Kotlin Multiplatform.
- **Foundation** — sub-project 1, this document's scope.
- **Outcome** — repository-layer return type with Loading / Success / Failure.
- **Store** — MVIKotlin abstraction owning state + reducer + executor + labels.
- **Component / Container** — terms reserved if Decompose is later adopted;
  not used in foundation.
