# Intelligence Service

## Purpose

Python, FastAPI. Owns suggestion logic, pattern detection, and eventually reprioritization.
Reads from the backend's data, does not own storage itself.

## Current state

Not started. Phase 1 scope is a single rule-based suggestion endpoint, no model, no
training, weighted toward whichever of the three tracked goals has had the least recent
attention.

## Decisions log

| Date | Decision | Reason |
|------|----------|--------|
| 2026-09-18 | Phase 1 is rule-based, not ML | No data exists yet to train or validate a model against; building one now would be guessing, not engineering |
| 2026-09-18 | Separate service from the backend, same as settlement-engine's ml-service | Lets the intelligence logic evolve independently of the data layer, and keeps this honestly a Python/AI codebase |

## Open questions

- What Phase 2's descriptive statistics actually need from the backend's API, to be
  finalized once Phase 1 data exists to test against.
