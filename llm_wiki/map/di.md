---
title: di — package
slug: di
type: map
verified: 2026-05-23
sources:
  - app/src/main/java/com/b2b/online/di/
---

# di — package

**Hilt modules wiring the dependency graph.**

## What it does
Hilt modules that bind Repository, Room database, OkHttp/Retrofit, helpers,
and SharedPreferences into the DI graph. The single largest file by far is
`DatabaseModule.kt` because every Room migration is declared inline there.

## Where it lives
- `app/src/main/java/com/b2b/online/di/SingletonModule.kt` (902 B) — top-level singletons.
- `app/src/main/java/com/b2b/online/di/ActivityRetainModule.kt` (7.9 KB) — `@ActivityRetainedScoped` bindings.
- `app/src/main/java/com/b2b/online/di/DatabaseModule.kt` (18.6 KB) — Room database + 23 migration objects.
- `app/src/main/java/com/b2b/online/di/HelperModule.kt` (625 B).
- `app/src/main/java/com/b2b/online/di/SharedPrefModule.kt` (1.5 KB).

## Depends on
- [[data]]
- [[domain]]

## Depended on by
- [[authentication]]
- [[data]]
- [[di-graph]]
- [[feature]]
- [[localization]]
- [[push-notifications]]
- [[repository]]
- [[room-database]]
- [[worker]]

## Gotchas
- All 23 Room migrations are defined as inline `Migration` objects inside
  `DatabaseModule.kt` and registered in a long `.addMigrations(...)` chain.
  Adding a new migration touches this file in two places. See
  [[room-database]].
- `SingletonModule` is tiny — most singletons actually live in
  `ActivityRetainModule` because they need an `@ActivityRetainedScoped`
  lifecycle.

## Open questions
- What exactly distinguishes `HelperModule` from `SingletonModule` in
  practice — file size alone doesn't make the boundary obvious.
