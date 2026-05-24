# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project orientation

**MyKitta** is a Kotlin Multiplatform (Compose Multiplatform) re-architecture of the legacy B2B-SIMPLR Android app, targeting Android + iOS. The backend contract is frozen; the stack is rebuilt. The codebase is currently at **sub-project 1: foundation + thin auth slice** — most feature surface area in `docs/superpowers/specs/2026-05-23-mykitta-foundation-design.md` is still aspirational. See that spec before assuming a screen, DI module, or feature class exists.

`llm_wiki/` documents the **legacy** Android codebase being migrated; use it only as the canonical reference for backend behavior, not as a map of this repo.

Kotlin namespace: `com.simplr.mykitta2.*` (the spec text says `com.mykitta.*` — the spec is wrong, the code is right).

## Commands

```bash
# First-time setup — build will FAIL loudly without this
cp .env.example .env   # fill in BASE_URL_* and APP_NAME_* for dev/staging/prod

# Android assemble (per flavor × buildType)
./gradlew :androidApp:assembleDevDebug
./gradlew :androidApp:assembleStagingDebug
./gradlew :androidApp:assembleProdRelease

# iOS framework (built from :shared, consumed by iosApp/)
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
./gradlew :shared:linkReleaseFrameworkIosArm64

# Tests
./gradlew :shared:testAndroidHostTest         # JVM-hosted Android unit tests
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

`shared/build.gradle.kts` defines a `generateBuildEnv` task wired into `commonMain` source generation. It reads `.env` at the repo root and emits `shared/build/generated/buildenv/commonMain/kotlin/com/simplr/mykitta2/core/env/FlavorConfig.kt`. Missing `.env` or missing any required key fails the build with an explicit message. Required keys: `BASE_URL_{DEV,STAGING,PROD}` and `APP_NAME_{DEV,STAGING,PROD}`.

`BuildEnv` (expect/actual) reads `FlavorConfig` at runtime keyed by `Flavor`. On Android, `Flavor` is supplied at boot via `initBuildEnv(...)` reading the `FLAVOR_NAME` `BuildConfig` field — **this initializer is not yet called anywhere** (no `MyKittaApplication` class exists yet). On iOS, `BuildEnv` reads `MYKITTA_FLAVOR` / `MYKITTA_IS_DEBUG` from `Info.plist` — those keys are also not yet wired into `iosApp/Configuration/Config.xcconfig`. Both are open items for the foundation finish.

## Architecture

Single `:shared` Kotlin Multiplatform module + thin `:androidApp` Android Application + `iosApp/` Xcode project consuming the `Shared.framework`.

```
shared/src/commonMain/kotlin/com/simplr/mykitta2/
  core/
    env/      BuildEnv (expect), Flavor enum, initBuildEnv
    logging/  AppLogger (Kermit), KermitMVILogger (MVI bridge)
    error/    AppError sealed interface, ErrorMapper
    result/   Outcome<T> sealed (Idle / Loading / Success / Failure)
    mvi/      BaseStoreFactory, ScreenViewModel<I, S, L>
  data/
    net/      KtorClientFactory, HttpEngineFactory (expect)
    net/dto/  Serializable request/response DTOs
    net/api/  Api interfaces + Ktor implementations (AuthApi today)
    db/       SqlDriverFactory (expect), DatabaseFactory
    prefs/    SettingsFactory (expect), TokenStore, CountryStore
    repo/     Repository interfaces (AuthRepository today)
  domain/     Pure Kotlin model types (Country today)
