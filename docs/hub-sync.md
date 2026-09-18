# Hub Sync

## Purpose

Reads the UMWAYI context hub repo directly (public, no auth required) and converts activity
there into routine completions, so journaling and writing don't have to be logged twice, once
by doing them and once by telling Routine Machine about it.

## Current state

Not started. Phase 1 scope: on each check, list files under `journal/entries/` and
`articles/` in the UMWAYI repo, compare against what's already been recorded, and mark the
`journaling` or `writing` routine item as done for the corresponding date on any new or
changed file.

## Decisions log

| Date | Decision | Reason |
|------|----------|--------|
| 2026-09-18 | Read UMWAYI directly rather than manual re-logging | Journaling and writing already happen in UMWAYI; requiring a second manual entry in Routine Machine duplicates effort and will get skipped under exactly the kind of overload this project exists to reduce |
| 2026-09-18 | Detects that an entry or article exists, does not parse its content or infer goal relevance | Content-based tagging is a pattern-detection problem, belongs in Phase 2. Phase 1 stays rule-based, not inferential, per PROJECT.md's phase rules |
| 2026-09-18 | Reads via the public GitHub API, no token stored | UMWAYI is a public repo; storing a credential for a public read is unnecessary risk with no benefit |

## Open questions

- Poll on a schedule, or trigger manually. Decide when Phase 1 implementation starts.
- Whether a moved or renamed file in UMWAYI could get double-counted. Needs a real check
  once this is implemented, not assumed away here.