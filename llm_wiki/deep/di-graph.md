---
title: Hilt DI graph
slug: di-graph
type: deep
verified: 2026-05-23
sources:
  - app/src/main/java/com/b2b/online/di/SingletonModule.kt
  - app/src/main/java/com/b2b/online/di/ActivityRetainModule.kt
  - app/src/main/java/com/b2b/online/di/DatabaseModule.kt
  - app/src/main/java/com/b2b/online/di/HelperModule.kt
  - app/src/main/java/com/b2b/online/di/SharedPrefModule.kt
  - app/src/main/java/com/b2b/online/base/BaseApplication.kt
---

# Hilt DI graph

**Hilt module wiring across SingletonModule, DatabaseModule, ActivityRetainModule, SharedPrefModule, HelperModule.**

## What it does
Hilt is the sole DI container. `BaseApplication` carries `@HiltAndroidApp`, which is what generates the `SingletonComponent` root at process start. Two scopes are in active use: `SingletonComponent` (process-wide) for things tied to app lifetime — Room database, DAOs, `SharedPreferences`, Crashlytics, asset manager — and `ActivityRetainedComponent` (survives config changes, dies on Activity finish) for the network stack and the country-aware `CountryHelper`. The network layer is `ActivityRetainedScoped` so that when the user changes country (`C.COUNTRY` in `SharedPreferences`), the next Activity gets a fresh Retrofit pointing at the new base URL. Modules are split by concern, not by scope, which is why `ActivityRetainModule` ends up doing all the Retrofit/OkHttp plumbing in one ~230-line file.

## Where it lives
- `app/src/main/java/com/b2b/online/base/BaseApplication.kt:21` — `@HiltAndroidApp` on `BaseApplication` (the only entry point; `onCreate` is otherwise empty).
- `app/src/main/java/com/b2b/online/di/SingletonModule.kt:17-36` — `assetManager`, `cartDao`, `cartDetailDao`, `FirebaseCrashlytics`. All `@Singleton`.
- `app/src/main/java/com/b2b/online/di/DatabaseModule.kt:18-51` — `provideDatabase` builds Room `LocalDatabase("b2b")` with 23 chained `addMigrations(...)` calls. `@Singleton`. (Migration objects fill the rest of the file — see [[room-database]].)
- `app/src/main/java/com/b2b/online/di/SharedPrefModule.kt:14-19` — `object` module, `provideSP` returns `getSharedPreferences("SIMPLR-B2B", MODE_PRIVATE)`. **No scope annotation** on the provider; relies on `SharedPreferences` being a singleton by Android contract.
- `app/src/main/java/com/b2b/online/di/SharedPrefModule.kt:21-43` — top-level `write`/`read` extensions (reified) used everywhere prefs are touched; not bindings but live in this file.
- `app/src/main/java/com/b2b/online/di/ActivityRetainModule.kt:38-233` — base URLs (`@Named("apiPH"|"basePost"`), media types (`@Named("encrypt"|"decrypt"`), `OkHttpClient` with token interceptor + Chucker-on-debug, three Retrofits (`@Named("retrofitGet"|"retrofitPost"|"retofitPH"`), and the three services (`ApiService`, `ApiPostService`, `PHLocationService`).
- `app/src/main/java/com/b2b/online/di/HelperModule.kt:18-23` — single binding: `CountryHelper(shared.read(C.COUNTRY, ""))`, `@ActivityRetainedScoped`.

## Depends on
- [[di]]
- [[base]]
- [[room-database]]
- [[repository]]
- [[build-variants]]
- [[localization]]

## Depended on by
- [[address-management]]
- [[build-variants]]
- [[push-notifications]]
- [[repository]]

## Gotchas
- **Missing `@Named("baseGet")` provider.** `ActivityRetainModule.kt:174` injects `@Named("baseGet") baseUrl: String` into `provideRetrofit`, but no module in `di/` provides that qualifier. Either it's defined in a flavor source set not read here, or `provideApiService` would fail at runtime. Worth verifying before assuming the GET Retrofit is wired. See open questions.
- **`SharedPrefModule` has no scope annotation.** `provideSP` is not `@Singleton`, so Hilt will create a new wrapper each injection — but Android returns the same underlying `SharedPreferences` instance per `(name, mode)` pair, so behaviour is identical. Still surprising; adding `@Singleton` would make intent explicit.
- **`@ActivityRetainedScoped` Retrofits are intentional, not a mistake.** They look like they "should" be `@Singleton` like the DAOs. They aren't, because `@Named("basePost")` reads `C.COUNTRY` from prefs at provision time (`ActivityRetainModule.kt:49-65`); making it `@Singleton` would freeze the base URL at first launch and silently break country switching. If you ever migrate this to a `@Singleton`, you must also move country switching into a per-request interceptor.
- **Token header lives in the OkHttp interceptor, not a header annotation.** `doCheckToken` (`ActivityRetainModule.kt:157-169`) reads `C.TOKEN` from prefs on every request and writes `Authorization: Bearer …`. There is no separate `AuthInterceptor` class — grep for `Bearer` if you're hunting the auth path.
- **Encryption interceptors are commented out.** `encryptedData`/`decryptedData` exist and reference `@Named("encrypt"|"decrypt")` media types, but the `.addInterceptor` calls are commented (`ActivityRetainModule.kt:91-95`). The two media-type bindings are currently dead but provided.
- **Why is `ActivityRetainModule` so large?** It bundles three concerns (URL config, OkHttp, three Retrofits) because all three are scoped to the same component and share dependencies. Splitting would force more `@Named` qualifiers across files. Acceptable trade-off, but it's the first file you should refactor if you add a fourth Retrofit.
- **`HelperModule` exists for one binding.** `CountryHelper` could live in `ActivityRetainModule`. It's separate because `CountryHelper` is consumed by ViewModels for [[localization]] decisions independent of network — keeping it out of the network module avoids accidental coupling.
- **No `@Provides` for `ApplicationContext`.** It's injected via Hilt's built-in `@ApplicationContext` qualifier (`dagger.hilt.android.qualifiers.ApplicationContext`) — no module needs to provide it.

## Open questions
- Where is `@Named("baseGet") String` provided? Not in any of the 5 DI files read here. Check flavor source sets (`src/debug/`, `src/demo/`, `src/release/`) under `app/src/.../di/` — `BuildConfig.API_GET` likely binds in a per-flavor module. See [[build-variants]].
- Is `FirebaseCrashlytics.getInstance()` being injected anywhere, or only used directly? If unused, the binding can be removed.
- Should `provideSP` be `@Singleton` for clarity? Behaviour is unchanged, but Hilt graph readers shouldn't have to know the Android contract to feel safe.

## Scope distribution observed
- `@Singleton` bindings: 5 (assetManager, cartDao, cartDetailDao, FirebaseCrashlytics, LocalDatabase). `provideSP` is implicitly singleton via Android, so call it 6.
- `@ActivityRetainedScoped` bindings: 12 (`apiPH`, `basePost`, `encrypt`, `decrypt`, `OkHttpClient`, `retrofitGet`, `ApiService`, `retrofitPost`, `ApiPostService`, `retofitPH`, `PHLocationService`, `CountryHelper`).
- Unscoped: `provideSP` (1, see above).
