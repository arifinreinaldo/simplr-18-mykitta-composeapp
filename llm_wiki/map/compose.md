---
title: compose — package
slug: compose
type: map
verified: 2026-05-23
sources:
  - app/src/main/java/com/b2b/online/compose/ComposeActivity.kt
  - app/src/main/java/com/b2b/online/compose/screen/
  - app/src/main/java/com/b2b/online/compose/ui/
---

# compose — package

**Jetpack Compose surface area: ComposeActivity plus `screen/` and `ui/` packages.**

## What it does
The Compose-based slice of the app. The main app is still View-based (XML
layouts + ViewBinding), but a small Compose surface exists here for newer
screens or experiments. `ComposeActivity.kt` is tiny (608 B) — it's probably
a thin host that loads a `screen/` composable.

## Where it lives
- `app/src/main/java/com/b2b/online/compose/ComposeActivity.kt` (608 B).
- `app/src/main/java/com/b2b/online/compose/screen/` — composable screens.
- `app/src/main/java/com/b2b/online/compose/ui/` — theme / shared UI bits.

## Depends on
- [[base]]
- [[domain]]

## Depended on by
_(none)_

## Gotchas
- The app is dual-UI: most features are XML+ViewBinding under [[feature]],
  and only this package is Compose. Don't assume Compose is the default.
- `app/build.gradle.kts` sets `buildFeatures { viewBinding = true; compose =
  true }` — both stacks ship.

## Open questions
- Which screens (if any) are currently routed through `ComposeActivity` in
  production builds.
