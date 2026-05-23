---
title: Orders
slug: orders
type: feature
verified: 2026-05-23
sources:
  - app/src/main/java/com/b2b/online/feature/order/OrderFragment.kt
  - app/src/main/java/com/b2b/online/feature/order/OrderViewModel.kt
  - app/src/main/java/com/b2b/online/feature/order/detail/OrderDetailFragment.kt
  - app/src/main/java/com/b2b/online/feature/order/detail/OrderDetailViewModel.kt
  - app/src/main/java/com/b2b/online/feature/history/HistoryFragment.kt
---

# Orders

**Order listing and detail; feature/order/ and feature/history/.**

## What it does
Renders the user's past and in-flight orders as a status-tabbed list, and drills into a single invoice for full breakdown. `HistoryFragment` is the screen-level host: it owns a `ViewPager2` whose tabs come from `C.statusDocument` (e.g. one tab per order status). Each tab page is an `OrderFragment` instance filtered by that status string, so "history vs order" here is not two screens but the same screen rendered with a different `status` argument — every status (including current/in-progress and finished/cancelled) lives under `HistoryFragment`. `OrderDetailFragment` shows invoice header, line items grouped by `PromoId`, bonus items, shipping address, and a payment breakdown (subtotal, discount, GST). When an order's `invStatus == "Finished"` and its promo name ends with `#OK`, a per-group **Reorder** button appears that re-opens the original promotion's purchase dialog via `vm.getPromoDetail(...)`.

## Where it lives
- `app/src/main/java/com/b2b/online/feature/history/HistoryFragment.kt:18` — tabbed host; `HistoryTabAdapter:51` creates one `OrderFragment` per status.
- `app/src/main/java/com/b2b/online/feature/order/OrderFragment.kt:34` — paginated list of `HistoryData` rows; `refreshData():129` resets offset + clears + refetches; `setupDisplay():135` binds each row and wires click → `listener.goToHistory(invNo)`.
- `app/src/main/java/com/b2b/online/feature/order/OrderViewModel.kt:19` — `fetch():29` calls `repository.getHistoryList(...)`; `getHistoryFlow():38` subscribes to the Room-backed flow (by-principal or by-status).
- `app/src/main/java/com/b2b/online/feature/order/detail/OrderDetailFragment.kt:46` — invoice screen; `setupObserver():85` renders header, line groups, bonus, and payment; opens `SupportFragment` and a camera-based photo upload.
- `app/src/main/java/com/b2b/online/feature/order/detail/OrderDetailViewModel.kt:24` — `getUnifiedOrderDetail():38` pulls the full `HistoryData`; `getPromoDetail():54` powers reorder; `updateNotif():62` marks the source notification read when arrived via deep link.

## Depends on
- [[repository]]
- [[room-database]]
- [[uistate-pattern]]
- [[domain]]
- [[feature]]
- [[shopping-cart]]
- [[promotions]]
- [[push-notifications]]
- [[localization]]

## Depended on by
- [[authentication]]
- [[product-catalog]]
- [[repository]]
- [[shopping-cart]]

## Gotchas
- **Status → UI mapping is string-based.** The row's `status` label is `"Cancel"` if `item.history.isCancel` is true, otherwise the raw `invStatus` string from the server. Detail screen's reorder visibility hard-codes `invStatus == "Finished"` (`OrderDetailFragment.kt:144`) — any server-side rename of that literal silently breaks reorder.
- **History vs Order is just an argument.** `HistoryFragment` always passes an empty `principalId` and iterates `C.statusDocument` for the status arg. `OrderFragment` can also be reached with a non-empty `principalId` (e.g. from a principal/supplier screen), in which case `OrderViewModel.getHistoryFlow` switches to `getHistoryFlowByPrincipal` and ignores the status filter.
- **Refresh triggers.** Pull-to-refresh and `onResume()` both call `refreshData()`, which zeroes `vm.offset`, clears the adapter datasource, and re-runs `fetch(true)`. Scroll-to-end (`listenerScroll`) calls `fetch(false)` for pagination. `onStop()` saves `RecyclerView` layout state into `SavedStateHandle` keyed by `"$principalId&&$statusOrder"` and restores on next `setupDisplay`.
- **Reorder gating is double-conditioned.** The button only shows when the order is `Finished` AND at least one promo line's `PromotionName` ends with the `#OK` sentinel — a server-side flag smuggled through the promo name suffix. Tapping it re-fetches the promo variant list and pops the same purchase dialog used in [[product-catalog]] / [[shopping-cart]] flows (see `showPurchaseDialog` in `feature/item/detail`).
- **No cancel/refund UI here.** Cancel state arrives pre-baked from the server via `isCancel`; the detail screen exposes only `contactSupport` and an unattached `uploadPhoto` (camera capture compresses via Compressor but the result is logged, not uploaded — see `OrderDetailViewModel.compressImage:47`).
- **Filter button is dead.** `binding.filter.button.setOnClickListener { }` in `OrderFragment` is wired but empty.
- **Relationship to the cart's result screen.** Cart checkout (see [[shopping-cart]]) lands on a confirmation/result screen, not here; orders show up in this list only after the next Room sync from `Repository.getHistoryList`. There is no direct navigation edge from cart-success → `OrderDetailFragment`.

## Open questions
- What is the canonical set of `C.statusDocument` values, and does the backend guarantee `invStatus` matches one of them exactly (case-sensitive)?
- The `uploadPhoto` button captures and compresses an image but the result is only logged — is this a feature stub (e.g. proof-of-delivery upload) that was never wired to `Repository`?
- `OrderDetailViewModel` exposes `_history` and `_data` flows that no observer in the fragment consumes — dead code, or are they consumed elsewhere (e.g. a child fragment) that wasn't read during ingest?
- The `#OK` suffix convention on `PromotionName`: is this documented server-side, or an implicit contract?
