---
title: domain — package
slug: domain
type: map
verified: 2026-05-23
sources:
  - app/src/main/java/com/b2b/online/domain/
---

# domain — package

**Plain Kotlin model classes shared across layers, including UIState.**

## What it does
Holds the model types that flow between Repository, ViewModel, and UI. These
are plain Kotlin classes — no Room/Retrofit annotations live here; entity and
DTO variants live under [[data]]. The `UIState<T>` sealed class is the
canonical envelope every feature uses for view-state.

## Where it lives
- `app/src/main/java/com/b2b/online/domain/` — 21 files.
- Notable: `UIState.kt`, `Cart.kt`, `Item.kt`, `Address.kt`, `User.kt`,
  `Principal.kt`, `Promo.kt`, `Notif.kt`, `Payment.kt`, `Subtotal.kt`,
  `QtyInsufficientResponse.kt`, `Variant.kt`, `Category.kt`, `Banner.kt`,
  `Loading.kt`, `Line.kt`, `Image.kt`, `ItemDisplay.kt`, `ConfigList.kt`,
  `CountryData.kt`, `PrincipalPicker.kt`.

## Depends on

## Depended on by
- [[address-management]]
- [[authentication]]
- [[chat]]
- [[compose]]
- [[data]]
- [[di]]
- [[feature]]
- [[localization]]
- [[orders]]
- [[product-catalog]]
- [[promotions]]
- [[push-notifications]]
- [[repository]]
- [[room-database]]
- [[shopping-cart]]
- [[uistate-pattern]]
- [[util]]

## Gotchas
- `UIState<T>` is not just another model — it's the contract every
  ViewModel-Fragment pair uses. See [[uistate-pattern]] before adding new
  states.
- Some files are very small (e.g. `Line.kt` 71 B); they exist as marker types,
  not stubs.

## Open questions
- Are the DTOs in `data/api/request/` and `data/api/response/` strictly
  separate from these domain classes, or do features sometimes consume DTOs
  directly?
