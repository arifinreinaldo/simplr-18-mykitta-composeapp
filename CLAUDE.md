# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project orientation

**MyKitta** is a Kotlin Multiplatform (Compose Multiplatform) re-architecture of the legacy B2B-SIMPLR Android app, targeting Android + iOS. The backend contract is frozen; the stack is rebuilt.

The auth slice (login OTP → verify → signed-in) is live, the post-auth shell (Home, Principal, Profile, Rewards bottom-nav, Search overlay) is wired with real data on Home/Principal and stubs on Rewards. Many product surfaces (Cart, Chat, Notifications, principal-scoped catalog, rewards detail) are intentionally placeholders today. Before assuming a screen / module / repository exists, grep — and treat `docs/superpowers/specs/2026-05-23-mykitta-foundation-design.md` as historical context, not a current map.

`llm_wiki/` documents the **legacy** Android codebase being migrated; use it only as the canonical reference for backend behavior, not as a map of this repo.

Kotlin namespace: `com.simplr.mykitta2.*` (the foundation spec text says `com.mykitta.*` — the spec is wrong, the code is right).

## Commands

```bash
# First-time setup — build will FAIL loudly without this
cp .env.example .env   # fill in BASE_URL_*_{PH,SG} and APP_NAME_* for dev/staging/prod

# Android assemble (per flavor × buildType)
./gradlew :androidApp:assembleDevDebug
./gradlew :androidApp:assembleStagingDebug
./gradlew :androidApp:assembleProdRelease

# iOS framework (built from :shared, consumed by iosApp/)
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
./gradlew :shared:linkReleaseFrameworkIosArm64

# Tests
./gradlew :shared:testAndroidHostTest         # JVM-hosted Android unit tests + real SQLite driver tests
./gradlew :shared:iosSimulatorArm64Test       # iOS sim tests
./gradlew :shared:allTests                    # everything

# Single test (Kotlin Multiplatform test filter)
./gradlew :shared:testAndroidHostTest --tests "com.simplr.mykitta2.data.AuthRepositoryTest.successReturnsSuccess"

# SQLDelight schema regen (after editing shared/src/commonMain/sqldelight/**/*.sq)
./gradlew :shared:generateSqlDelightInterface

# Run iOS app: open iosApp/iosApp.xcodeproj in Xcode and Run (no Gradle equivalent)
```

`gradle.properties` enables configuration cache + build cache — don't disable casually. Toolchain: JVM 11, Kotlin 2.3.21, AGP 9.0.1, Compose Multiplatform 1.11.0, MVIKotlin 4.3.0, SQLDelight 2.1.0.

## The `.env` → `FlavorConfig.kt` codegen (load-bearing)

`shared/build.gradle.kts` defines a `generateBuildEnv` task wired into `commonMain` source generation. It reads `.env` at the repo root and emits `shared/build/generated/buildenv/commonMain/kotlin/com/simplr/mykitta2/core/env/FlavorConfig.kt`. Missing `.env` or any missing required key fails the build with an explicit message.

Required keys: `BASE_URL_{DEV,STAGING,PROD}_{PH,SG}` (six URLs — each (flavor, country) is its own backend) and `APP_NAME_{DEV,STAGING,PROD}`. `BuildEnv.baseUrlFor(country)` resolves the URL at call time; repositories pass the country down from `CountryStore` so requests route correctly per user.

`BuildEnv` (expect/actual) is initialized at boot:
- **Android** — `MyKittaApplication.onCreate` calls `initBuildEnv(...)` keyed off `BuildConfig.FLAVOR_NAME` *before* Koin starts.
- **iOS** — reads `MYKITTA_FLAVOR` / `MYKITTA_IS_DEBUG` from `Info.plist`. Those keys still need to be wired into `iosApp/Configuration/Config.xcconfig` for non-Dev iOS builds (Dev works because Dev is the fallback).

## Android boot wiring

`MyKittaApplication.onCreate` is the only place that side-effects happen before DI is alive. The order is load-bearing:
1. `initBuildEnv(...)` — sets the flavor for every subsequent `BuildEnv.*` read.
2. `AndroidNetworkConfig.addInterceptor(ChuckerInterceptor)` (debug only) — registers OkHttp interceptors that the Android `actual createPlatformHttpClient` will apply when Koin builds the HttpClient. **Adding interceptors after `initKoin` is too late** — the client is built eagerly.
3. `initKoin { androidContext(...) }` — Koin sees the interceptor list and the Android context.

