---
title: worker — package
slug: worker
type: map
verified: 2026-05-23
sources:
  - app/src/main/java/com/b2b/online/worker/PrincipalWorker.kt
---

# worker — package

**Background WorkManager jobs.**

## What it does
Hosts background tasks scheduled via Android's WorkManager. Today there is
only a single job — `PrincipalWorker` — and the file is small (439 B), which
suggests it delegates the real work to [[data]] (likely a Repository call to
refresh the active Principal).

## Where it lives
- `app/src/main/java/com/b2b/online/worker/PrincipalWorker.kt` (439 B).

## Depends on
- [[data]]
- [[di]]

## Depended on by
_(none)_

## Gotchas
- Single-worker package. If a second worker is added, decide whether to keep
  them in this folder or move them under their feature.

## Open questions
- Where `PrincipalWorker` is scheduled from (Application start? Login?
  Periodic?).
