---
title: Shopping cart
slug: shopping-cart
type: feature
verified: 2026-05-23
sources:
  - app/src/main/java/com/b2b/online/feature/cart/CartFragment.kt
  - app/src/main/java/com/b2b/online/feature/cart/CartViewModel.kt
  - app/src/main/java/com/b2b/online/feature/cart/checkout/CheckoutFragment.kt
  - app/src/main/java/com/b2b/online/feature/cart/checkout/CheckoutViewModel.kt
  - app/src/main/java/com/b2b/online/feature/cart/checkout/PaymentDialogFragment.kt
  - app/src/main/java/com/b2b/online/feature/cart/payment/PaymentFragment.kt
  - app/src/main/java/com/b2b/online/feature/cart/payment/GatewayFragment.kt
  - app/src/main/java/com/b2b/online/feature/cart/result/ResultFragment.kt
  - app/src/main/java/com/b2b/online/domain/Cart.kt
  - app/src/main/java/com/b2b/online/domain/Subtotal.kt
  - app/src/main/java/com/b2b/online/domain/Payment.kt
  - app/src/main/java/com/b2b/online/domain/QtyInsufficientResponse.kt
  - app/src/main/java/com/b2b/online/util/CartDataGenerator.kt
---

# Shopping cart

**Cart, checkout, payment, result screens; feature/cart/.**

## What it does
Manages the buyer's cart line items, computes subtotals/discounts/GST per
principal (supplier), then drives a linear flow `Cart -> Checkout -> Payment
method dialog -> POST order -> Result`. Cart lines are grouped by `Principal`
(supplier) — each group renders a header, its `Cart` rows (with optional bonus
rows), and a `Subtotal` block for subtotal/discount/GST. Promotions are
re-validated against the server every time the cart contents change
(`CartViewModel.doCheck` / `doCheckCart`). Final totals are recomputed locally
just before submit and posted together with the selected shipping `Address`.

## Where it lives
- Cart screen — `app/src/main/java/com/b2b/online/feature/cart/CartFragment.kt:44` (entry) and `CartFragment.kt:164` (observer wiring for `checkPromotion` / `checkGroupPromotion` / `mainVM.cartData`)
- Cart VM — `app/src/main/java/com/b2b/online/feature/cart/CartViewModel.kt:26`; promotion re-check `CartViewModel.kt:55` (`doCheck`) and `CartViewModel.kt:100` (`doCheckCart`)
- Checkout screen — `app/src/main/java/com/b2b/online/feature/cart/checkout/CheckoutFragment.kt:43`; submit handler at `CheckoutFragment.kt:215` opens the payment-method bottom sheet
- Checkout VM — `app/src/main/java/com/b2b/online/feature/cart/checkout/CheckoutViewModel.kt:31`; `doSubmit` at `CheckoutViewModel.kt:106` builds `CartHeaderRequest`s via `calculateCart` and calls `repo.doPostCart`
- Payment method bottom sheet — `app/src/main/java/com/b2b/online/feature/cart/checkout/PaymentDialogFragment.kt:18` (Cash / Transfer / Cheque buttons; PayPal SDK block is commented out)
- Payment list + gateway (currently unwired) — `app/src/main/java/com/b2b/online/feature/cart/payment/PaymentFragment.kt:20`, `app/src/main/java/com/b2b/online/feature/cart/payment/GatewayFragment.kt:25`
- Result screen — `app/src/main/java/com/b2b/online/feature/cart/result/ResultFragment.kt:18` (Lottie `R.raw.order_success`, links to history / home)
- Cart row builder — `app/src/main/java/com/b2b/online/util/CartDataGenerator.kt:11` (`calculateCart`) and `CartDataGenerator.kt:64` (`generateCart`)

## Depends on
- [[repository]]
- [[room-database]]
- [[domain]]
- [[uistate-pattern]]
- [[promotions]]
- [[address-management]]
- [[product-catalog]]
- [[orders]]
- [[util]]
- [[feature]]

## Depended on by
- [[orders]]
- [[product-catalog]]
- [[promotions]]
- [[repository]]

