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

Phase 2 built: `GET /patterns`, reading `GET /api/routine-items` and `GET /api/daily-logs`
from the backend, computed by `patterns.compute_patterns` (pure function, takes an explicit
`today` so it's testable without mocking the clock):

- **Completion rate** per routine item: distinct logged days divided by the number of days
  the item has existed (its `createdAt` date through today, inclusive).
- **Streaks** per routine item: `currentStreak` counts consecutive logged days ending today,
  with a one-day grace period (not yet logging today doesn't break it until tomorrow);
  `longestStreak` is the longest historical run. Both are computed only over logged dates on
  or after the item's `createdAt` — see the decisions log entry on backdated logs below.
- **Skip-heavy days**: a calendar day where at least half of that day's *active* routine items
  (items that existed by that date) went unlogged. Days before any routine item existed are
  excluded, not treated as 100% skipped.
- **Topic/routine correlation**: compares the average number of distinct routine items
  completed on days with at least one learning-topic log against days without one, over the
  same date range as skip-heavy days. This is a plain average comparison, not a correlation
  coefficient — see the decisions log.

No new backend endpoint or schema was needed for Phase 2; `routine-items` and `daily-logs`
were already sufficient once `RoutineItemResponse` also exposed `createdAt` (see `backend.md`).

Phase 5 built: `GET /tally?windowDays=` (default 28), reading `GET /api/routine-items`,
`GET /api/learning-topics`, and `GET /api/daily-logs`, computed by `tally.compute_tally` (pure
function, same `today` argument pattern as `compute_patterns`):

- **Tally stick**: a day counts as shown up once at least `TALLY_STICK_THRESHOLD` (3, named
  and documented in `tally.py`) distinct routine items were tended that day. Only routine
  items count toward this, not learning topics, per `docs/design.md`.
- **Cumulative hours per goal, by week**: each `daily_log`'s `measuredMinutes` rolls up to a
  goal through either the log's routine item's `goalId` or its learning topic's `goalId`
  (learning topics already always have one), summed into Monday-anchored weeks, then
  accumulated week over week so the series only goes up, matching the Tally page's line
  chart. A log with no goal mapping (no `goalId` on its routine item, or no `measuredMinutes`)
  contributes nothing.
- **Shown-up-of-window per routine item**: distinct logged days within the window, with the
  window itself clipped to the item's lifetime if it's younger than the window (an item
  created 2 days ago can't have shown up in the last 28).

## Decisions log

| Date | Decision | Reason |
|------|----------|--------|
| 2026-10-16 | `/tally` is a separate module (`tally.py`) and endpoint from `/patterns`, not an extra field on it | Different question (hours and shown-up-ness vs completion rates/streaks/correlation), different consumer page (Tally's stat tiles and chart vs the rest of Tally's `/patterns`-sourced sections); keeping them separate pure functions keeps each one's tests focused, same reasoning `compute_patterns` and `choose_suggestion` already follow |
| 2026-10-16 | `TALLY_STICK_THRESHOLD = 3` is a named constant in `tally.py`, not inlined | `docs/design.md` states the rule in prose ("a day counts when at least 3 routines were tended") as a stated choice, not a derived one; the constant makes that explicit in the one place it's checked, same pattern as `SKIP_HEAVY_THRESHOLD` in `patterns.py` |
| 2026-09-18 | Streak and completion-rate calculations both exclude logs dated before the routine item's `createdAt` | Found via manual Docker Compose verification: a routine item created "today" that also had a log backdated to "yesterday" (nothing at the API level stops this) showed `currentStreak: 2` while `completionRates` showed `totalDays: 1`, an inconsistency. Completion rate already excluded the backdated log; streak didn't, so it was made to match. Regression test: `test_a_log_backdated_before_the_item_existed_does_not_count_toward_its_streak` |
| 2026-09-18 | Skip-heavy-day threshold is a flat 50% of active items, stated rather than derived | Phase 2 is descriptive statistics, not a model; a fixed, documented threshold is honest about being a choice, not a fitted parameter |
| 2026-09-18 | Topic/routine correlation is an average-comparison, not a Pearson correlation coefficient | PROJECT.md's phase description calls this "a first descriptive correlation," and the simpler comparison needs no new dependency (no numpy/scipy) and is easier to state honestly as "average completions on X days vs Y days" than a coefficient would be to explain for two datasets this small |
| 2026-09-18 | `RoutineItemResponse` now includes `createdAt` | Phase 2's completion-rate and skip-heavy-day math needs each item's inception date to bound its window; the data already existed on the entity, this only exposes it. See `backend.md` |
| 2026-09-18 | Answered the "what Phase 2 needs from the backend's API" open question: `GET /api/routine-items` and `GET /api/daily-logs`, nothing new | Previously left open on the grounds that answering it early would mean designing Phase 2 while still in Phase 1; now that Phase 2 has actually started, this is the real answer, confirmed by building against it, not speculated in advance |
| 2026-09-18 | Suggestion logic is a pure function (`choose_suggestion`) separate from the FastAPI route | Lets the rule be unit-tested directly against plain dicts, without mocking HTTP for every case |
| 2026-09-18 | Phase 1 is rule-based, not ML | No data exists yet to train or validate a model against; building one now would be guessing, not engineering |
| 2026-09-18 | Separate service from the backend, same as settlement-engine's ml-service | Lets the intelligence logic evolve independently of the data layer, and keeps this honestly a Python/AI codebase |

## Open questions

- None currently open for Phase 5.
