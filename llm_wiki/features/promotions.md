---
title: Promotions & vouchers
slug: promotions
type: feature
verified: 2026-05-23
sources:
  - app/src/main/java/com/b2b/online/feature/voucher/DetailVoucherFragment.kt
  - app/src/main/java/com/b2b/online/feature/voucher/DetailVoucherViewModel.kt
  - app/src/main/java/com/b2b/online/feature/voucher/VoucherDialogFragment.kt
  - app/src/main/java/com/b2b/online/feature/voucher/VoucherDialogViewModel.kt
  - app/src/main/java/com/b2b/online/feature/voucher/VoucherFragment.kt
  - app/src/main/java/com/b2b/online/feature/voucher/VoucherViewModel.kt
  - app/src/main/java/com/b2b/online/feature/item/promo/PromoFragment.kt
  - app/src/main/java/com/b2b/online/feature/item/promo/PromoViewModel.kt
  - app/src/main/java/com/b2b/online/domain/Promo.kt
  - app/src/main/java/com/b2b/online/data/Repository.kt
---

# Promotions & vouchers

**Vouchers, promo items, and promo logic; feature/voucher/, feature/item/promo/, domain/Promo.kt.**

## What it does

Two distinct surfaces sit on top of the same promotional backbone. The **voucher** flow (`feature/voucher/`) is a loyalty-points redemption catalog: `VoucherFragment` lists redeemable items via `GetRedemptionItems`, `DetailVoucherFragment` shows one voucher with image slider + applicable promotion variants, and `VoucherDialogFragment` is the bottom-sheet "redeem N for X points" confirmation that calls `doPostRedeem` and refreshes the user's loyalty balance. The **promo** flow (`feature/item/promo/`) is a per-principal promotion browser: `PromoFragment` renders `Promo` rows (FOC qty / % discount / cash-off) sourced from `promoDao().getPromoByPrincipal` and forwards taps to the shared `showPurchaseDialog`. Cart-side promo math (group promotion check, line-item discount application) is **server-driven** via `Repository.doCheckGroupPromotion` — see [[shopping-cart]] for the cart-integration side.

## Where it lives

Voucher surface:
- `app/src/main/java/com/b2b/online/feature/voucher/VoucherFragment.kt:35` — grid/list of redeemable items, search + display-mode toggle, paginated via `scrollListener`.
- `app/src/main/java/com/b2b/online/feature/voucher/VoucherViewModel.kt:21` — `getRedemptionItemList` calls, query/sort/price filter setters, `getSearchFlow` for room-backed paging.
- `app/src/main/java/com/b2b/online/feature/voucher/DetailVoucherFragment.kt:46` — voucher detail (slider, promotion variants RV, CTA to redeem).
- `app/src/main/java/com/b2b/online/feature/voucher/DetailVoucherViewModel.kt:24` — `getItemPromotionList`, `getImageFlow`, `getPrincipalById`.
- `app/src/main/java/com/b2b/online/feature/voucher/VoucherDialogFragment.kt:34` — bottom-sheet redeem confirmation; `showVoucherDialog` extension at `:169`.
- `app/src/main/java/com/b2b/online/feature/voucher/VoucherDialogViewModel.kt:32` — qty debounced via `_clickAction.debounce(100)`; `doSave` posts to `doPostRedeem`; `points = (qty * itemVoucher).toInt()` (client multiplies, server authorises).

Promo surface:
- `app/src/main/java/com/b2b/online/feature/item/promo/PromoFragment.kt:31` — promo list per principal, swipe-to-refresh, formats bonus copy (FOC / Discount / DiscAmount).
- `app/src/main/java/com/b2b/online/feature/item/promo/PromoViewModel.kt:17` — pairs a local flow (`getPromoByPrincipal`) with a network refresh (`getItemPrincipalList`).