## Gotchas
- **Promotion/voucher application is server-side.** `CartViewModel.doCheck`
  (`CartViewModel.kt:55`) and `doCheckCart` (`CartViewModel.kt:100`) post the
  current lines to `repo.doCheckCart` / `repo.doCheckGroupPromotion`; the
  server returns updated promo state (`CartActiveUpdate`,
  `ItemCustomerPromotionResponse`). `CartFragment` only observes the result and
  re-renders. `CartDataGenerator` does the *math* (subtotal, discount %, GST
  inclusive/exclusive) but does not *choose* promotions.
- **Subtotal recomputation is duplicated.** `generateCart`
  (`CartDataGenerator.kt:64`) computes totals for the cart/checkout list UI
  (emits `Principal` / `Cart` / `Subtotal` / `Line` rows plus a trailing
  `Pair<Boolean, Double>` for the submit-enabled flag and grand total).
  `calculateCart` (`CartDataGenerator.kt:11`) re-runs the same math at submit
  time to fill `CartHeaderRequest.subtotal / discount / gst / total`. Any
  formula change must be made in both functions.
- **Last row of `cartData` is a magic `Pair<Boolean, Double>`.** Both
  `CartFragment` (`CartFragment.kt:227`) and `CheckoutFragment`
  (`CheckoutFragment.kt:138`) read `it.last()` as the submit-enabled flag +
  grand total, then render `it.take(it.size - 1)` as the list. Forgetting to
  strip the last element will crash the recycler.
- **Insufficient-quantity handling.** When `repo.doPostCart` returns
  `UIState.Error(UITextState.QtyInsufficient)`, `CheckoutViewModel`
  (`CheckoutViewModel.kt:51`) writes `avalableQuantity` onto each affected
  `CartDetail` and re-emits via `generateCart`; the row's `errorMessage`
  becomes visible (`CheckoutFragment.kt:111`) and submit stays disabled until
  the user goes back and edits quantities. `QtyInsufficientResponse` is parsed
  from the API error body.
- **Payment-mode selection is cosmetic in checkout.** The bottom sheet from
  `showPaymentDialog` (`PaymentDialogFragment.kt:101`) ignores which button was
  pressed except as a string — `onMethod` just calls `vm.doSubmit()`. The
  PayPal SDK path is commented out, and the `payment/` sub-package
  (`PaymentFragment` / `GatewayFragment`) is not navigated to from the active
  flow (its `btyPay` button navigates to a stub URL).
- **`CartDataGenerator.generateCart` runs on every cart mutation.**
  `CheckoutViewModel.init` (`CheckoutViewModel.kt:67`) collects
  `repo.getCartFlow()` and emits a freshly-generated list each time —
  including after `updateInsufficientQty`. Same pattern on the cart screen
  through `mainVM.cartData`. Expensive cart edits will trigger a full
  re-projection.
- **`cleanupLocal` runs only on `UIState.SuccessFromRemote`.** Local cart rows
  are deleted one-by-one after a successful POST (`CheckoutViewModel.kt:96`),
  then `_localCleanup` emits and the fragment navigates to the result screen
  (`CheckoutFragment.kt:179`). A successful server post followed by a delete
  failure could leave stale local rows.
- **GST inclusive vs exclusive.** `CartHeaderRequest.gst` is set to whichever
  of `gstExclusive` / `gstInclusive` is non-zero (`CheckoutViewModel.kt:135`),
  but `total` only adds `gstExclusive`. Mixed inclusive + exclusive lines
  within a single principal are not exercised by this formula.

## Open questions
- Where is the payment gateway URL actually meant to come from?
  `PaymentFragment.kt:69` passes the literal string `"url"` to
  `GatewayFragment`, and `GatewayFragment.goToLanding` is an empty stub.
- Is `payment/GatewayFragment` still reachable from any nav graph in the
  active flow, or is it dead code retained for a future online-payment
  rollout?
- The PayPal SDK block in `PaymentDialogFragment` is commented out — is PayPal
  intentionally disabled for B2B, or pending re-enable?