Release builds skip even the no-op Chucker call (guarded on `BuildConfig.DEBUG`).

## Architecture

Single `:shared` Kotlin Multiplatform module + thin `:androidApp` Android Application + `iosApp/` Xcode project consuming the `Shared.framework`.

```
shared/src/commonMain/kotlin/com/simplr/mykitta2/
  App.kt                Compose entry; wires Coil image loader, ThemeStore, splash→nav.
  core/
    env/      BuildEnv (expect), Flavor enum, initBuildEnv, baseUrlFor(Country)
    logging/  AppLogger (Kermit), KermitMVILogger (MVI bridge)
    error/    AppError sealed interface, ErrorMapper
    result/   Outcome<T> sealed (Idle / Loading / Success / Failure)
    mvi/      BaseStoreFactory, ScreenViewModel<I, S, L>
  data/
    net/      KtorClientFactory (createForApi + createForImages), HttpEngineFactory (expect),
              AndroidNetworkConfig (interceptor registry consumed by androidMain actual)
    net/dto/  Serializable request/response DTOs
    net/api/  AuthApi, CatalogApi + Ktor implementations
    db/       SqlDriverFactory (expect), DatabaseFactory
    prefs/    SettingsFactory (expect), TokenStore, SessionStore, CountryStore, ThemeStore, ProfileCacheStore
    repo/     AuthRepository, HomeRepository, PrincipalRepository, ProfileRepository, LocalDataWiper
  domain/     Pure Kotlin model types (Country, Session, Banner, Item, Principal, ThemeMode, …)
  di/         AppModule — every Koin module, plus `commonModules()` + `expect platformModule`
  feature/    auth/ home/ principal/ profile/ search/ splash/ main/ — MVI store + screen per feature
  ui/
    nav/      AppNavHost (top-level), Destination sealed, MainTab sealed (nested tabs)
    common/   PlatformBackButton (expect chevron), MyKittaScaffold
    splash/   SplashScreen composable
    theme/    MyKittaTheme — light + dark palettes, ThemeMode plumbing
```

Platform actuals live under `androidMain/` and `iosMain/` mirroring the same package paths. Android JVM-hosted tests live under `androidHostTest/` and have the JVM SQLite driver wired so logout / DB-wipe paths can be exercised against a real schema; common tests use a fake.

### MVI pattern (MVIKotlin)

Every screen owns a `Store<Intent, State, Label>`. `Store`s are built via `BaseStoreFactory.create()` which returns a `StoreFactory` chain: `LoggingStoreFactory(...) → DefaultStoreFactory()` in release, plus `TimeTravelStoreFactory` wrapping in debug. `ScreenViewModel<I, S, L>` is the AndroidX `ViewModel` adapter that exposes `state: StateFlow<S>`, `labels: Flow<L>`, and `accept(intent: I)`; it calls `store.dispose()` in `onCleared`. **All screens extend `ScreenViewModel`** — don't roll a bare `StateFlow<UIState>` pattern.

Labels carry one-shot side effects (navigate, snackbar). Compose collects state via `collectAsStateWithLifecycle` and observes labels in `LaunchedEffect`.

Store factories with per-screen args (e.g. `OtpVerifyStoreFactory(args = OtpVerifyArgs(...))`) get those args via Koin `parametersOf(...)` at `koinViewModel<…>(parameters = { parametersOf(args) })` call time. The ViewModel forwards them to the factory.

### Navigation topology

Two NavControllers, one nested:

- **Top-level NavHost** (`AppNavHost`) is sibling to the splash. Routes: `LoginOtp`, `OtpVerify`, `Home` (= `MainShell`), `Search`, `ProfileDetail`, `SignedIn` (legacy, retained for tests). `ProfileDetail` lives at the top level (not as a tab) so it pushes a full-screen surface over the bottom-nav shell; the Profile tab invokes `onOpenProfileDetail` to navigate up to it.
- **Splash** sits outside `AppNavHost` and resolves a `SplashStore.Destination` (Home or Login) — `App.kt` keeps the resolved destination in `rememberSaveable` so a config change doesn't replay the splash animation. Splash is unreachable via back-button by construction.
- **MainShell** owns its own NavController for the bottom-nav tabs (`MainTab.Home / Principal / Rewards / Profile`, plus the nested `PrincipalCatalog`). Tab switches use `popUpTo(graph.startDestination) { saveState = true } + launchSingleTop + restoreState` so each tab keeps its back-stack and scroll state.
- **Search** is a top-level destination (not a tab) and reads `onOpenSearch` from `MainShell` via callback.
- **OTP-verify → Home** pops the entire login graph: `popUpTo(LoginOtp) { inclusive = true }`. Back button on Home exits the app, not back into login. Logout does the inverse from Home.

