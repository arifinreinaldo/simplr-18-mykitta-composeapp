---
title: Room database & migrations
slug: room-database
type: deep
verified: 2026-05-23
sources:
  - app/src/main/java/com/b2b/online/data/room/LocalDatabase.kt
  - app/src/main/java/com/b2b/online/di/DatabaseModule.kt
  - app/src/main/java/com/b2b/online/data/room/dao/AddressDao.kt
  - app/src/main/java/com/b2b/online/data/room/dao/BannerDao.kt
  - app/src/main/java/com/b2b/online/data/room/dao/BaseDao.kt
  - app/src/main/java/com/b2b/online/data/room/dao/CartDao.kt
  - app/src/main/java/com/b2b/online/data/room/dao/CartDetailDao.kt
  - app/src/main/java/com/b2b/online/data/room/dao/CategoryItemDao.kt
  - app/src/main/java/com/b2b/online/data/room/dao/ChatDao.kt
  - app/src/main/java/com/b2b/online/data/room/dao/ConfigListDao.kt
  - app/src/main/java/com/b2b/online/data/room/dao/HistoryDao.kt
  - app/src/main/java/com/b2b/online/data/room/dao/HistoryDetailDao.kt
  - app/src/main/java/com/b2b/online/data/room/dao/HotItemDao.kt
  - app/src/main/java/com/b2b/online/data/room/dao/ImageDao.kt
  - app/src/main/java/com/b2b/online/data/room/dao/LastItemDao.kt
  - app/src/main/java/com/b2b/online/data/room/dao/NotifDao.kt
  - app/src/main/java/com/b2b/online/data/room/dao/PHBarangayDao.kt
  - app/src/main/java/com/b2b/online/data/room/dao/PHCityDao.kt
  - app/src/main/java/com/b2b/online/data/room/dao/PHProvinceDao.kt
  - app/src/main/java/com/b2b/online/data/room/dao/PrincipalDao.kt
  - app/src/main/java/com/b2b/online/data/room/dao/PrincipalItemDao.kt
  - app/src/main/java/com/b2b/online/data/room/dao/PrincipalPickerDao.kt
  - app/src/main/java/com/b2b/online/data/room/dao/PromoBonusDao.kt
  - app/src/main/java/com/b2b/online/data/room/dao/PromoDao.kt
  - app/src/main/java/com/b2b/online/data/room/dao/PromoReqDao.kt
  - app/src/main/java/com/b2b/online/data/room/dao/SearchItemDao.kt
  - app/src/main/java/com/b2b/online/data/room/entity/AddressEntity.kt
  - app/src/main/java/com/b2b/online/data/room/entity/BannerEntity.kt
  - app/src/main/java/com/b2b/online/data/room/entity/CartEntity.kt
  - app/src/main/java/com/b2b/online/data/room/entity/CategoryItemEntity.kt
  - app/src/main/java/com/b2b/online/data/room/entity/ChatEntity.kt
  - app/src/main/java/com/b2b/online/data/room/entity/ConfigListEntity.kt
  - app/src/main/java/com/b2b/online/data/room/entity/HistoryEntity.kt
  - app/src/main/java/com/b2b/online/data/room/entity/HotItemEntity.kt
  - app/src/main/java/com/b2b/online/data/room/entity/ImageEntity.kt
  - app/src/main/java/com/b2b/online/data/room/entity/LastItemEntity.kt
  - app/src/main/java/com/b2b/online/data/room/entity/NotifEntity.kt
  - app/src/main/java/com/b2b/online/data/room/entity/PHBarangayEntity.kt
  - app/src/main/java/com/b2b/online/data/room/entity/PHCityEntity.kt
  - app/src/main/java/com/b2b/online/data/room/entity/PHProvinceEntity.kt
  - app/src/main/java/com/b2b/online/data/room/entity/PrincipalEntity.kt
  - app/src/main/java/com/b2b/online/data/room/entity/PrincipalItemEntity.kt
  - app/src/main/java/com/b2b/online/data/room/entity/PrincipalPickerEntity.kt
  - app/src/main/java/com/b2b/online/data/room/entity/PromoEntity.kt
  - app/src/main/java/com/b2b/online/data/room/entity/SearchItemEntity.kt
---

# Room database & migrations

**Room schema, entities, DAOs, and the 23 migrations from db1to2 through db23to24.**

## What it does
Defines the on-device SQLite store named `"b2b"` (`DatabaseModule.kt:26`) that backs almost every cache path in [[repository]]. `LocalDatabase` is a single `@Database` abstract class at version 24 (`LocalDatabase.kt:78`) that aggregates 23 entities and exposes 23 DAOs. Hilt provides it as a `@Singleton` via `DatabaseModule.provideDatabase` (`DatabaseModule.kt:20-51`). Every schema bump since v1 ships as a hand-written `Migration` object registered in order on the builder; there is no `fallbackToDestructiveMigration`, so a missing migration crashes the app on first open after upgrade.

