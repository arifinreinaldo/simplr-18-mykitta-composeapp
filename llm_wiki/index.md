# B2B-SIMPLR LLM Wiki — Index

Catalog of every wiki page, grouped by category. Each entry's summary line is
quoted verbatim from the corresponding page; drift between page and index is a
lint failure (see [SCHEMA.md](SCHEMA.md) §4 rule 8).

## Map (top-level packages)
- [[base]] — Base Activity / Fragment / Application classes and the `C` constants object.
- [[buildsrc]] — `buildSrc/` Kotlin configuration: `Library.kt` and `Versions.kt`.
- [[compose]] — Jetpack Compose surface area: ComposeActivity plus `screen/` and `ui/` packages.
- [[data]] — Network + persistence layer: Repository facade, Retrofit API services, Room database.
- [[di]] — Hilt modules wiring the dependency graph.
- [[domain]] — Plain Kotlin model classes shared across layers, including UIState.
- [[feature]] — User-facing screens (Activities, Fragments, ViewModels) grouped by feature area.
- [[util]] — Cross-cutting utilities: messaging receivers, helpers, custom views, view-binding delegate.
- [[view-module]] — The `:view` Gradle module providing reusable XML view components.
- [[worker]] — Background WorkManager jobs.

## Deep (hot spots)
- [[build-variants]] — Three variants — debug, release, demo — plus Firebase App Distribution wiring.
- [[di-graph]] — Hilt module wiring across SingletonModule, DatabaseModule, ActivityRetainModule, SharedPrefModule, HelperModule.
- [[localization]] — Multi-locale support (PH / SG) including phone watchers and country helpers.
- [[push-notifications]] — Dual messaging stack: Firebase Cloud Messaging and Huawei HMS Push, with shared dispatch logic.
- [[repository]] — Repository.kt: the 1294-line facade through which all features talk to network + cache.
- [[room-database]] — Room schema, entities, DAOs, and the 23 migrations from db1to2 through db23to24.
- [[uistate-pattern]] — UIState<T> sealed class plus the Fragment / ViewModel convention every feature uses.

## Features
- [[address-management]] — Address CRUD and selection under feature/profile/address/.
- [[authentication]] — Login, register, OTP waiting, country picker; feature/auth/.
- [[chat]] — Contact list, chat threads, and messaging; feature/chat/.
- [[orders]] — Order listing and detail; feature/order/ and feature/history/.
- [[product-catalog]] — Home, brand, item browsing and search; feature/home/, feature/brand/, feature/item/.
- [[promotions]] — Vouchers, promo items, and promo logic; feature/voucher/, feature/item/promo/, domain/Promo.kt.
- [[shopping-cart]] — Cart, checkout, payment, result screens; feature/cart/.