All destinations are kotlinx-serializable sealed types (`Destination.LoginOtp`, `MainTab.Home`, …) — use `backStackEntry.toRoute<T>()` to recover route args, not string keys.

### Error + result conventions

Repositories return `Outcome<T>` (`Idle | Loading | Success | Failure(AppError)`). Throwables — including from Ktor — are funneled through `ErrorMapper.from(throwable)` at the repository boundary. **ViewModels / Stores must never see a raw `Throwable`.** `ErrorMapper.message(AppError)` produces user-facing strings; reach for it instead of hand-writing messages. `CancellationException` is re-thrown, never mapped.

### Persistence

- **SQLDelight 2.x** for relational storage. Schema lives in `shared/src/commonMain/sqldelight/com/simplr/mykitta2/shared/db/*.sq`; package `com.simplr.mykitta2.shared.db`. Today: `Schema.sq` (Meta startup-warmup table) and `Principal.sq`. Features add their own `.sq` files. The Room schema from the legacy app is **not** migrated.
- **Multiplatform Settings** for key-value, with two flavors via `SettingsFactory`: `secureSettings(name)` (EncryptedSharedPreferences on Android, Keychain on iOS) for tokens, and `plainSettings(name)` (SharedPreferences / NSUserDefaults) for prefs. `TokenStore` uses secure; `SessionStore`, `CountryStore`, `ThemeStore`, `ProfileCacheStore` use plain.
- **Per-feature read-through caches** live next to the prefs stores (`ProfileCacheStore` is the canonical example: JSON-serialized payload + fetched-at timestamp, default 24h TTL exposed as `ProfileCacheStore.DEFAULT_TTL`). The repository — not the store — decides freshness (`loadProfile(ttl = …)`) and is the only place that calls the network. Treat these caches as user-scoped: register them in `LocalDataWiper` so logout drops them.
- **`LocalDataWiper.wipeAll()`** — `AuthRepository.logout()` clears tokens, session, country, and every user-scoped SQLDelight table through this interface. Add a new `deleteAll()` call inside `MyKittaDatabaseWiper` when a new `.sq` table lands; logout will then stay correct without further surgery. Wrap multi-table wipes in a transaction.

### Networking

`KtorClientFactory` produces **two** HttpClients:

1. **API client** (`KtorClientFactory.create(tokenStore, appLogger)`):
   - Engine via `expect fun createPlatformHttpClient(config)` — OkHttp on Android (applies `AndroidNetworkConfig.interceptors` to the engine config), Darwin on iOS.
   - `expectSuccess = true` — non-2xx throws, which `ErrorMapper` translates.
   - `ContentNegotiation` with `kotlinx.serialization` JSON (`ignoreUnknownKeys = true`, `isLenient = false`).
   - `Logging` wired through `AppLogger`, with the `Authorization` header sanitized and a regex redact pass on the message body.
   - `Auth` bearer plugin reads from `TokenStore`. The refresh hook is a placeholder — until that lands, the verify call stamps a synthetic 365-day expiry into `TokenStore` so the Bearer plugin loads tokens, and a 401 from any call is the de-facto expiry signal.
2. **Image client** (`KtorClientFactory.createForImages()`, Koin-qualified `IMAGE_HTTP_CLIENT`):
   - **No ContentNegotiation** — that plugin auto-adds `Accept: application/json` and IIS responds 406 to image GETs. App-wide root cause; do not "fix" by sharing the API client.
   - Wired into Coil via `setSingletonImageLoaderFactory { ImageLoader.Builder(ctx).components { add(KtorNetworkFetcherFactory(imageHttpClient)) } }` in `App.kt`. Use `AsyncImage` everywhere; the singleton picks it up.

**`baseUrl` is per-call, per-country** — call sites pass `BuildEnv.baseUrlFor(countryStore.read())` because each country lives on its own server. Tests build their own `HttpClient` with `MockEngine`.

