---
title: ":view" Gradle module
slug: view-module
type: map
verified: 2026-05-23
sources:
  - view/build.gradle
  - view/src/main/AndroidManifest.xml
  - view/src/main/res/
---

# `:view` Gradle module

**The `:view` Gradle module providing reusable XML view components.**

## What it does
A standalone Android library module that ships custom XML view components and
drawables used by the main `:app` module. Contents include
`material_edit_text`, `simple_edit_text`, `simple_edit_password`,
`simple_spinner`, `simple_spinner_dropdown`, plus stroke/fill curve
drawables and arrow / password show-hide icons.

## Where it lives
- `view/build.gradle` — module Gradle script.
- `view/src/main/res/layout/` — `material_edit_text.xml`,
  `simple_edit_text.xml`, `simple_edit_password.xml`, `simple_spinner.xml`,
  `simple_spinner_dropdown.xml`.
- `view/src/main/res/drawable/` — stroke/fill curves, arrow drop, password
  show/hide icons.
- `view/src/main/res/values/` — `colors.xml`, `dimens.xml`, `attrs.xml`.
- `view/src/androidTest/java/com/rei/view/ExampleInstrumentedTest.kt`.

## Depends on

## Depended on by
- [[build-variants]]

## Gotchas
- Package namespace is `com.rei.view`, not `com.b2b.online.view`. Watch for
  that when grepping.
- Module is included via `settings.gradle.kts` and consumed by `:app`.

## Open questions
- Whether this module is shared with another project (the `com.rei.view`
  namespace is suspicious) or strictly internal to b2b-simplr.
