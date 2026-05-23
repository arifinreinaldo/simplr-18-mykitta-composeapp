---
title: Localization (PH / SG)
slug: localization
type: deep
verified: 2026-05-23
sources:
  - app/src/main/java/com/b2b/online/util/CountryHelper.kt
  - app/src/main/java/com/b2b/online/util/PhoneWatcher.kt
  - app/src/main/java/com/b2b/online/util/Helper.kt
  - app/src/main/java/com/b2b/online/domain/CountryData.kt
  - app/src/main/java/com/b2b/online/feature/auth/country/CountryListFragment.kt
  - app/src/main/java/com/b2b/online/data/RepositoryPHLocation.kt
  - app/src/main/java/com/b2b/online/base/C.kt
  - app/src/main/java/com/b2b/online/di/HelperModule.kt
  - app/src/main/java/com/b2b/online/di/ActivityRetainModule.kt
  - app/src/main/java/com/b2b/online/feature/auth/AuthViewModel.kt
  - app/src/main/assets/mapping/
  - cities-mapping/
---

# Localization (PH / SG)

**Multi-locale support (PH / SG) including phone watchers and country helpers.**

## What it does
The app supports two markets — Philippines (PH) and Singapore (SG) — selected
once at first launch and persisted in `SharedPreferences` under `C.COUNTRY`.
That single string ("PHILIPPINE" or "SINGAPORE") drives currency formatting,
tax labels (VAT vs GST), phone validation/masking, Retrofit base URL selection,
and which register / address-edit flow renders. PH additionally ships a
four-level address hierarchy (region implicit → province → city/municipality →
barangay) sourced from CSVs bundled in `assets/mapping/`; SG uses a flat
address form. There is no Android `values-XX` resource localization — strings
live in the single `values/strings.xml`; "localization" here means market
configuration, not UI translation.

## Where it lives
- `app/src/main/java/com/b2b/online/util/Helper.kt:225` — `enum class Country { INDETERMINATE, SINGAPORE, PHILIPPINE }` (the canonical token; stringified into prefs).
- `app/src/main/java/com/b2b/online/util/CountryHelper.kt:9` — constructed with the country string. Key methods: `getIsoCode()` (L35), `getTaxRate()` (L36, SG 0.08 / PH 0.12), `getCountryCode()` (L39, "65"/"63"), `getPhonePrefix()` (L42), `getPhoneFormat()` (L106, "#### ####" vs "### ### ####"), `cleanNumber()` (L52), `validatePhoneField()` (L72), `formatCurrency()` (L88), `getTaxDescription()` (L110, "GST(Exclusive/Inclusive)" vs "VAT").
- `app/src/main/java/com/b2b/online/util/PhoneWatcher.kt:8` — `TextWatcher` that masks input live using `countryHelper.getPhoneFormat()` and strips the country-code prefix on paste.
- `app/src/main/java/com/b2b/online/domain/CountryData.kt:5` — picker row model (flag drawable, name, "+63"/"+65", ISO).
- `app/src/main/java/com/b2b/online/feature/auth/country/CountryListFragment.kt:24` — the only picker UI; reads `TelephonyManager.networkCountryIso` (L62) and prompts confirmation; calls `AuthViewModel.setCountry()` (L90).
- `app/src/main/java/com/b2b/online/feature/auth/AuthViewModel.kt:219` — `setCountry()` writes `C.COUNTRY`; `loadPHData()` (L99) triggers `RepositoryPHLocation.insertCSV()` once, gated by `C.ISLOADCSV`.
- `app/src/main/java/com/b2b/online/data/RepositoryPHLocation.kt:59` — `insertCSV()` reads `assets/mapping/{province,city,barangays}.csv` and bulk-inserts into Room DAOs (`phProvinceDao`, `phCityDao`, `phBarangayDao`); also exposes online fetchers via `PHLocationService`.
- `app/src/main/java/com/b2b/online/base/C.kt:11` — `const val COUNTRY = "Country"` (prefs key); L17 `ISLOADCSV` gates the one-time CSV import.
- `app/src/main/java/com/b2b/online/di/HelperModule.kt:21` — provides `CountryHelper` from the persisted country string at `ActivityRetainedScoped`.
- `app/src/main/java/com/b2b/online/di/ActivityRetainModule.kt:48` — `@Named("basePost")` switches Retrofit base URL between `BuildConfig.API_POST_PH` and `BuildConfig.API_POST` based on `C.COUNTRY`.
- `app/src/main/assets/mapping/{province,city,barangays}.csv` — bundled PH address data (barangays.csv is ~3 MB).
- `cities-mapping/` (project root) — source CSVs (`provinces.csv`, `city-municipality.csv`, `barangays.csv`) that the bundled assets are derived from; not shipped in the APK.