**Chucker** (Android-only HTTP inspector) is wired via `debugImplementation(libs.chucker)` + `releaseImplementation(libs.chucker.noOp)`. Release builds get the no-op artifact so the call site compiles identically; the interceptor is installed only when `BuildConfig.DEBUG`. iOS has no equivalent — use an external proxy (Proxyman / Charles).

### Theming

`MyKittaTheme(themeMode: ThemeMode)` switches `lightColorScheme` / `darkColorScheme`. `ThemeMode.SYSTEM` defers to `isSystemInDarkTheme()`. `App.kt` collects `ThemeStore.mode` as state, so a `themeStore.set(...)` from `ProfileScreen` re-themes the whole tree immediately. Defaults to `SYSTEM` when nothing is stored.

### Platform back-button affordance

`PlatformBackButton` is a common-API composable whose chevron glyph is an internal `expect` — Android draws Material `arrow_back`, iOS draws an SF-symbol `chevron.backward`. Reach for it instead of `IconButton(Icons.Default.ArrowBack)` so navigation icons match host conventions on each platform.

### iOS framework linker

`shared/build.gradle.kts` pins two linker opts on the framework binary:
- `-lsqlite3` — `NativeSqliteDriver` needs it; without this, the iosApp link step fails with ~31 undefined `_sqlite3_*` symbols.
- `-mios-version-min=15.0` / `-mios-simulator-version-min=15.0` — must match `IPHONEOS_DEPLOYMENT_TARGET` in `iosApp/Configuration/Config.xcconfig`. Drift in either direction re-triggers "object file built for newer iOS-simulator version than being linked".

## Conventions worth not breaking

- **Don't bypass `ErrorMapper`.** Every `try { … } catch (t: Throwable)` in a repository should funnel through it, and `CancellationException` must be re-thrown explicitly.
- **Don't bypass `LocalDataWiper`** for logout — extend it. The contract is "logout wipes everything user-scoped"; adding a new `.sq` table without updating the wiper leaks user data across sessions.
- **Don't share the API HttpClient with Coil.** Image requests need a client without `ContentNegotiation` (see above). The two-client split is intentional.
- **Don't add Hilt, Dagger, or codegen DI.** Koin is the only DI; modules live under `shared/src/commonMain/kotlin/com/simplr/mykitta2/di/AppModule.kt`. New feature modules: add a `featureXxxModule` and append to `commonModules()`.
- **Don't add `expect`/`actual` for things that fit in pure common Kotlin.** The phone formatter is common-only by design; a real `PhoneFormatter` expect surface is deferred until partner registration needs international validation.
- **Backend wire-format strings matter.** `Country.PH.wireFormat == "PHILIPPINE"`, not `"PH"` — this is the legacy `SharedPreferences` contract, preserved on purpose. `apiCountryCode` is the dial code (`"63"`/`"65"`) used in request bodies; `iso` is the round-trip key used in nav routes.
- **`flavorDimensions = ["env"]`** with `dev`/`staging`/`prod`. Don't add a second dimension without a real reason.
- **`google-services.json` is conditional.** `androidApp/build.gradle.kts` only applies the Google Services + Crashlytics plugins if a `google-services.json` exists somewhere under `androidApp/` or `androidApp/src/<flavor>/`. Builds succeed without one; Crashlytics just doesn't initialize.
- **Tab switches use the saved-state options bundle**, not raw `navigate(tab)`. Copy the helper in `MainShell.switchTab` rather than reinventing it — the `saveState`/`restoreState` pair is what keeps each tab's scroll position and back-stack.
- **External URLs go through `LocalUriHandler.current.openUri(…)`**, not a custom in-app browser. The About menu in `MainShell.kt` opens a hard-coded YouTube link this way — keep that pattern for any "open in browser / partner site" actions.

## Useful pointers

- Foundation design spec (historical, not current): `docs/superpowers/specs/2026-05-23-mykitta-foundation-design.md`.
- Legacy backend reference (do not treat as this repo's structure): `llm_wiki/index.md` and `llm_wiki/deep/repository.md`.
- iOS app entry: `iosApp/iosApp/iOSApp.swift` → `ContentView.swift` → `MainViewControllerKt.MainViewController()` exported from `:shared`.
- Android app entry: `androidApp/src/main/kotlin/com/simplr/mykitta2/MyKittaApplication.kt` → `MainActivity.kt`.
