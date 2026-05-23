# B2B-SIMPLR LLM Wiki — Schema & Discipline Rules

This file is the contract every wiki page must satisfy. It is also the contract
the `b2b-wiki` skill enforces during `ingest`, `query`, and `lint`.

If a rule below is violated, lint fails. If a rule needs to change, change it
here first and then re-lint — never let pages drift from the schema silently.

---

## 1. Wiki purpose

The wiki is an LLM-maintained, persistent description of the **current state of
this Android codebase** — `simplr-01-b2b-simplr`. It is not a journal, not a
roadmap, not an ADR log, and not a duplicate of `CLAUDE.md`.

- `CLAUDE.md` = build commands & 30,000-ft orientation. Read by every Claude
  session. Kept short.
- `llm_wiki/` = entity-level, file-path-anchored descriptions of how this code
  actually works right now. Read only when asked.

If a piece of information belongs in both, keep it in `CLAUDE.md` and link to
the wiki from there.

## 2. Directory layout

```
llm_wiki/
├── SCHEMA.md       this file
├── index.md        catalog: every page, one-line summary, grouped by category
├── log.md          append-only: [TS] INGEST|QUERY|LINT — summary
├── map/            wide & shallow: one page per top-level package
├── deep/           hot-spot pages: gnarly internals
└── features/       one page per user-facing feature
```

## 3. Page format

Every content page (anything under `map/`, `deep/`, `features/`) MUST have:

```markdown
---
title: <human title>
slug: <kebab-case, must equal filename minus .md>
type: map | deep | feature
verified: YYYY-MM-DD
sources:
  - <path/to/source/file.kt>
  - <path/to/another.kt>
---

# <Title>

**One-line summary.** Single sentence. `index.md` quotes this verbatim.

## What it does
2–6 sentences. The purpose, not the API.

## Where it lives
Bulleted file paths. Use `file:line` anchors for the important entry points.

## Depends on
Bulleted cross-links: `[[slug]]`, `[[slug]]`. One per line.

## Depended on by
Bulleted cross-links. **Lint rebuilds this section** — do not author by hand.

## Gotchas
The non-obvious stuff. Hidden constraints, footguns, "looks dead but isn't."

## Open questions
Things that couldn't be verified during ingest. Lint surfaces these.
```

`index.md` and `log.md` follow their own formats (described below), not this
one.

## 4. The eight schema rules (lint enforces these)

1. **Every claim with a file path must be verifiable.** Every entry under
   `sources:` and every inline `file:line` reference must resolve in the
   current working tree. Stale path → lint failure.

2. **Cross-links use `[[slug]]`.** No raw markdown links between wiki pages —
   only between wiki and source files. This survives file moves and keeps lint
   cheap.

3. **`depended on by` is derived, not authored.** Lint rebuilds the section
   from every page's `depends on`. Hand-edits are overwritten on the next
   lint pass.

4. **`verified` is a contract.** A page whose `verified` date is older than
   30 days is a lint **warning**; older than 90 days is a lint **error**.
   Re-verification means re-reading the source files under `sources:` and
   updating anything that drifted, then bumping the date.

5. **One concept per page.** If a page exceeds ~400 lines or covers two
   distinct concepts, split it. Lint flags pages over 400 lines.

6. **No duplication of `CLAUDE.md`.** If a wiki page paraphrases `CLAUDE.md`
   without adding entity-level detail, delete it. Lint flags suspected
   duplicates.

7. **`log.md` is append-only.** Format:
   `[YYYY-MM-DDTHH:MM] INGEST|QUERY|LINT — one-line summary`. Never rewrite
   history.

8. **Index entry is the page's one-line summary, verbatim.** Drift between
   the page's `**One-line summary.**` line and its entry in `index.md` is a
   lint failure.

## 5. index.md format

```markdown
# B2B-SIMPLR LLM Wiki — Index

## Map (top-level packages)
- [[data]] — one-line summary verbatim from the page.
- [[domain]] — …

## Deep (hot spots)
- [[repository]] — …

## Features
- [[authentication]] — …
```

Categories are headers. Entries are `[[slug]] — summary` bullets, alphabetised
within each category. Pages not yet written may appear with a `(pending)` suffix.

## 6. log.md format

```
[2026-05-23T14:32] INGEST — seed: scaffolded llm_wiki, wrote SCHEMA + index + log
[2026-05-23T14:45] INGEST — phase 1 (map): 10 pages
[2026-05-23T15:10] INGEST — phase 2 (deep): 7 pages
[2026-05-23T15:30] INGEST — phase 3 (features): 7 pages
[2026-05-23T15:45] LINT — 0 errors, 2 warnings; rebuilt backlinks
```

One entry per operation. Time is local (Asia/Manila — owner is in PH). Prefix
is uppercase. Summary is one sentence; if more context is needed, link to the
page that captures it.

## 7. The three operations

These are defined in detail in the `b2b-wiki` skill at
`.claude/skills/b2b-wiki/SKILL.md`. Summary:

- **ingest** — given a topic / file list / PR / commit range, update every
  affected page in a single pass; update `index.md`; append to `log.md`.
- **query** — answer a question using the wiki, with citations. Does **not**
  write back to the wiki by default. Use `--pin` to file the answer.
- **lint** — run the 8 checks in rule 4; report and ask before fixing.

## 8. Things this wiki deliberately does NOT do

- No git-history ingest. The wiki describes *current* state.
- No automated CI lint. Lint is a Claude-run procedure.
- No team-wide rollout assumption. This is infra one engineer can use; broader
  adoption is a separate decision after the wiki proves its keep.
- No decisions / ADR archive. If we want that later, add `llm_wiki/decisions/`.
