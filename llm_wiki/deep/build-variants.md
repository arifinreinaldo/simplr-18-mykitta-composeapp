---
title: Build variants & Firebase distribution
slug: build-variants
type: deep
verified: 2026-05-23
sources:
  - app/build.gradle.kts
  - buildSrc/src/main/java/Library.kt
  - buildSrc/src/main/java/Versions.kt
  - build.gradle.kts
  - settings.gradle.kts
  - app/src/demo/agconnect-services.json
  - app/src/release/agconnect-services.json
  - app/src/release/google-services.json
---

# Build variants & Firebase distribution

**Three variants — debug, release, demo — plus Firebase App Distribution wiring.**

## What it does

The `:app` module ships three build types — `debug`, `release`, and a custom `demo` — that mostly differ in `applicationIdSuffix`, app name, minification, and where the resulting APK ends up. Base `applicationId` is `com.b2b.online` (defaultConfig at `app/build.gradle.kts:22`); each variant tacks a suffix on so all three can co-exist on the same device:

- **release** — installs as `com.b2b.online` ("MyKitaa"), minified + resource-shrunk via ProGuard. The production build.
- **debug** — installs as `com.b2b.online.dev` ("MyKitaa-Dev"), versionName gets `-dev` suffix, no minification, Chucker enabled, `alwaysUpdateBuildId=false` for faster incremental builds. The `assembleDebug` task is finalized by `copyDebug`, which copies the APK to `$rootDir/debugTest/testing-v<versionName>-<epochMillis>.apk`.
- **demo** — installs as `com.b2b.online.demo` ("MyKitaa-Demo"), versionName gets `-demo`. `initWith(debug)` so it inherits debug's settings, then enables minification + resource shrinking like release. Its unique trick: `assembleDemo` is finalized by `appDistributionUploadDemo`, pushing the APK to **Firebase App Distribution** for groups `tester,developer,surveyor-demo` with release notes read from `./release.txt`.

The Firebase App Distribution plugin (`com.google.firebase.appdistribution`) is applied at `app/build.gradle.kts:14` and its classpath comes from `build.gradle.kts:17` (`firebase-appdistribution-gradle:4.0.0`).

## Where it lives

- `app/build.gradle.kts:43-77` — the `buildTypes {}` block (all three variants).
  - `release` — `app/build.gradle.kts:44-52`
  - `debug` — `app/build.gradle.kts:53-58`
  - `demo` (custom, `create("demo")`) — `app/build.gradle.kts:59-75`, including `firebaseAppDistribution {}` at `:71-74`.