```

Platform actuals live under `androidMain/` and `iosMain/` mirroring the same package paths.

### MVI pattern (MVIKotlin)

Every screen owns a `Store<Intent, State, Label>`. `Store`s are built via `BaseStoreFactory.create()` which returns a `StoreFactory` chain: `LoggingStoreFactory(...) → DefaultStoreFactory()` in release, plus `TimeTravelStoreFactory` wrapping in debug. `ScreenViewModel<I, S, L>` is the AndroidX `ViewModel` adapter that exposes `state: StateFlow<S>`, `labels: Flow<L>`, and `accept(intent: I)`; it calls `store.dispose()` in `onCleared`. **All screens extend `ScreenViewModel`** — don't roll a bare `StateFlow<UIState>` pattern.

Labels carry one-shot side effects (navigate, snackbar). Compose collects state via `collectAsStateWithLifecycle` and observes labels in `LaunchedEffect`.

### Error + result conventions

Repositories return `Outcome<T>` (`Idle | Loading | Success | Failure(AppError)`). Throwables — including from Ktor — are funneled through `ErrorMapper.from(throwable)` at the repository boundary. **ViewModels / Stores must never see a raw `Throwable`.** `ErrorMapper.message(AppError)` produces user-facing strings; reach for it instead of hand-writing messages. `CancellationException` is re-thrown, never mapped.

### Persistence

- **SQLDelight 2.x** for relational storage. Schema lives in `shared/src/commonMain/sqldelight/com/simplr/mykitta2/shared/db/*.sq`; package `com.simplr.mykitta2.shared.db`. Foundation `Schema.sq` is intentionally empty (only a `Meta` table) — features add their own `.sq` files. The Room schema from the legacy app is **not** migrated.
- **Multiplatform Settings** for key-value, with two flavors via `SettingsFactory`: `secureSettings(name)` (EncryptedSharedPreferences on Android, Keychain on iOS) for tokens, and `plainSettings(name)` (SharedPreferences / NSUserDefaults) for prefs like `CountryStore`. `TokenStore` always uses `secureSettings`.

### Networking

`KtorClientFactory.create(...)` produces a single configured `HttpClient`:
- Engine via `expect fun createPlatformHttpClient(config)` — OkHttp on Android, Darwin on iOS. The Android `actual` applies `AndroidNetworkConfig.interceptors` to the OkHttp engine config; the host app populates that list before Koin builds the client (this is how Chucker is injected — see `MyKittaApplication`).
- `expectSuccess = true` — non-2xx throws, which `ErrorMapper` translates.
- `ContentNegotiation` with `kotlinx.serialization` JSON (`ignoreUnknownKeys = true`, `isLenient = false`).
- `Logging` wired through `AppLogger`, with the `Authorization` header sanitized and a regex redact pass on the message.
- `Auth` bearer plugin reads from `TokenStore`. The refresh hook is a placeholder until the OTP-verify sub-project lands.
- `baseUrl` defaults to `BuildEnv.baseUrl`; tests build their own `HttpClient` with `MockEngine` directly.

**Chucker** (in-app HTTP traffic inspector) is wired Android-only via `debugImplementation(libs.chucker)` + `releaseImplementation(libs.chucker.noOp)`. Release builds get the no-op artifact so `MyKittaApplication` compiles identically either way; the interceptor is a pass-through in production. iOS has no equivalent — use an external proxy (Proxyman / Charles) for iOS traffic inspection.

## Conventions worth not breaking

- **Don't bypass `ErrorMapper`.** Every `try { … } catch (t: Throwable)` in a repository should funnel through it.
- **Don't add Hilt, Dagger, or codegen DI.** Koin is the only DI; the Koin modules described in the spec aren't written yet — when you add them, they live under `shared/src/commonMain/kotlin/com/simplr/mykitta2/di/`.
- **Don't add `expect`/`actual` for things that fit in pure common Kotlin.** The auth slice's phone formatter is explicitly common-only (PH/SG masks are simple); a real `PhoneFormatter` expect surface is deferred until partner registration needs international validation. Reference §10 of the foundation spec.
- **Backend wire-format strings matter.** `Country.PH.wireFormat == "PHILIPPINE"`, not `"PH"` — this is the legacy `SharedPreferences` contract, preserved on purpose.
- **`flavorDimensions = ["env"]`** with `dev`/`staging`/`prod`. Don't add a second dimension without a real reason; the foundation only fully wires `dev`.
- **`google-services.json` is conditional.** `androidApp/build.gradle.kts` only applies the Google Services + Crashlytics plugins if a `google-services.json` exists somewhere under `androidApp/` or `androidApp/src/<flavor>/`. Builds succeed without one; Crashlytics just doesn't initialize.

## Useful pointers

- Foundation design spec (authoritative for current sub-project): `docs/superpowers/specs/2026-05-23-mykitta-foundation-design.md`.
- Legacy backend reference (do not treat as this repo's structure): `llm_wiki/index.md` and `llm_wiki/deep/repository.md`.
- iOS app entry: `iosApp/iosApp/iOSApp.swift` → `ContentView.swift` → `MainViewControllerKt.MainViewController()` exported from `:shared`.