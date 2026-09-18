# Intelligence Service

## Purpose

Python, FastAPI. Owns suggestion logic, pattern detection, and eventually reprioritization.
Reads from the backend's data, does not own storage itself.

## Current state

Built: FastAPI, single `GET /suggestion` endpoint. On each call it reads `GET /api/goals`,
`GET /api/learning-topics`, and `GET /api/daily-logs` from the backend (base URL from
`BACKEND_BASE_URL`, default `http://localhost:8080`), then picks a goal and one of its
learning topics with `suggestion.choose_suggestion`:

- For each goal, its "last attention" is the most recent `logDate` among daily logs against
  any of its learning topics (routine item logs don't count toward goal attention). A goal
  with no logged topics at all counts as needing the most attention.
- The goal with the oldest (or absent) last attention is chosen; ties break on lowest goal id
  for determinism. The same rule picks a topic within that goal.
- Returns 404 if no goal has any learning topics yet, since there's nothing to suggest.

This is a pure sort over data fetched from the backend, no model, no training.

## Decisions log

| Date | Decision | Reason |
|------|----------|--------|
| 2026-09-18 | Suggestion logic is a pure function (`choose_suggestion`) separate from the FastAPI route | Lets the rule be unit-tested directly against plain dicts, without mocking HTTP for every case |
| 2026-09-18 | Phase 1 is rule-based, not ML | No data exists yet to train or validate a model against; building one now would be guessing, not engineering |
| 2026-09-18 | Separate service from the backend, same as settlement-engine's ml-service | Lets the intelligence logic evolve independently of the data layer, and keeps this honestly a Python/AI codebase |

## Open questions

- What Phase 2's descriptive statistics actually need from the backend's API, to be
  finalized once Phase 1 data exists to test against.
