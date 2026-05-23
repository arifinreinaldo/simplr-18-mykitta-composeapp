---
title: buildSrc/
slug: buildsrc
type: map
verified: 2026-05-23
sources:
  - buildSrc/src/main/java/Library.kt
  - buildSrc/src/main/java/Versions.kt
---

# `buildSrc/`

**`buildSrc/` Kotlin configuration: `Library.kt` and `Versions.kt`.**

## What it does
Gradle's `buildSrc` directory: Kotlin source compiled at build time and made
available to every Gradle script in the project. Used here to centralise
dependency coordinates (`Library.kt`) and version numbers (`Versions.kt`) so
`app/build.gradle.kts` references symbols like `Library.X` instead of
hard-coded strings.

## Where it lives
- `buildSrc/src/main/java/Library.kt` — dependency coordinates.
- `buildSrc/src/main/java/Versions.kt` — version constants and `Config`
  (min/max SDK, etc.).

## Depends on

## Depended on by
- [[build-variants]]

## Gotchas
- Files are under `src/main/java/` even though they're Kotlin. Don't move
  them to `src/main/kotlin/` without verifying the Gradle script picks them
  up.
- `CLAUDE.md` documents these as the canonical source of dependency and
  version info — keep that promise.

## Open questions
- Whether the version-bump workflow is manual or driven by some external
  tooling.