## Depends on
- [[util]]
- [[domain]]
- [[di]]
- [[base]]
- [[repository]]
- [[room-database]]
- [[authentication]]
- [[address-management]]
- [[build-variants]]

## Depended on by
- [[address-management]]
- [[authentication]]
- [[di-graph]]
- [[orders]]
- [[product-catalog]]

## Gotchas
- **Country switch is destructive to the Retrofit graph.** `basePost` (DI/ActivityRetainModule.kt:48) reads `C.COUNTRY` *once* at retained-component creation. Changing country mid-session leaves Retrofit pointing at the old base URL; the only correct switch path is sign out → relaunch the country picker. There is no in-app "change country" affordance for a logged-in user.
- **`CountryHelper` is `ActivityRetainedScoped`.** Same caveat: its `currentLocale`, `formatCurrency`, etc. are captured at construction. A fresh instance is needed after `C.COUNTRY` changes.
- **`Locale("en_SG", "SG")`** in `CountryHelper.kt:12` passes `"en_SG"` as the *language* tag — this is malformed (should be `Locale("en", "SG")`). It still produces a usable `country = "SG"` because the second arg sets the region, but the language portion is non-standard and could confuse `NumberFormat`/`PhoneNumberUtils` on some OEM ROMs.
- **PH has 4-level address; SG is flat.** PH register/edit fragments live under `feature/auth/register/ph/` and `feature/profile/address/edit/ph/`; SG counterparts under `.../sg/` use a single address-line form. No shared address-form abstraction — each side is duplicated.
- **CSV import is one-shot and synchronous-ish.** `insertCSV()` runs on `Dispatchers.IO` in `AuthViewModel.loadPHData()` but blocks the PH address picker until done. The ~3 MB `barangays.csv` parse on first launch is the dominant cost; the `ISLOADCSV` flag prevents re-import but is not invalidated when the CSVs are updated in a new release — bump the flag key or clear it on version change if you ship new mappings.
- **Missing-locale fallback silently defaults to SG.** `CountryHelper.kt:19` and the `getCountryCode()`/`validatePhoneField()` `else` branches return `""` / `false` rather than throwing. An empty `C.COUNTRY` (fresh install, pre-picker) means phone validation always fails and tax rate is `0.0` — fine because the auth gate forces picker selection first, but easy to forget in tests.
- **No `values-XX/strings.xml`.** All copy is English in a single `values/` resource set. PH-specific labels (e.g. "Barangay") are inlined as English strings, not translated.
- **`networkCountryIso` auto-suggest is a soft confirm.** The picker prompts even when the SIM matches a supported country; the user can decline and pick the other market.

## Open questions
- Are the bundled `assets/mapping/*.csv` and the root `cities-mapping/*.csv` kept in sync, and what is the regeneration process? (Filenames differ: `province.csv` vs `provinces.csv`, `city.csv` vs `city-municipality.csv`.)
- Is SG genuinely feature-equivalent, or are PH-only flows (e.g. barangay-aware delivery, VAT-inclusive pricing) the primary product and SG a thinner variant? Code volume under `.../ph/` is noticeably larger than under `.../sg/`.
- Should `CountryHelper`'s Singapore branch use `Locale("en", "SG")` instead of `Locale("en_SG", "SG")`? No test currently asserts the resulting locale tag.