Domain & wiring:
- `app/src/main/java/com/b2b/online/domain/Promo.kt:3` — `Promo`, `PromoBonus`, `PromoTerms` data classes (no client-side discount calc; carriers only).
- `app/src/main/java/com/b2b/online/data/Repository.kt:316` `getItemPromotionList`, `:338` `getItemPrincipalList`, `:592` `getPromoByPrincipal` (local), `:1203` `getRedemptionItemList`, `:1225` `doCheckGroupPromotion`, `:1276` `doPostRedeem`.
- `app/src/main/java/com/b2b/online/util/Helper.kt:65` — `PREFIX_DEF_PROMO = "DEFAULT"`; promotionDesc with this prefix is relabelled "Retail price" in UI.

## Depends on

- [[repository]]
- [[room-database]]
- [[domain]]
- [[uistate-pattern]]
- [[shopping-cart]]
- [[product-catalog]]

## Depended on by
- [[orders]]
- [[product-catalog]]
- [[repository]]
- [[shopping-cart]]

## Gotchas

- **Discount math is server truth.** `Repository.doCheckGroupPromotion` (`:1225`) is the canonical path: the server returns `details[]` with `discount` / `discountAmount` / `isUpdate`, and the local cart rows are mutated via `cartDetailDao().updateDiscount` / `updateDiscountAmount`. A commented block at `Repository.kt:1246-1257` shows a former *client-side* "if sum > 200 apply 30 off" rule — that logic is **dead** and intentionally left in as a marker. Do not resurrect client discount math; the server owns it.
- **`updateDiscountAmount` bug surface.** `Repository.kt:1243` passes `it.discount` (not `it.discountAmount`) into `updateDiscountAmount` — likely a copy-paste defect. Verify before relying on cash-off promos in cart.
- **Voucher-redeem race.** `VoucherDialogViewModel.doSave` (`:54`) inserts a `delay(100)` before `doPostRedeem`; the dialog dismisses on `SuccessFromRemote` and only *then* calls `mainVM.getLoyaltyPoints()` to refresh balance (`VoucherDialogFragment.kt:89`). Double-tapping "Yes" on the confirmation dialog isn't guarded — the bottom-sheet stays open until the response arrives, so a fast retap can fire a second redeem with the same qty before the first completes.
- **Quantity ceiling is local-only.** `VoucherDialogViewModel.updateTextQuantity` (`:106`) clamps to `maxLoyalty / itemVoucher`, but `maxLoyalty` is the value passed in at dialog construction (`showVoucherDialog(... mainVM.loyaltyPoint.value ...)`); if the user redeems elsewhere mid-session the dialog will not re-clamp.
- **`PREFIX_DEF_PROMO` is a magic-string contract.** `promotionDesc` / `promoName` starting with `"DEFAULT"` means "this is the no-promo retail price"; the UI swaps the label and `CheckPromotionResponse.kt:18` treats it as always-active. Renaming the constant requires server alignment.
- **Promo expiry is display-only.** `PromoFragment.kt:101` renders `validStart … validEnd` via `convertPromo()` but does **not** filter expired promos client-side; the API is expected to omit expired rows. A stale Room cache can therefore surface expired promos until the next refresh.
- **Two RV setups on one screen.** `DetailVoucherFragment.kt:177-198` keeps a fully commented suggestion-list block and `vm.suggestionList` is still observed at `:267`; the state flow exists but is never emitted to. Looks dead but the observer is wired — leave it alone unless you also strip the flow in `DetailVoucherViewModel.kt:35` and the commented `getItemSuggestion` at `:74`.
- **`PromoFragment` inflates twice.** Both `BaseFragment(R.layout.fragment_promo)` and an `onCreateView` override (`:43`) inflate the same layout. Harmless duplication; the override wins.

## Open questions

- Is the `updateDiscountAmount(it.discount, …)` at `Repository.kt:1243` a real bug or an intentional fallback? No tests cover it.
- Does the server ever return promos with `validEnd` in the past, or is client-side expiry filtering definitively unnecessary?
- `VoucherDialogViewModel` exposes an unused `_isSuccess` LiveData (`:36`) — vestigial or planned re-use?
- `Promo.promoId` vs `PromoBonus.PromoID` / `PromoTerms.PromoID` casing mismatch — Gson field-name binding only, or a sign of two response shapes being merged?