## Where it lives

**Database root**
- `app/src/main/java/com/b2b/online/data/room/LocalDatabase.kt:52-106` — `@Database(entities = [...], version = 24, exportSchema = true)`; 23 entities, 23 DAO accessors. `autoMigrations` is commented out (`LocalDatabase.kt:79`).
- `app/src/main/java/com/b2b/online/di/DatabaseModule.kt:20-51` — Hilt `@Provides` builder; instantiates the DB with name `"b2b"` and chains every `addMigrations(dbNtoN+1)` call.

**Entities** (all under `data/room/entity/`)
- `AddressEntity` (+ helper `UpdatedAddressEntity` for partial `@Update`s) — `AddressEntity.kt`
- `BannerEntity` — `BannerEntity.kt`
- `CartEntity`, `CartDetailEntity`, `CartSelectedUpdate`, `CartActiveUpdate`, `CartWithDetailsEntity` — `CartEntity.kt` (composite PK on detail; `@Relation` view for cart+details)
- `CategoryItemEntity` — `CategoryItemEntity.kt` (composite PK `productId,type`)
- `ChatEntity` (+ `ChatData` join view) — `ChatEntity.kt`
- `ConfigListEntity` — `ConfigListEntity.kt`
- `HistoryEntity`, `HistoryDetailEntity`, `HistoryData` — `HistoryEntity.kt` (detail composite PK `invNo,line,salesType`)
- `HotItemEntity` — `HotItemEntity.kt`
- `ImageEntity` — `ImageEntity.kt` (composite PK `productId,productUrl`)
- `LastItemEntity` — `LastItemEntity.kt`
- `NotifEntity` (+ `NotifOrderPayload`, `NotifPrincipalPayload`) — `NotifEntity.kt`
- `PHBarangayEntity`, `PHCityEntity`, `PHProvinceEntity` — Philippines address lookup tables.
- `PrincipalEntity`, `PrincipalItemEntity`, `PrincipalPickerEntity` — `PrincipalEntity.kt`, `PrincipalItemEntity.kt`, `PrincipalPickerEntity.kt`
- `PromoEntity`, `PromoReqEntity`, `PromoBonusEntity`, `PromoData` — `PromoEntity.kt` (composite PKs on req/bonus; `@Relation` view aggregates header + requirements + bonus)
- `SearchItemEntity` — `SearchItemEntity.kt`

**DAOs** (all under `data/room/dao/`, all extending the shared `BaseDao<T>` from `BaseDao.kt:8-14` which provides `replace` / `insertIgnore`)
- `AddressDao`, `BannerDao`, `CartDao`, `CartDetailDao`, `CategoryItemDao`, `ChatDao`, `ConfigListDao`, `HistoryDao`, `HistoryDetailDao`, `HotItemDao`, `ImageDao`, `LastItemDao`, `NotifDao`, `PHBarangayDao`, `PHCityDao`, `PHProvinceDao`, `PrincipalDao`, `PrincipalItemDao`, `PrincipalPickerDao`, `PromoBonusDao`, `PromoDao`, `PromoReqDao`, `SearchItemDao`.
- Reads predominantly expose `Flow<List<...>>` (e.g. `CartDao.getFlow()` at `CartDao.kt:9-10`, `ChatDao.getFlow()` at `ChatDao.kt:12-13`) — this is the integration point with the StateFlow/UIState layer described in [[uistate-pattern]].
- `@Transaction` helpers wrap multi-table writes: `CartDao.insertCartEntity` (`CartDao.kt:21-25`), `PromoDao.refresh` (`PromoDao.kt:25-29`), `AddressDao.onlineInsert` / `upsertAddress` / `refresh` (`AddressDao.kt:46-73`), `ChatDao.replaceData` (`ChatDao.kt:41-46`).

**Migrations** (all anonymous `object : Migration(n, n+1)` declared inline in `DatabaseModule.kt:54-494`)
- Registered as a 23-step chain at `DatabaseModule.kt:28-50` — there is no `MIGRATIONS` array or vararg list; each migration is a separate `addMigrations(...)` call, one per line.
- Representative samples:
  - `db1to2` (`DatabaseModule.kt:54-124`) — bootstraps `LastItemEntity`, `ImageEntity`, `HistoryEntity`, `HistoryDetailEntity`; adds `principalId` to `PromoEntity` and wipes all three promo tables.
  - `db5to6` (`DatabaseModule.kt:158-172`) — loops over `LastItem/HotItem/SearchItem/PrincipalItem`, `DELETE FROM` then `ALTER TABLE ... ADD COLUMN unitPrice/basicPrice` (the established pattern for "new not-null column on an item cache").
  - `db8to9` / `db10to11` / `db11to12` (`DatabaseModule.kt:210-305`) — three successive `DROP TABLE HistoryDetailEntity` + recreate sequences, the only place tables are dropped/recreated.
  - `db14to15` (`DatabaseModule.kt:321-354`) — creates `PHProvinceEntity`, `PHCityEntity`, `PHBarangayEntity` (PH-only address lookup) for [[address-management]].
  - `db21to22` / `db22to23` / `db23to24` (`DatabaseModule.kt:438-494`) — same item-cache loop pattern adds `baseUOM`/`salesUOM`, then `InvQty`, then `aiReason`. Each wipes the cache before adding the not-null column.

