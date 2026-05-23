---
title: data — package
slug: data
type: map
verified: 2026-05-23
sources:
  - app/src/main/java/com/b2b/online/data/Repository.kt
  - app/src/main/java/com/b2b/online/data/RepositoryPHLocation.kt
  - app/src/main/java/com/b2b/online/data/api/
  - app/src/main/java/com/b2b/online/data/room/
---

# data — package

**Network + persistence layer: Repository facade, Retrofit API services, Room database.**

## What it does
Houses everything that moves bytes between the app and the outside world: a
single Repository facade through which features make calls, Retrofit API
services for the backend, the Room database (with 23 migrations), and a
Philippines-specific location-resolution helper. Features never call Retrofit
or DAOs directly — they go through Repository.

## Where it lives
- `app/src/main/java/com/b2b/online/data/Repository.kt` — 1294-line god-object facade.
- `app/src/main/java/com/b2b/online/data/RepositoryPHLocation.kt` — Philippines location lookup.
- `app/src/main/java/com/b2b/online/data/api/` — Retrofit services (`ApiService.kt`, `ApiPostService.kt`, `PHLocationService.kt`, `NetworkUtil.kt`, `ApiResult.kt`) plus `request/` and `response/` DTO packages.
- `app/src/main/java/com/b2b/online/data/room/` — `LocalDatabase.kt`, `dao/`, `entity/`.

## Depends on
- [[domain]]
- [[di]]

## Depended on by
- [[address-management]]
- [[di]]
- [[feature]]
- [[repository]]
- [[room-database]]
- [[worker]]

## Gotchas
- The Repository is the only legitimate way into this package from
  feature code; see [[repository]] for its internals.
- Room migrations are not co-located with `data/room/` — they live in
  `DatabaseModule` under [[di]] / [[room-database]].

## Open questions
- Whether `NetworkUtil.kt` actually owns OkHttp/Retrofit configuration or
  just helpers — needs source read.
