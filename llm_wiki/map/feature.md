---
title: feature — package
slug: feature
type: map
verified: 2026-05-23
sources:
  - app/src/main/java/com/b2b/online/feature/
---

# feature — package

**User-facing screens (Activities, Fragments, ViewModels) grouped by feature area.**

## What it does
The presentation layer. Each subfolder is a self-contained feature with one
or more Fragments/Activities, ViewModels, and supporting types. Hosts the
single `MainActivity` and `MainViewModel` at the package root that orchestrate
top-level navigation.

## Where it lives
- `app/src/main/java/com/b2b/online/feature/MainActivity.kt` (19.6 KB) — root host.
- `app/src/main/java/com/b2b/online/feature/MainViewModel.kt` (6.6 KB).
- Subfolders: `auth/`, `brand/`, `cart/`, `chat/`, `dialogs/`, `exception/`,
  `history/`, `home/`, `item/`, `notif/`, `order/`, `profile/`,
  `viewholder/`, `voucher/`.

## Depends on
- [[data]]
- [[domain]]
- [[base]]
- [[uistate-pattern]]
- [[di]]

## Depended on by
- [[address-management]]
- [[authentication]]
- [[chat]]
- [[orders]]
- [[product-catalog]]
- [[push-notifications]]
- [[shopping-cart]]

## Gotchas
- Almost every Fragment follows the [[uistate-pattern]]: `Fragment` collects
  `UIState<T>` from its `ViewModel`, which in turn talks to `Repository`.
- `MainActivity.kt` is the navigation host; deep links and bottom-nav routing
  live there. Worth a deep page if it grows further.
- `dialogs/`, `exception/`, and `viewholder/` are *shared* across features —
  they aren't a feature themselves.

## Open questions
- Whether each feature uses its own Navigation graph or one global graph
  rooted in `MainActivity`.