## Depends on
- [[data]]
- [[di]]
- [[domain]]
- [[repository]]

## Depended on by
- [[address-management]]
- [[chat]]
- [[di-graph]]
- [[localization]]
- [[orders]]
- [[product-catalog]]
- [[promotions]]
- [[repository]]
- [[shopping-cart]]

## Gotchas
- **Per-call migration chain footgun (`DatabaseModule.kt:28-50`).** Each migration is registered with its own `addMigrations(...)` call. There is no array, no `MIGRATIONS.toTypedArray()`, no lint that says "is this number sequential?". Add a new `db24to25` and forget to add a 24th `.addMigrations(db24to25)` line and the build still compiles — the migration just never runs, and any user upgrading from v24 will crash on `IllegalStateException: A migration from 24 to 25 was required but not found`. Always grep `addMigrations` and confirm the count matches `version - 1`.
- **No `fallbackToDestructiveMigration*` configured.** A missing or buggy migration is a hard crash, not a wipe. Intentional, but worth knowing before shipping a release.
- **`exportSchema = true` (`LocalDatabase.kt:78`) without a schema dir argued in the gradle config.** Confirm the `room.schemaLocation` arg is set in `app/build.gradle.kts` before relying on it for diffing — `exportSchema` alone is silent if the kapt arg is missing.
- **The item-cache "wipe + ADD NOT NULL" pattern.** `db5to6`, `db21to22`, `db22to23`, `db23to24` all `DELETE FROM` the four/five item-cache tables and then `ALTER TABLE ... ADD COLUMN ... DEFAULT '...' NOT NULL`. The wipe is required because SQLite would otherwise reject the not-null add on existing rows even with a default in some Android versions; the side effect is **every app upgrade through one of these migrations empties the product cache** and the next open re-fetches from network. Don't be surprised by "empty home screen after install" reports immediately post-update.
- **`ConfigListEntity` schema/entity drift.** Migration `db17to18` (`DatabaseModule.kt:380-392`) creates `ConfigListEntity` with two columns (`Title`, `Function`). But the live entity (`ConfigListEntity.kt:9-13`) declares a third column, `DisplayNo: Int`. There is no follow-up migration adding `DisplayNo`. On a clean install Room creates the table from the entity (3 cols) and everything works; on an upgrade from v17→v18 the user keeps a 2-col table and the next query that mentions `DisplayNo` will throw. Either the field was added before v18 ever shipped, or this is a latent bug — verify before bumping the schema again.
- **No `@TypeConverters` anywhere in `data/room/`.** All complex payloads are persisted as `String` (e.g. `NotifEntity.payload` is a raw JSON blob deserialised at read time via Gson in `NotifEntity.toDomain()` at `NotifEntity.kt:23-43`). Booleans are stored as `INTEGER` per Room's default. Don't add a converter without auditing the existing string-blob columns first.
- **DAO `Flow` reads are hot — they re-emit on any table write.** Several `@Transaction` writes intentionally chain a `DELETE FROM` + bulk insert (e.g. `PromoDao.refresh`, `AddressDao.refresh`, `HistoryDao.deleteTable*` followed by `inserts`). Subscribers see a transient empty list mid-transaction in some Room versions; the `@Transaction` annotation on the DAO method is what suppresses that — don't remove it when refactoring.
- **`CartDetailEntity` has a no-arg secondary constructor (`CartEntity.kt:57-61`).** This exists so the gson/moshi layer in the [[repository]] can default-construct it; Room itself doesn't need it. Don't delete it on the assumption that it's dead.
- **`@Relation` views are not entities.** `CartWithDetailsEntity`, `PromoData`, `HistoryData`, `ChatData` are read-only projections — they cannot be inserted. Writes always go through the underlying `@Entity` table via the matching DAO.

## Open questions
- Is `room.schemaLocation` actually configured in `app/build.gradle.kts`? `exportSchema = true` is set but I didn't open the gradle file in this pass.
- Was the `ConfigListEntity.DisplayNo` column added between v17 release and v18 release (i.e. never reached production at v17), or is upgrading from v17→v18 currently broken in the wild?
- Why are `db1to2`'s three `DELETE From Promo*` statements at the end (`DatabaseModule.kt:120-122`) — was promo data corrupted at v1, or is this defensive? No comment explains it.
- The `autoMigrations = [AutoMigration(from = 2, to = 3)]` line (`LocalDatabase.kt:79`) is commented out — was auto-migration tried and abandoned, or aspirational?
