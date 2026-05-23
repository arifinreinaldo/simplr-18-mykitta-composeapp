---
title: util — package
slug: util
type: map
verified: 2026-05-23
sources:
  - app/src/main/java/com/b2b/online/util/
---

# util — package

**Cross-cutting utilities: messaging receivers, helpers, custom views, view-binding delegate.**

## What it does
A catch-all for shared utilities that aren't features and aren't data layer.
Includes the two push-notification receivers (Firebase and Huawei), a large
`Helper.kt` (13.5 KB) with miscellaneous helpers, custom views (OTPView,
CustomDotsIndicator), the ViewBinding delegate, SVG and QR generators, a
phone-format watcher, country helper, and the camera + SMS broadcast hooks.

## Where it lives
- `app/src/main/java/com/b2b/online/util/Helper.kt` (13.5 KB).
- `app/src/main/java/com/b2b/online/util/FirebaseMessagingReceiver.kt` (5.8 KB).
- `app/src/main/java/com/b2b/online/util/HuaweiMessagingReceiver.kt` (5.7 KB).
- `app/src/main/java/com/b2b/online/util/SimpleCamera.kt` (13.3 KB).
- `app/src/main/java/com/b2b/online/util/SafeAreaExtensions.kt` (4.7 KB).
- `app/src/main/java/com/b2b/online/util/CustomDotsIndicator.kt` (6.5 KB).
- `app/src/main/java/com/b2b/online/util/RecyclerViewSeparatorDecoration.kt` (4.1 KB).
- `app/src/main/java/com/b2b/online/util/CountryHelper.kt` (3.5 KB).
- `app/src/main/java/com/b2b/online/util/OTPView.kt` (3.3 KB).
- Plus `qrGenerator/`, `svg/`, and ~14 smaller files
  (`DialogLoading.kt`, `EndlessScrollListener.kt`, `KeyboardListener.kt`,
  `LiveOnce.kt`, `MyLocationListener.kt`, `PhoneWatcher.kt`,
  `SmsBroadcastReceiver.kt`, `UITextState.kt`, `ViewBindingDelegate.kt`,
  `SafeAreaTestFragment.kt`).

## Depends on
- [[domain]]
- [[base]]

## Depended on by
- [[address-management]]
- [[authentication]]
- [[chat]]
- [[localization]]
- [[push-notifications]]
- [[repository]]
- [[shopping-cart]]
- [[uistate-pattern]]

## Gotchas
- `FirebaseMessagingReceiver` and `HuaweiMessagingReceiver` are parallel
  implementations — same payload contract, different SDKs. Edits to one
  almost always need a mirror edit to the other. See [[push-notifications]].
- `Helper.kt` at 13.5 KB is a kitchen-sink file; treat it as a "candidate
  for splitting" if you add to it.

## Open questions
- Whether `SafeAreaTestFragment.kt` is production code or a dev sandbox.
