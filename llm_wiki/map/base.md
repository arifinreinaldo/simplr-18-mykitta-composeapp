---
title: base — package
slug: base
type: map
verified: 2026-05-23
sources:
  - app/src/main/java/com/b2b/online/base/BaseActivity.kt
  - app/src/main/java/com/b2b/online/base/BaseApplication.kt
  - app/src/main/java/com/b2b/online/base/BaseFragment.kt
  - app/src/main/java/com/b2b/online/base/C.kt
---

# base — package

**Base Activity / Fragment / Application classes and the `C` constants object.**

## What it does
Shared base classes every feature extends from, plus a constants object.
`BaseApplication` is the Hilt entry point (marked `@HiltAndroidApp`) and
performs cross-cutting init (Firebase, HMS, theme). `BaseActivity` and
`BaseFragment` hold common UI plumbing. `C.kt` is the home for compile-time
constants (intent keys, action names, time windows).

## Where it lives
- `app/src/main/java/com/b2b/online/base/BaseApplication.kt` (1.1 KB).
- `app/src/main/java/com/b2b/online/base/BaseActivity.kt` (1.1 KB).
- `app/src/main/java/com/b2b/online/base/BaseFragment.kt` (524 B).
- `app/src/main/java/com/b2b/online/base/C.kt` (794 B).

## Depends on

## Depended on by
- [[authentication]]
- [[compose]]
- [[di-graph]]
- [[feature]]
- [[localization]]
- [[push-notifications]]
- [[repository]]
- [[uistate-pattern]]
- [[util]]

## Gotchas
- All four files are small. If `BaseApplication` grows, init concerns should
  be extracted into Hilt modules under [[di]], not piled into the
  application class.
- `C.kt` is the *only* sanctioned constants store. New magic strings go here,
  not into ViewModels.

## Open questions
- Whether `BaseFragment` uses the `ViewBindingDelegate` from [[util]] by
  default, or whether features wire that up themselves.
