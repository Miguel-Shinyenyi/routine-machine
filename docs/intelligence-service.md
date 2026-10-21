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

Phase 7 started: `POST /reading-notes/assess`, the first call to an external LLM in this
codebase (agreed with the user beforehand: Anthropic's Claude API, see the decisions log).
Takes `{noteText, bookTitle, currentChapter, totalChapters}`, calls `POST /v1/messages`
directly with `httpx` (no `anthropic` SDK dependency, matching the service's existing
plain-`httpx` style for every other external call), and returns the model's assessment:
`chapterPlaced`, `ownWordsPercentage`, `ideasConnected`, `quotedPhrases`, `questionBack`, per
`docs/design.md`'s Reading page. The system prompt instructs the model to answer with nothing
but a single JSON object; `assess_note.parse_assessment_response` (pure function, `assess_note.py`)
extracts and validates it, tolerating stray prose around the JSON but raising
`AssessmentParseError` on a missing field, a malformed `ownWordsPercentage` (must be an
integer 0-100), or no JSON object at all. Reads `ANTHROPIC_API_KEY` fresh from the environment
on each call; missing, returns 503 rather than guessing or failing silently. An upstream error
from Anthropic itself (bad request, rate limit, billing) is caught and returned as a 502 with
the real error message, not an opaque 500 — found and fixed by actually calling the real API
during this phase's verification (see `testing.md`).

Not built yet: the backend storage for notes themselves (this endpoint only assesses text
handed to it, it doesn't persist anything), the named ratio threshold that decides whether a
note counts toward the reading goal, and any wiring from this endpoint into `/tally`'s
goal-hours rollup.

Phase 10 added `POST /analogy`: given `{topic, interest}`, explains the topic through that
one interest, then the precise technical definition — per `docs/product-direction.md`'s
Profile section ("the analogy comes first, then the precise definition... labelled as an
analogy, never as a source of truth"). Same pattern as `/reading-notes/assess`: a system
prompt instructing JSON-only output, `analogy.parse_analogy_response` (pure function,
`analogy.py`) extracting and validating it, `ANTHROPIC_API_KEY` missing returns 503, an
Anthropic-side error is caught and returned as 502. Response includes `label: "analogy"`
explicitly, so a caller never has to infer it's not the literal truth. Cached per
`(topic.strip().lower(), interest.strip().lower())` in a process-lifetime in-memory dict
(`_ANALOGY_CACHE`) — the same pairing is never billed or waited on twice; verified with a real
Anthropic call during this phase's manual testing that reached the real API with a well-formed
request and got a real account-billing error back, handled as a clean 502 rather than a crash,
the same billing issue `/reading-notes/assess` already hit during Phase 7's own verification
(see `testing.md`).

Per-user scoping, part 4 built: `GET /suggestion`, `/patterns` and `/tally` take an optional
`userId` query parameter (the frontend sends the signed-in user's id on every call).

- **`userId` given**: each backend read (`/api/goals`, `/api/learning-topics`,
  `/api/routine-items`, `/api/daily-logs`) is made with `?userId=` so the backend returns only
  that user's rows, and the service keeps only rows whose `userId` equals the caller's anyway
  (`_scoped`), so a backend that ever returned someone else's row could not leak it into a
  computation. A new account therefore gets an empty tally, an empty patterns response and a
  404 suggestion ("no learning topics yet"), never someone else's.
- **`userId` omitted**: only the unowned (pre-account) rows are used, exactly as before. This
  is the founder's data until the owner claim (`backend.md`) hands it to the owner account.
- Learning topics carry no `userId` of their own (a topic belongs to whoever owns its goal), so
  they are scoped by the backend alone.
- Still unscoped, deliberately: `POST /reading-notes/assess` and `POST /analogy` take text in and
  return text out; they read no stored data of anyone's.

## Decisions log

| Date | Decision | Reason |
|------|----------|--------|
| 2026-10-18 | `/analogy`'s cache is a plain process-lifetime `dict`, not persisted to the backend or any external store | The result is fully reproducible from its own inputs (same topic, same interest → deterministic enough a prompt not to need the exact same wording stored); nothing is lost on a restart that matters, and adding storage for a cache would need its own invalidation story for no real benefit at this service's current scale |
| 2026-10-18 | The cache key normalises case and surrounding whitespace (`.strip().lower()`) | "idempotency keys"/"Idempotency Keys"/" idempotency keys " typed on different occasions should hit the same cache entry; without normalising, a trivial difference in how the frontend or a future caller phrases the same request would silently double the API calls and the cost |
| 2026-10-18 | `compute_tally` gained `since_start: bool`, windowing from the earliest real log date instead of `today - window_days` | Tally's reference design has a "Since the start" toggle; a first attempt faked it from the frontend with a very large fixed `windowDays` (years), which made `days_shown_up` generate one entry per day back to a date nothing ever happened — thousands of meaningless notches. Computing the real earliest log date in `tally.py` itself is the honest version of what "since the start" means. 2 new tests |
| 2026-10-17 | (Superseded 2026-10-21, see the per-user scoping rows below) `/suggestion`, `/patterns`, and `/tally` each filtered the backend's goals/routine-items/daily-logs to `userId is None` (a private `_unowned()` helper) | The service had no "current user" concept, and Phase 8's hillside demo users' data silently mixed into every computation. The same protection now comes from `_scoped(records, user_id)`, which with no `userId` keeps exactly the unowned rows, and with a `userId` keeps exactly that user's |
| 2026-10-21 | The service takes an optional `userId` and passes it through to the backend, instead of being told which rows to use | The backend already answers for one user (per-user scoping part 2); asking it for that user's rows keeps the rule in one place. The service also re-filters (`_scoped`), because a computation that trusted the transport alone would turn any future backend mistake into a cross-account leak |
| 2026-10-21 | `userId` stays optional, and omitted means the unowned rows | The service must keep working for the data that existed before accounts until the owner claim has run, and the old behaviour is exactly "unowned only". Making it required would break the founder's own Today and Tally in the gap |
| 2026-10-17 | `POST /reading-notes/assess` calls Anthropic's API directly with `httpx`, not the `anthropic` Python SDK | Matches every other external call this service already makes (backend reads, and now Anthropic) — one HTTP client pattern, one dependency surface, no new SDK to track for a single endpoint |
| 2026-10-17 | The system prompt instructs the model to return only a JSON object, parsed by a dedicated pure function with its own test file | Keeps the hard-to-get-right part (parsing a real but imperfect LLM response: stray prose, a missing field, a percentage out of range) testable with plain strings, no network, same separation `choose_suggestion`/`compute_patterns`/`compute_tally` already use for their own hard-to-get-right logic |
| 2026-10-17 | An Anthropic API error (4xx/5xx) is caught and re-raised as a 502 with the real message, not left to bubble into FastAPI's default 500 | Found by actually calling the real API with a real (but over-budget) key during manual verification: the unhandled `httpx.HTTPStatusError` produced an opaque 500 with no useful detail. A real upstream failure should be visibly different from a bug in this service's own code |
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

- `/analogy`'s cache is process-lifetime only and not shared across replicas — fine for this
  service's current single-instance deployment, but would need a real shared cache (Redis or
  similar) if this service ever runs more than one instance.
- The named ratio threshold that decides whether a reader's note counts toward the reading
  goal (per PROJECT.md's Phase 7 description) isn't decided yet; needs its own pass once
  note storage and the goal-hours rollup are being built, not guessed at alongside the
  assessment call itself.
- Whether `ownWordsPercentage` below some threshold should still get a full assessment (ideas
  connected, a question back) or short-circuit to something cheaper, isn't decided — not an
  issue yet since nothing calls this endpoint in a loop or at volume.
- None currently open for Phase 5.

## Access control, part 2: the caller's token is forwarded

`/suggestion`, `/patterns` and `/tally` read goals, topics, routine items and daily logs from the
backend, which now requires a signed token on those routes. Each endpoint accepts an
`Authorization` header and sends it unchanged on its backend calls (`_backend_headers`). The service
never makes or checks a token itself. With no header it sends none, and when the backend answers
401 or 403 the service answers the same (it used to return 500); any other backend error is now 502.
There is no `userId` parameter (removed in part 5) and no row filter: the backend returns only the
rows of the person in the forwarded token. A `userId` in the request is not read and never sent on.

## Observability, part 2

A request id (`X-Request-Id`, reused if plain, else generated) goes on every response, every log line and every call to the backend. One JSON `request completed` line per request (no query string). Request metrics on port 9000 (`METRICS_PORT`), not the API port, not published by Compose. `observability.py` holds all of it; details, what is recorded and how to turn it off are in `docs/observability.md`.

## Observability, part 3

`POST /reading-notes/assess` reports `assessment_requests_total{outcome}`, `assessment_duration_seconds`, `assessment_tokens_total{direction}` and `assessment_estimated_cost_usd_total` (prices from `ANTHROPIC_INPUT_USD_PER_MTOK` / `ANTHROPIC_OUTPUT_USD_PER_MTOK`, 0 if unset). Never the note or the book. See `docs/observability.md`.