- `app/build.gradle.kts:159-164` — `copyDebug` task definition.
- `app/build.gradle.kts:165-175` — `tasks.configureEach` that wires `assembleDebug -> copyDebug` and `assembleDemo -> appDistributionUploadDemo`.
- `buildSrc/src/main/java/Library.kt:98-102` — `DependencyHandler.chucker()` extension: Chucker library on `debug`, no-op on `demo` and `release` (so demo builds don't ship the network inspector even though they inherit from debug).
- `buildSrc/src/main/java/Library.kt:143-145` — `demoImplementation` configuration helper, which is what makes per-variant deps possible.
- `build.gradle.kts:13-17` — top-level classpath: `google-services`, `firebase-crashlytics-gradle`, `firebase-appdistribution-gradle`, plus Huawei `agcp`.
- Flavor-specific source dirs (see Gotchas):
  - `app/src/demo/agconnect-services.json` — Huawei AGConnect config for demo builds.
  - `app/src/release/agconnect-services.json` and `app/src/release/google-services.json` — Huawei + Google config for release builds.
  - `app/src/main/` holds the shared `google-services.json` / `agconnect-services.json` that debug picks up by default.

## Depends on

- [[buildsrc]]
- [[di-graph]]
- [[push-notifications]]
- [[view-module]]

## Depended on by
- [[di-graph]]
- [[localization]]

## Gotchas

- **`release.txt` does not exist in the repo.** `demo { firebaseAppDistribution { releaseNotesFile = "./release.txt" } }` references a path relative to the `:app` module, but no such file is checked in. Either it's generated at build time by a CI step, written by hand before each demo upload, or `assembleDemo` silently uploads with empty/missing release notes. **Open question** — needs verification with whoever runs demo builds.
- **`matchingFallbacks = listOf("debug", "release")`** at `app/build.gradle.kts:70` only matters because the demo variant is custom. If any library module (like `:view`) ever introduces its own `buildTypes` without a `demo` entry, Gradle uses this fallback list to pick `debug` first, then `release`. Today `:view` has no custom build types, so the fallback is defensive — but it's the line that prevents future "Could not resolve variant" failures when adding flavors downstream.
- **`applicationIdSuffix` collisions are a real backend concern.** Production user accounts are scoped to package `com.b2b.online`; debug uses `com.b2b.online.dev`, demo uses `com.b2b.online.demo`. Firebase / Huawei push registration, OAuth redirect URIs, and any server-side allowlist of package names must include all three suffixes. The split `google-services.json` / `agconnect-services.json` under `app/src/release/` and `app/src/demo/` exists *because* the suffix means each variant is a separate Firebase / AGConnect app registration — they have different App IDs. Mixing these up = silent push delivery failure on the affected variant. See [[push-notifications]].
- **`demo` inherits `debug` then re-enables minification.** `initWith(getByName("debug"))` at `:60` copies the `debug` block's `applicationIdSuffix=".dev"` and `versionNameSuffix="-dev"` first, then the demo block overwrites them with `.demo` / `-demo`. **But** `extra["alwaysUpdateBuildId"] = false` from debug also propagates — meaning demo builds may re-use buildIds across builds, which can confuse Crashlytics symbol uploads if the same buildId points at different mappings. Not currently a known incident, but worth knowing.
- **`demoImplementation` is hand-rolled in `buildSrc`.** The configuration is added in `Library.kt:143-145` as a private extension. AGP would auto-generate `demoImplementation` once the `demo` build type exists, so adding the helper is just a typing convenience — but it means if you ever rename/remove the `demo` build type, this helper silently no-ops with a Gradle warning instead of failing loudly.
- **`assembleDemo` always uploads.** Because `tasks.configureEach { ... "assembleDemo" -> this.finalizedBy("appDistributionUploadDemo") }` (`:167-169`) is unconditional, running `./gradlew assembleDemo` locally will attempt a Firebase upload. Authentication is via the standard `FIREBASE_TOKEN` / service account JSON resolved by the plugin — if not configured, the build fails *after* a successful assemble. There is no `assembleDemoNoUpload` escape hatch wired in.
- **`debug` APK rename uses `Instant.now().toEpochMilli()`.** `copyDebug` (`:159-164`) imports `java.time.Instant` at file top (`:1`) and stamps the APK filename, so every `assembleDebug` produces a uniquely named file in `debugTest/` — old APKs accumulate and must be cleaned manually.
- **No product flavors, only build types.** This codebase has zero `flavorDimensions` / `productFlavors {}`. The multi-locale split (PH / SG mentioned in `CLAUDE.md`) is runtime, not build-time. See [[localization]].
- **`isCheckReleaseBuilds = false` + `isAbortOnError = false`** in `lintOptions` (`:37-42`) means release builds will not fail on lint errors. Combined with R8/ProGuard rules in `proguard-rules.pro`, lint regressions are invisible until manual inspection.
- **No flavor-specific `AndroidManifest.xml`.** `app/src/demo/` and `app/src/release/` only contain Firebase / AGConnect JSON files. Permissions, components, and intent filters are all in `app/src/main/AndroidManifest.xml`.

## Open questions

- Who/what generates `release.txt` before `assembleDemo`? Is it a CI step, a manual edit, or just missing-and-tolerated?
- Is the `surveyor-demo` Firebase tester group still in use, or stale from a past launch? It's the only group whose purpose isn't obvious from the name.
- Does the production backend's allowlist explicitly recognise `com.b2b.online.demo` and `.dev` for OAuth callbacks and push topics, or do demo/debug builds talk to a separate environment? Not visible from the build script alone.
- Is `app/src/debug/` deliberately absent (so debug uses `app/src/main/` for Firebase config), or should debug have its own override that just hasn't been added yet?
