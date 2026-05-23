---
title: Address management
slug: address-management
type: feature
verified: 2026-05-23
sources:
  - app/src/main/java/com/b2b/online/feature/profile/address/AddressFragment.kt
  - app/src/main/java/com/b2b/online/feature/profile/address/AddressViewModel.kt
  - app/src/main/java/com/b2b/online/feature/profile/address/edit/ph/EditAddressPHFragment.kt
  - app/src/main/java/com/b2b/online/feature/profile/address/edit/ph/EditAddressPHViewModel.kt
  - app/src/main/java/com/b2b/online/feature/profile/address/edit/sg/EditAddressFragment.kt
  - app/src/main/java/com/b2b/online/feature/profile/address/edit/sg/EditAddressViewModel.kt
  - app/src/main/java/com/b2b/online/domain/Address.kt
  - app/src/main/java/com/b2b/online/data/RepositoryPHLocation.kt
  - app/src/main/java/com/b2b/online/data/api/PHLocationService.kt
  - app/src/main/java/com/b2b/online/util/MyLocationListener.kt
---

# Address management

**Address CRUD and selection under feature/profile/address/.**

## What it does
The list screen renders the user's shipping addresses from Room (offline-first), tapping the star toggles the default/selected address, the toolbar `addMenu` opens an empty edit form, and tapping a row opens an edit form pre-filled from the `Address` parcelable. Branching by `CountryHelper.country`: Singapore routes to a flat free-form `EditAddressFragment`, Philippines routes to `EditAddressPHFragment` which forces a 4-level hierarchy — region/province → city/municipality → barangay → subdivision — wired so that setting `addressProvince` triggers `getCities(...)` and setting `addressCity` triggers `getBarangay(...)` via property setters on the VM. Lookups come from Room tables seeded by `RepositoryPHLocation.insertCSV()` from `assets/mapping/{province,city,barangays}.csv` (semicolon-delimited; barangay `city` falls back to column 5 when column 4 is empty). `PHLocationService` (Retrofit) provides equivalent remote endpoints but the edit VM uses the local DB path exclusively. The address feature itself does **not** use map/current-location — `MyLocationListener` exists in `util/` but is only wired into the PH registration flow.

## Where it lives
- `feature/profile/address/AddressFragment.kt:32` — list screen; collects `vm.roomAddress` into a `recyclical` data source.
- `feature/profile/address/AddressFragment.kt:80` — star tap calls `vm.updateAddress(item)` to set default.
- `feature/profile/address/AddressFragment.kt:85` — country-branched navigation to SG vs PH edit screen.
- `feature/profile/address/AddressViewModel.kt:23` — `init` subscribes to `repository.getShipmentFlow()`; also calls `getShipmentList("0", ...)` for remote sync.
- `feature/profile/address/AddressViewModel.kt:41` — `updateAddress` -> `repository.updateSelectedAddress`.
- `feature/profile/address/edit/ph/EditAddressPHFragment.kt:36` — kicks off `vm.getProvince()`; observes 3 cascading spinners.
- `feature/profile/address/edit/ph/EditAddressPHViewModel.kt:36` — `addressProvince` setter cascades to `getCities(value)`.
- `feature/profile/address/edit/ph/EditAddressPHViewModel.kt:41` — `addressCity` setter cascades to `getBarangay(value)`.
- `feature/profile/address/edit/ph/EditAddressPHViewModel.kt:111` — `doValidate()` returns per-field `Pair<String, Boolean>` consumed by the fragment to drive per-input error text.
- `feature/profile/address/edit/sg/EditAddressFragment.kt:72` — pre-fills from `args.address`; gates editing via `vm.getUser() == CustNo`.
- `feature/profile/address/edit/sg/EditAddressViewModel.kt:102` — `validate()` branches on `countryHelper.getIsoCode() == "SG"` to choose a 5-field vs 8-field rule set.
- `domain/Address.kt:7` — `@Parcelize` model used across nav args; includes PH-only fields (`addressBarangay`, `addressProvince`, `addressSubdivision`, `addressCity`) even when used by the SG flow.
- `data/RepositoryPHLocation.kt:59` — `insertCSV()` loads `mapping/*.csv` into Room.
- `data/api/PHLocationService.kt:9` — Retrofit endpoints for province/city/barangay (currently unused by the edit VM; the DB path wins).

## Depends on
- [[feature]]
- [[domain]]
- [[data]]
- [[repository]]
- [[room-database]]
- [[uistate-pattern]]
- [[localization]]
- [[di-graph]]
- [[util]]

## Depended on by
- [[localization]]
- [[repository]]
- [[shopping-cart]]

## Gotchas
- **PH-only picker.** The 4-level cascading dropdowns and the `EditAddressPHFragment` exist only for PH; SG uses a free-form `outletAddress` text field plus a flat `zipCode`. Any country other than `Country.PHILIPPINE` falls through to the SG fragment (`AddressFragment.kt:100`).
- **CSV-backed, not API-backed.** `EditAddressPHViewModel.getProvince/getCities/getBarangay` read from `phProvinceDao/phCityDao/phBarangayDao`, never from `PHLocationService` — the Retrofit service in `data/api/PHLocationService.kt` is dead at the edit-form layer (the DAO data is seeded by `RepositoryPHLocation.insertCSV` reading `assets/mapping/*.csv`). If the CSVs ship stale, the picker is stale; bumping a Room migration without re-seeding may surface empty spinners.
- **CSV column 4-vs-5 fallback for barangay city** (`RepositoryPHLocation.kt:113`) — quietly silent if both columns are blank; bad rows just log to `TAG` and continue.
- **Cascading reset is in the fragment, not the VM** (`EditAddressPHFragment.kt:218`/`229` — `outletCity.reset()` / `outletBarangay.reset()`). Changing province programmatically without going through the spinner's `addTextChanged` will leave stale city/barangay state on the VM.
- **`AddressRequest` field order is positional** (`EditAddressPHViewModel.kt:60`); `addressDetail` is passed twice (positions 3 and 4) and `addressName` is passed twice (positions 2 and 8) — easy to break by re-ordering constructor args.
- **SG edit gates UI by `CustNo == currentUser`** (`EditAddressFragment.kt:86`): viewing another user's address quietly disables the update button without an explicit empty/disabled state.
- **No map / no current-location** in this feature. `util/MyLocationListener.kt` is only wired into PH registration (`feature/auth/register/ph/RegisterPHFragment.kt`), not into the address edit flow.
- **Validation differs by country in two places**: PH uses `EditAddressPHViewModel.doValidate()` returning per-field errors; SG uses `EditAddressViewModel.validate()` emitting a single boolean into `isValidate`. Both live in `feature/profile/address/edit/`.
- `AddressViewModel.init` already calls `getAddress(listOf())`; `AddressFragment.onViewCreated` calls it again — harmless duplicate fetch.

## Open questions
- Why is `PHLocationService` retained if all reads go through Room? Is there a sync path elsewhere that refreshes the CSV-seeded tables from the network, or is the Retrofit service truly unused?
- The CSV format encodes a boolean at column 4 of `city.csv` (mapped to `PHCityEntity` "isXXX"); the field's meaning isn't documented here.
- `Address.isSelected` is parceled but unclear whether the server round-trips it or it is purely a local Room flag flipped by `updateSelectedAddress`.
- No delete UI path is visible in the list/edit fragments inspected; whether "delete" is exposed elsewhere (e.g., a menu in the edit screen rendered by a layout-only control) is unverified.
