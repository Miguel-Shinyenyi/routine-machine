# Routine Machine

## What this is

A system that logs daily routine completion and daily learning, and over phases moves from
recording, to detecting patterns, to a fixed schedule with a tracking frontend, to actively
suggesting what to do next. Built for one user.

## Why it exists

Directly from the person building it: "sometimes I feel like it's a lot so I end up doing
nothing." This system counters that by leading with a little a day, tracked honestly, rather
than a plan that looks complete on paper and never gets touched.

It also exists as portfolio evidence of AI engineering, not just backend engineering, and it
feeds three concrete goals: a CMU masters application, target roles including PostHog, and
becoming a working AI engineer rather than claiming the title without evidence.

Journaling and article writing in the UMWAYI context hub feed this directly, see
`docs/hub-sync.md`, so the two systems reinforce each other instead of running in parallel
with duplicated effort.

While Phase 4 (active reprioritization) waits on real usage data, a fixed weekly schedule and
a tracking frontend give something usable in the meantime, see `docs/schedule.md` and
`docs/frontend.md`.

## The underlying problem

Most personal habit tools either do nothing but record, or try to be intelligent from day
one with no data to be intelligent about. This project treats that as a sequencing problem,
not a scope problem: build the data layer correctly first, only add intelligence once there
is real data to learn from, and gate the most ambitious feature (active reprioritization) on
evidence, not on a target date. A fixed, non-adaptive schedule doesn't have that problem, it
doesn't need pattern data to be correct, so it can be built now rather than waiting alongside
Phase 4.

## Tech stack

- Backend: Spring Boot (Java), same core stack as settlement-engine
- Intelligence service: Python, FastAPI
- Frontend: Next.js, same as settlement-engine's frontend, for the same reason the backend
  and intelligence service reuse its pattern, one known-working shape instead of a new one
- Database: PostgreSQL
- Containers: Docker Compose (local only, no Kubernetes yet, single user, nothing to scale)

## Project structure

```
routine-machine/
  PROJECT.md              <- this file, master guide
  docs/
    DOCS_MAINTENANCE.md    <- rules for keeping these docs current
    backend.md              <- Spring Boot service structure and APIs
    intelligence-service.md  <- Python service, suggestion logic, pattern detection
    database.md               <- schema, migrations
    testing.md                 <- test strategy, coverage rules
    hub-sync.md                 <- reads UMWAYI directly, converts journaling/writing into completions
    schedule.md <- the fixed weekly schedule and its data model
    frontend.md <- the tracking UI: backlog, roadmap, board, reports
  backend/                      <- Spring Boot source
  intelligence-service/          <- Python source
  frontend/ <- Next.js source
  infra/                          <- Docker Compose
```

## Running the application

Prerequisites: Java 21, Python 3.13, Docker Desktop running (needed for local Postgres and
for the backend's Testcontainers integration tests — check `docker info` if unsure it's up).

**Everything together:**

```
docker compose -f infra/docker-compose.yml up --build -d
```

Starts Postgres on `localhost:5432` (db/user/password all `routine_machine`), the backend on
`localhost:8080`, the intelligence service on `localhost:8000`, and the frontend on
`localhost:3000`, wired together with the env vars in `infra/docker-compose.yml`. If port
5432 is already taken locally (a native Postgres install, for instance), don't edit the
compose file — override the host port for a local run only, e.g. `docker compose -f
infra/docker-compose.yml -f <override-file> up -d` with the override remapping `postgres`'s
`ports` to `"5433:5432"`, and point `ROUTINE_MACHINE_DB_URL` at that port if running the
backend outside Docker too.

**Backend only, for local development:**

```
cd backend
./mvnw spring-boot:run
```

`./mvnw` is the standard Maven Wrapper (self-downloads Maven on first run) — there is no
system-wide `mvn` on this machine. Needs Postgres reachable at the URL in
`ROUTINE_MACHINE_DB_URL` (defaults to `jdbc:postgresql://localhost:5432/routine_machine`,
matching `docker compose -f infra/docker-compose.yml up -d postgres`). Runs on port 8080, runs
Flyway migrations automatically on startup.

Run its tests with `./mvnw test` (needs Docker running, for Testcontainers).

**Intelligence service only, for local development:**

```
cd intelligence-service
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements-dev.txt
uvicorn main:app --reload --port 8000
```


Reads the backend's API at `BACKEND_BASE_URL` (defaults to `http://localhost:8080`). Run its
tests with `pytest`.

**Frontend only, for local development:**

```
cd frontend
npm install
BACKEND_URL=http://localhost:8080 INTELLIGENCE_SERVICE_URL=http://localhost:8000 npm run dev
```

Runs on port 3000, redirects `/` to `/board`. Needs the backend (and, for Reports and the
Board's learning-slot materialization, the intelligence service) reachable at those URLs.
Run its tests with `npm test` (Vitest, covers `lib/schedule.ts`'s pure logic only — see
`docs/testing.md` for why the rest is verified by running the app instead).

## Build phases

1. Logging and rule-based suggestion: record daily routine completion and learning topics,
   suggest a topic weighted toward whichever of the three goals has had the least recent
   attention. No real intelligence yet, correct data modeling only.
2. Pattern detection: completion rates, streaks, skip-heavy days, and a first descriptive
   correlation between logging a learning topic and completing more or fewer routine items
   that day. Statistics on structured data, not a model.
3. Fixed schedule and tracking frontend: a deliberately non-adaptive weekly schedule (see
   `docs/schedule.md`), materialized into daily tasks, tracked through a frontend with a
   backlog, a roadmap, a board, and reports built on Phase 2's statistics (see
   `docs/frontend.md`). Doesn't need pattern data to be correct, so it isn't gated on Phase 4.
4. Active reprioritization: suggest a next action for the current day, with a stated reason
   tied to a Phase 2 finding. Does not start until Phase 2 has produced real data, weeks of
   it, not a synthetic sample, judged by mutual agreement that its output is actually usable,
   not by a fixed timeline. This is a gate, not a target date.
5. Shepherd frontend over the existing API: redesign the frontend (Today, Path, Flock,
   Tally) to the design system in `docs/design.md`, plus the small backend additions
   (routine item categorisation, `measured_minutes`, goal roll-up, intelligence-service
   `/tally`) that those views need.
6. Watering holes: automatic counting from WakaTime, ActivityWatch, GitHub, optionally
   RescueTime, a measurements table, a sync job, a health-data ingest endpoint, and
   hub-sync's Phase 1 scope (listing journal/article activity, not just the Current-book
   line Stage 1 already reads).
7. Reader's notes: books, chapters, assessed reading notes via an intelligence-service LLM
   call, counted toward the reading goal under a named ratio threshold.
8. The hillside: an anonymous, opt-in, k-anonymous leaderboard by days-shown-up. Needs an
   accounts/auth decision before any code, explicitly gated on mutual agreement on that
   architecture, not a target date.

Phases 5 to 8 are inserted alongside Phase 4, not after it: none of them need Phase 2's real
usage data, so Phase 4's gate is unchanged by their existence. Each phase is built, verified,
and reported on its own before the next starts.

Current phase: **Phase 5 done; Phase 6 done** (hub-sync Phase 1, the measurements table and
idempotent upsert, all four sync sources — WakaTime/ActivityWatch/GitHub on by default,
RescueTime optional and off by default — the health-data ingest endpoint, Strava explicitly
deferred to Phase 8's accounts/auth decision, and the watering holes status page). **Phase 7
done** (the `Book` entity, superseding `current_reading_log` as hub-sync's write target; the
intelligence-service's `POST /reading-notes/assess` LLM call, verified against the real
Anthropic API; `ReadingNote` storage; the named ratio threshold (70% own words) and the
goal-hours rollup, reusing the existing `daily_logs`/`/tally` machinery with no new
intelligence-service code; and the `/reading` frontend page, also verified live end to end
through a real — if billing-blocked — Anthropic call). **Phase 8 in progress**: the
accounts/auth architecture decision is now agreed with the user (one shared deployment, real
user accounts, both password and GitHub OAuth, Next.js owns the session), backend groundwork
is built (`users` table, register/login/github-login, JWT verification proven against a real
signed token, optional `user_id` scoping on `routine_items`/`daily_logs`), and **the hillside
itself is now built** (`GET /api/hillside`, `/hillside` page: real computed bands, histogram,
and pseudonymous neighbours from seeded data) — but `asUserId` is a demo-mode stand-in for a
real logged-in user, `goals` still has no `user_id` or creation API, and the Next.js
session/login UI itself is not built. The frontend was also compared page by page against a
design reference the user supplied; **every view now has had that fidelity pass** (nav icons,
tokens, and each page's own copy/structure — Today, Reading, Watering holes, Path, Flock,
Tally, Hillside). The frontend is redesigned to the shepherd's language (Today, Path, Flock,
Tally, Reading, Hillside, Watering holes) per `docs/design.md`. hub-sync
now lists UMWAYI's journal entries and essays on a schedule and marks `journaling`/`writing`
routine items done. Phase 4 remains gated on real, weeks-of-usage data behind Phase 2's statistics,
which still doesn't exist — only what manual verification has produced so far. The weekly
layout itself (which days, which durations) still needs to be entered through Flock's
schedule-template form; nothing in this repo does that automatically, and real durations are
still an open question (see `schedule.md`).

## Repo structure decision

Monorepo, same reasoning as settlement-engine: one contributor, tightly coupled services,
no reason yet to pay the coordination cost of separate repos. Revisit if that changes.

## Status log

Update this section every time a phase starts or finishes, or a real decision gets made.
Keep entries short. Record fixes and gaps found, not just what was completed.

| Date | Phase | Status | Notes |
|------|-------|--------|-------|
| 2026-09-18 | Setup | Done | Reconciled documentation structure against settlement-engine's actual PROJECT.md and docs/ pattern, replacing an earlier, less disciplined draft |
| 2026-09-18 | Setup | Done | Stack decided: Spring Boot core plus Python FastAPI intelligence service, reusing settlement-engine's polyglot pattern rather than inventing a new shape |
| 2026-09-18 | Phase 1 | Done | Backend: 4 entities (Goal, RoutineItem, LearningTopic, DailyLog), Flyway migrations, REST API, 23 tests passing (Testcontainers Postgres). Intelligence service: FastAPI `/suggestion` endpoint reading the backend's API, rule-based goal/topic selection, 8 tests passing. Verified end-to-end via `docker compose up --build`: created a routine item, a learning topic, a daily log, and got back a correct suggestion. Found and fixed a real bug in the process, `GET /api/learning-topics` 500'd on a Hibernate lazy-loading exception that no existing test had caught, since only the POST path asserted on a response body; see `backend.md` and `testing.md` |
| 2026-09-18 | Doc cleanup | Done | Fixed a stale test count in testing.md (said 22, actually 23) and explicitly logged the decision to leave intelligence-service.md's Phase 2 open question open rather than answer it prematurely |
| 2026-09-18 | Phase 2 | Done | Intelligence service: `GET /patterns` (completion rates, streaks, skip-heavy days, topic/routine correlation), computed by the pure `compute_patterns` function, 18 intelligence-service tests passing (up from 8). Backend: `RoutineItemResponse` now exposes `createdAt`, the one piece Phase 2 needed that Phase 1's API didn't already have. Verified end-to-end via Docker Compose with real routine items, logs, and a learning topic. Found and fixed a second real bug this way, streaks counted a log backdated to before its routine item existed, while completion rate already excluded it; both now agree. See `intelligence-service.md` and `testing.md` |
| 2026-09-18 | Scope | Decided | Inserted a new Phase 3 (fixed schedule plus tracking frontend) ahead of the old Phase 3, renumbered to Phase 4 (active reprioritization). The fixed schedule doesn't need pattern data to be correct, so it doesn't belong behind Phase 4's gate. See `schedule.md` and `frontend.md` |
| 2026-10-14 | hub-sync Stage 1 | Done | `HubJournalReader`: reads `UMWAYI_JOURNAL_KEY_B64`, unlocks a short-lived git-crypt shallow clone of UMWAYI's journal, extracts the `Current book:` line, deletes the key and clone before returning. Not wired to any scheduler or endpoint yet, Stages 2 and 3 not started. Backend runtime image now installs `git` and `git-crypt`. 6 new tests against throwaway git-crypt repos/keys (62 backend tests total, up from 56); skip cleanly without `git-crypt` on `PATH`. Verified: `docker compose build` succeeds and `git-crypt --version` runs in the built image; grepped the repo for the key variable's value, found only the variable name. See `hub-sync.md`, `backend.md`, `testing.md` |
| 2026-10-16 | Scope | Decided | Inserted Phases 5-8 (shepherd frontend redesign, watering holes, reader's notes, the hillside) alongside Phase 4, per `docs/design.md`. None need Phase 2's real usage data, so Phase 4's gate is unchanged. Phase 8 is explicitly gated on agreeing an accounts/auth architecture first, not a target date |
| 2026-09-19 | Phase 3 | Done | Backend: `schedule_templates`, `tasks`, `current_reading_log` tables and endpoints, plus `TaskService` (first service class in this codebase) handling the DONE-transition's daily_log create/delete, 56 backend tests (up from 23). Frontend: Next.js app, all four views (backlog, roadmap, board, reports), 5 Vitest tests for the one piece of real logic. Verified end-to-end via a full browser walkthrough against the Docker Compose stack: created routine items/learning topics/templates, materialized a day's tasks (including resolving a learning-slot topic via the intelligence service), moved a task through in-progress to done, confirmed the resulting daily_log, and saw Reports render correctly. Found and fixed a real gap in the process — there was no UI path to create a routine item or learning topic at all, which would have made the schedule-template form unusable on first use; added a Routine items and Learning topics section to Backlog. See `backend.md`, `frontend.md`, `schedule.md`, `testing.md` |
| 2026-10-17 | Phase 5 | Done | Frontend redesigned to `docs/design.md`'s shepherd language and tokens: renamed routes (Today/Path/Flock/Tally, old paths redirect), a new `Shell` (glass sidebar/tab bar), Today as a single grouped task list, Tally's tally stick/stat tiles/`GoalHoursChart`/per-routine meters. Backend: `routine_items` gained `category`, `countRule`, `source`, `hillsideField`, `private`, `goalId` (migration V4), `daily_logs` gained `measuredMinutes`, 61 backend tests (up from 56). Intelligence service: new `GET /tally` (`compute_tally`), 26 tests (up from 18). Verified: `./mvnw test`, `pytest`, `npm run lint`/`test`/`build` all pass; full Docker Compose stack built and walked through in a browser at 1440px (light not independently re-verified live, system theme was dark during this session's walkthrough — token definitions were reviewed, not rendered) — seeded real data, ran materialize → start → done on Today, confirmed Path/Flock/Tally render correctly. The 390px phone layout could not be confirmed live: `resize_window` reported success but the screenshot kept the desktop viewport, a tool limitation in this session, not a code check; Tailwind's `md:` breakpoints are in place but unverified by eye. See `frontend.md`, `backend.md`, `database.md`, `intelligence-service.md`, `testing.md` |
| 2026-10-17 | Phase 6 (hub-sync Phase 1) | Done | `HubSyncService` polls UMWAYI on a schedule (default 15min, `hub-sync.enabled=false` in every test), lists `journal/entries/` (dated by filename) and the real `site/philosophy/`/`site/tech/` essay directories (dated by first commit; `docs/hub-sync.md` originally assumed a non-existent `articles/` path, flagged and resolved with the user before building), and marks `journaling`/`writing` routine items done via `daily_logs`. Dedupes by git blob sha in a new `hub_sync_processed_files` table (migration V5), so a renamed file isn't double-counted — the open question this doc used to defer. 9 new backend tests (76 total, up from 67), fakes `GitHubContentClient` for the listing/dedupe cases and reuses `GitCryptTestFixture` for the current-book-sync cases. Verified against the real UMWAYI repo through a live Docker container: correctly listed all 26 real files and recorded them as processed without crashing or double-logging. The rest of Phase 6 (measurements table, WakaTime/ActivityWatch/GitHub/RescueTime sources, health-data ingest endpoint, the Strava decision, the watering holes page) is not started. See `hub-sync.md`, `backend.md`, `database.md`, `testing.md` |
| 2026-10-17 | Phase 6 (measurements table) | Done | New `measurements` table (migration V6), unique on `(source, metric, measurement_date)`, and `MeasurementService.recordMeasurement(source, metric, date, value)` — the one write path any future sync job calls: a repeat call for the same `(source, metric, date)` updates the existing row's value instead of inserting a duplicate, the idempotency case the task asked to test first. `MeasurementSource` is a new, separate enum (`WAKATIME`, `ACTIVITYWATCH`, `GITHUB`, `RESCUETIME`, `HEALTH`) rather than reusing `RoutineItemSource`, which mixes in non-measurement origins (`MANUAL`, `HUB`, `READER_NOTES`) and is missing `RESCUETIME`. 5 new backend tests (81 total, up from 76). Verified: `./mvnw clean test`, all passing, including the double-write case. Nothing calls `recordMeasurement` yet — no WakaTime/ActivityWatch/GitHub/RescueTime source, no health-data endpoint, no daily_log/routine_item wiring, no watering holes page. Groundwork only, by design, per the one-part-at-a-time rule |
| 2026-10-17 | Phase 6 (WakaTime) | Done | `WakaTimeSyncService` polls WakaTime's summaries API (default 15min, `wakatime-sync.enabled=false` in every test), fetches the trailing 7 days of coding totals each run, and upserts each day into `measurements` as `coding_minutes` via `MeasurementService`. Every routine item with `source = WAKATIME` (matched by source, not name, unlike hub-sync's `journaling`/`writing`) gets one daily_log per day — created once, never updated, even though the underlying measurement can be. `daily_logs.source` gained a fourth value, `wakatime` (migration V7). 6 new backend tests (87 total, up from 81), fakes `WakaTimeClient` for all cases, including the idempotency case (syncing the same day twice does not duplicate the daily_log), tested first. `infra/.env.example` and `docker-compose.yml` now carry `WAKATIME_API_KEY`. Verified: `./mvnw clean test`, all passing. Not verified against the real WakaTime API — no key available this session, unlike `GitHubContentClientImpl`'s manual `curl` check; flagged as an open question in `testing.md`. ActivityWatch, GitHub, RescueTime, the health endpoint, the Strava decision, and the watering holes page remain unbuilt |
| 2026-10-17 | Scope | Decided | Before building ActivityWatch: flagged that, unlike WakaTime/GitHub, ActivityWatch has no cloud API — `aw-server` runs locally, so the backend can only reach it by pulling if both run on the same machine. Asked the user rather than deciding silently; agreed to pull directly, assuming same host, for now |
| 2026-10-17 | Phase 6 (ActivityWatch) | Done | `ActivityWatchSyncService`, same shape as `WakaTimeSyncService`: polls every 15min (`activitywatch-sync.enabled=false` in tests), fetches the trailing 7 days of active-time totals from `aw-server`'s afk bucket, upserts each day into `measurements` as `active_minutes`, and gives every `source = ACTIVITYWATCH` routine item one daily_log per day (`daily_logs.source` gained a fifth value, `activitywatch`, migration V8). `ActivityWatchClientImpl` assumes the backend runs on the same machine as `aw-server` (see the scope decision above), reachable at `ACTIVITYWATCH_BASE_URL` — defaults to `http://localhost:5600` directly, or `http://host.docker.internal:5600` in `docker-compose.yml`, since the backend is containerized there. 6 new backend tests (93 total, up from 87), fakes `ActivityWatchClient`, same six cases as WakaTime's including the double-log idempotency case tested first. Verified: `./mvnw clean test`, all passing. Not verified against a real `aw-server` — none running this session; flagged in `testing.md`. GitHub, RescueTime, the health endpoint, the Strava decision, and the watering holes page remain unbuilt |
| 2026-10-17 | Phase 6 (GitHub) | Done | `GitHubSyncService`, same shape again, built explicitly as a **backup signal** per PROJECT.md's own framing, not a primary source: polls every 15min (`github-sync.enabled=false` in tests), fetches the trailing 7 days of commit counts from GitHub's public events API (`GitHubCommitClientImpl`, no credential, `GITHUB_USERNAME` default `Miguel-Shinyenyi`), upserts each day into `measurements` as `commit_count`, and gives every `source = GITHUB` routine item one daily_log per day with no `measuredMinutes` (a count isn't a duration). `daily_logs.source` gained a sixth value, `github` (migration V9). 6 new backend tests (99 total, up from 93), fakes `GitHubCommitClient`, same six cases as the other two syncs. Verified: `./mvnw clean test`, all passing, and a real `curl` against `https://api.github.com/users/Miguel-Shinyenyi/events/public` returned HTTP 200 with real PushEvent/CreateEvent/PullRequestEvent data matching the parser's expected shape. RescueTime, the health endpoint, the Strava decision, and the watering holes page remain unbuilt |
| 2026-10-17 | Phase 6 (RescueTime) | Done | `RescueTimeSyncService`, same shape as the other three syncs, but genuinely optional: `rescuetime-sync.enabled` defaults to `false` in production too (every other sync in this phase defaults to `true`), matching PROJECT.md's "optionally RescueTime" framing as an actual behavioural difference, not just a label. `RoutineItemSource` gained `RESCUETIME` (closing a gap flagged back when `MeasurementSource` was introduced: `MeasurementSource` already had it, `RoutineItemSource` didn't), and `daily_logs.source` gained a seventh value, `rescuetime` (migration V10, which touches both check constraints). `RescueTimeClientImpl` sums RescueTime's Analytic API Data overview rows (one per productivity level per day) into a single daily total. 7 new backend tests (106 total, up from 99): the same six cases as WakaTime/ActivityWatch, plus a seventh proving the flag's own default holds (`scheduledSync()` via the test constructor, no explicit `enabled`, writes nothing). Verified: `./mvnw clean test`, all passing. Not verified against the real RescueTime API — no key available this session, same gap as WakaTime; flagged in `testing.md`. All four sync sources named in Phase 6 are now built. Remaining: the health-data ingest endpoint, the Strava decision, and the watering holes page |
| 2026-10-17 | Phase 6 (health-data ingest endpoint) | Done | `POST /api/measurements`, the push side of the watering holes (health data has no API to pull from, unlike the other four sources), writes `MeasurementSource.HEALTH` only — the request body has no `source` field, so this one endpoint can't be used to spoof any other source. Token-protected: `Authorization: Bearer <token>` must match `HEALTH_INGEST_TOKEN`; unset or blank, every request is rejected (fail closed), a plain header check rather than pulling in Spring Security for one comparison. 7 new backend tests (113 total, up from 106): a valid post, a missing token, a wrong token, a missing `metric`, a negative `value`, a repeat post updating rather than duplicating, and a second test class with no token property set at all confirming the fail-closed default. Found and fixed a real test bug while writing these: two cases asserted `findAll()` was empty but failed, because `@SpringBootTest` methods in this codebase share one Postgres instance per class with no per-method rollback, so an earlier test's row was still there; fixed by asserting on count deltas instead, documented in `testing.md`'s decisions log. Verified: `./mvnw clean test`, all passing. The companion app that would actually call this endpoint remains explicitly out of scope, per the task. Remaining in Phase 6: the Strava decision and the watering holes page |
| 2026-10-17 | Phase 6 (Strava) | Deferred, decided | No code written, deliberately: every watering hole built so far authenticates with a single static secret the backend holds; Strava would be the first needing real user-facing OAuth2 (authorization-code redirect, stored refresh token, refresh on expiry), which is the same category of decision Phase 8 (the hillside) is already explicitly gated on agreeing before any of its own code gets written. Building a one-off token store for Strava now risks duplicating or conflicting with whatever Phase 8 settles on. Recorded as a deferral, not a gap, in `backend.md`'s decisions log and open questions, to revisit once Phase 8's accounts/auth architecture is agreed. Remaining in Phase 6: only the watering holes page |
| 2026-10-17 | Phase 6 (watering holes page) | Done — **Phase 6 complete** | New backend `GET /api/watering-holes` (`WateringHoleStatusController`) returns each source's own feature-flag state (read live via `@Value`, never hardcoded) and latest activity timestamp (`hub_sync_processed_files` for HUB, `measurements` for the rest), null if never synced. New frontend page `/watering-holes` renders that as status cards, plus a "how each activity is counted" table built from the existing `GET /api/routine-items` (`source`/`countRule` fields), filtered to non-`MANUAL` items — generated from live data, not hard-coded, per the task's own instruction. Added as a fifth Shell nav link. 5 new backend tests (118 total, up from 113), proving the endpoint reads real property values (a deliberately atypical flag combination via `@TestPropertySource`) rather than echoing a guess, plus last-activity cases for a measurement source, for HUB, and for a source with no activity (null). Frontend: `npm run lint` and `npm run build` both clean; `RESCUETIME` added to the frontend's own `RoutineItemSource` type, which had been missing it. Verified end-to-end via Docker Compose: the real, already-seeded "deep work" routine item (source WAKATIME, countRule "30 active minutes") rendered correctly in both the status section and the counting table; HUB showed a real last-activity timestamp from earlier verification. 390px mobile layout not confirmed live — same `resize_window` tool limitation noted in Phase 5, flagged in `frontend.md` rather than claimed as passing. **With this, Phase 6 is fully done.** Phases 7 (reader's notes) and 8 (the hillside, gated on an accounts/auth decision) remain entirely unstarted |
| 2026-10-17 | Scope | Decided | Before building Phase 7: flagged two real decisions rather than guessing. (1) Which LLM assesses reader's notes — no provider exists in this codebase yet. User chose the Anthropic Claude API. (2) How the new `Book` entity (chapter progress, author) relates to the existing `current_reading_log` (a plain title log, written manually and by hub-sync). User chose: `Book` supersedes it — hub-sync writes `Book` rows going forward, `current_reading_log`'s existing rows and manual endpoint stay as-is for history and current Flock/Tally use |
| 2026-10-17 | Phase 7 (`Book` entity) | Done (Phase 7 groundwork, not complete) | New `books` table (migration V11): title, author, currentChapter, totalChapters, source (reuses `ReadingSource`). `GET /api/books`, `GET /api/books/current`, `POST /api/books` (`BookController`). `HubSyncService.syncCurrentBook()` now writes a `Book` row instead of a `CurrentReadingLog` row, per the scope decision above — `current_reading_log`, its endpoint, and Flock's manual form are untouched and keep working, they just stop getting hub-sync's automatic updates. No `chapters` table: a book's progress is two plain integers, matching `docs/design.md`'s "chapter progress" framing rather than a maintained catalog. 3 new backend tests (121 total, up from 118): `BookApiIntegrationTest` (create, current, list-newest-first, in one method to avoid the shared-Postgres-per-class ordering risk found in Phase 6's measurement-ingest tests), a separate `BookApiNoBooksYetTest` for the 404 case, plus `HubSyncServiceTest`'s two current-book tests updated to assert on `BookRepository`. Verified: `./mvnw clean test`, all passing. Not built this slice, deliberately: chapters as their own concept (folded into `Book`'s two integers instead), reader's notes, the Claude API assessment call, the named ratio threshold, the goal-hours rollup, and the Reading frontend page. See `backend.md`, `database.md`, `hub-sync.md`, `testing.md` |
| 2026-10-17 | Phase 7 (reading note LLM assessment) | Done (Phase 7 groundwork continues, not complete) | Intelligence service's `POST /reading-notes/assess`, the first LLM call in this codebase: takes `{noteText, bookTitle, currentChapter, totalChapters}`, calls Anthropic's Claude API directly via `httpx` (no SDK, matching the service's existing plain-HTTP style), and returns `chapterPlaced`, `ownWordsPercentage`, `ideasConnected`, `quotedPhrases`, `questionBack` per `docs/design.md`'s Reading page. The model is instructed to answer with only JSON; `assess_note.parse_assessment_response` (pure function) extracts and validates it, raising on a missing field, an invalid `ownWordsPercentage`, or no JSON found at all. Fails closed (503) with no `ANTHROPIC_API_KEY` set. 18 new intelligence-service tests (44 total, up from 26): 14 for the pure parser, 4 for the HTTP layer (success, no key, Anthropic error, unparseable response), all against a faked Anthropic API via `respx`. **Also verified against the real Anthropic API**: a real key (available in this session's environment) reached the real endpoint with the correct request shape and got a real account-billing error back — not a code defect, but it exposed a real bug (an unhandled `httpx.HTTPStatusError` turning into an opaque 500), found and fixed on the spot (now a clean 502 with the real upstream message), with a regression test added before moving on. `Dockerfile` now copies `assess_note.py`; `infra/.env.example` and `docker-compose.yml` carry `ANTHROPIC_API_KEY`. Verified: `pytest` (44/44) and a live Docker Compose call to the real API, both passing. Still not built: backend storage for notes themselves, the named ratio threshold, the goal-hours rollup, and the Reading frontend page. See `intelligence-service.md`, `testing.md` |
| 2026-10-17 | Phase 7 (`ReadingNote` storage) | Done (Phase 7 groundwork continues, not complete) | New `reading_notes` table (migration V12): a book reference, the raw `noteText`, and the intelligence service's assessment fields, all nullable since saving a note doesn't require a successful assessment first. `GET /api/reading-notes?bookId=`, `POST /api/reading-notes` (`ReadingNoteController`), 400 on an unknown `bookId`. `ideasConnected`/`quotedPhrases` are stored as JSON in a TEXT column via a new `StringListConverter`, not their own tables, since nothing queries into individual entries. 4 new backend tests (125 total, up from 121): one covering create/list-by-book/list-all together (same shared-Postgres-per-class reasoning as `BookApiIntegrationTest`), plus separate methods for a blank note, an unknown book, and an out-of-range percentage. Verified: `./mvnw clean test`, all passing. Still not built: the named ratio threshold, the goal-hours rollup, and the Reading frontend page — the last pieces of Phase 7 |
| 2026-10-17 | Phase 7 (named ratio threshold + goal-hours rollup) | Done — **only the Reading page remains for Phase 7** | New `ReadingNoteService` (coordinates `ReadingNoteRepository` and `DailyLogRepository`, same pattern `TaskService` already set): `OWN_WORDS_THRESHOLD = 70`, the named ratio threshold PROJECT.md's Phase 7 description asks for. A note at or above it marks every `source = READER_NOTES` routine item done for today (`daily_logs.source` gains an eighth value, `reader-notes`, migration V13), same find-by-source/log-once-per-day pattern as the four Phase 6 syncs. Below the threshold, or with no assessment, the note still saves, nothing gets marked. The goal-hours rollup itself needed **no new intelligence-service code**: a reading note's log carries no `measuredMinutes` (a written reflection has no duration, same reasoning as GitHub's commit-count logs), so `/tally`'s existing hours chart correctly doesn't count it, while `daysShownUp`/`routineShownUp` correctly do, since those only need the log to exist. 5 new backend tests (130 total, up from 125): at/above threshold creates a log, below threshold doesn't, no assessment doesn't, two qualifying notes the same day create only one log (tested before the no-routine-item case, per the project's "test the double-count case first" rule), no matching routine item still saves the note. Verified: `./mvnw clean test`, all passing. Only the Reading frontend page is left to finish Phase 7 |
| 2026-10-17 | Phase 7 (Reading page) | Done — **Phase 7 complete** | New `/reading` page: current book card (title, author, chapter progress), `NewBookForm` when no book is set yet, `ReadingNoteForm`, "What the Shepherd read." (the newest note's assessment, or a plain "wasn't assessed" message), and an earlier-notes list, per `docs/design.md`'s own layout. `submitReadingNote` (new Server Action) calls the intelligence service's `POST /reading-notes/assess` first, then `POST /api/reading-notes` with whatever came back — or nulls if the call failed, never blocking the save. Added as a sixth Shell nav link, in `docs/design.md`'s stated order. `npm run lint`/`build` both clean. Verified end-to-end via Docker Compose: created a real book, submitted a real note, and the page correctly called through to the **real** Anthropic API — which failed on the same account-billing issue found during the intelligence-service's own verification, caught cleanly and shown as "wasn't assessed," not a crash. Confirmed in the database directly: the note saved with null assessment fields and correctly created no `daily_log` (below the implicit threshold with no score at all). 390px mobile layout not confirmed live, same `resize_window` tool limitation as Watering holes. **With this, Phase 7 is fully done.** Only Phase 8 (the hillside, gated on an accounts/auth architecture decision) remains |
| 2026-10-17 | Scope | Decided | Before any Phase 8 code: three accounts/auth architecture decisions asked and confirmed with the user, per this phase's explicit stop-and-discuss gate. (1) The hillside compares users within **one shared deployment** with real accounts, not separate single-user instances opting into anonymous reporting — a much bigger change than the rest of this app, since every table will eventually need a `user_id`. (2) **Both** password and GitHub OAuth login are supported. (3) **Next.js owns the login session**; this backend only verifies a signed token Next.js's server attaches to its own calls, never renders a login page or handles an OAuth redirect itself |
| 2026-10-17 | Phase 8 (accounts groundwork) | Done (groundwork only, Phase 8 not complete) | New `users` table (migration V14): email, optional bcrypt password hash, optional GitHub id, a DB check constraint requiring at least one. `AuthService`/`AuthController`: `POST /api/auth/register`, `POST /api/auth/login` (generic 401 on any failure, never which part was wrong, to prevent email enumeration), `POST /api/auth/github-login` (finds-or-creates by GitHub id, links to an existing password account by email; protected by its own `AUTH_INTERNAL_TOKEN` since it has no proof of its own that the caller actually verified GitHub — same fail-closed rule `HEALTH_INGEST_TOKEN` already follows). `GET /api/users/me`, the one endpoint this phase requires a valid JWT for — not a real feature, proof the verification wiring works end to end. New dependencies: Spring Security, `spring-boot-starter-oauth2-resource-server`. Found and fixed a real bug while wiring this in: a single `SecurityFilterChain` with JWT validation broke five already-passing tests by intercepting `MeasurementIngestController`'s and `github-login`'s own `Authorization: Bearer` app-level tokens as failed JWTs, even on `permitAll` paths; fixed by scoping the JWT filter to only `/api/users/**` via a second filter chain. 17 new backend tests (147 total, up from 130), including one that builds a real signed JWT with the actual shared secret (not Spring Security's mock-auth test support) to prove real signature verification works, not just the authorization rule around it. Verified: `./mvnw clean test` (147/147) and a live Docker Compose smoke test — register, login (right and wrong password), the protected endpoint rejecting an unauthenticated request, and every existing endpoint (confirmed via `/api/books`, `/api/measurements`) completely unaffected. **Deliberately not touched:** no existing table has a `user_id`, no endpoint but the one proof-of-concept requires login, the Next.js session/login UI/GitHub OAuth app registration is not built, and existing dev data has not been migrated to a seed user. See `backend.md`, `database.md`, `testing.md` |
| 2026-10-17 | Phase 8 (optional user scoping + 10-user seed data) + frontend theme toggle | Done (groundwork continues) | `routine_items` and `daily_logs` gained an optional, nullable `user_id` (migration V15) — the two tables that drive "days shown up." Additive, not enforced auth: `userId` is an optional query param/body field on both controllers; omitted, every existing call (the frontend, every Phase 6 sync service) behaves exactly as before, untouched. `RoutineItem.assignUser`/`DailyLog.assignUser` are plain post-construction setters, not new constructor parameters, so no existing call site needed to change. 3 new backend tests (150 total, up from 147): two users' routine items and daily logs stay isolated from each other when listed by `userId`, and an unknown `userId` on create fails. Seeded 10 real users through the live API (`/api/auth/register`, `/api/routine-items`, `/api/daily-logs`) with their own distinct routine items and a spread of daily logs, for manual multi-user testing — not a migration, not committed data, created against the running dev stack. Also added a frontend theme toggle (`components/ThemeToggle.tsx`, desktop sidebar): cycles Auto/Light/Dark, overrides the system `prefers-color-scheme` setting via `data-theme` on `<html>` plus a pre-paint script to avoid a flash of the wrong theme, persisted to `localStorage`. Verified: `./mvnw clean test` (150/150), `npm run lint`/`build` clean, and the full stack left running against real seeded data per the task's request. Still not built: enforced auth on any endpoint, the Next.js session, real per-user goals, and the hillside feature itself |
| 2026-10-17 | Scope | Decided | User supplied a static HTML/CSS design reference (nav icons, exact copy, `tokens.css`) comparing the canvas design to this app's actual implementation. Found real, structural gaps across every page (nav had no icons, Reading's H1 was the nav label not the real headline, Watering holes' cards were much plainer than the reference, Hillside didn't exist). Agreed scope: bring Today/Reading/Watering holes to fidelity, then finish Phase 8 (the hillside itself) matching the same design, before a later pass on Path/Flock/Tally |
| 2026-10-17 | Phase 8 (design fidelity pass + the hillside itself) | Done — **Phase 8's core feature now exists** | Design fidelity: `globals.css` gained `--field-edge`/`--glass-edge` tokens and a shared `.field` style; `Shell.tsx` got real nav icons (copied from the reference), active-link state, a live "N of M" Today badge and a real "kept watch for N days" footer line (not fabricated numbers); `TaskCard`'s done check is now solid-filled with a white check, and auto-counted chips turn amber→green on completion. Today gained a real eyebrow date, sub-headline, and a Shepherd card using real `suggestion.reason` data. Reading gained a cover placeholder, a real progress bar, a "Counts for today" chip, a score grid, inline-marked quoted text (`lib/markQuotes.ts`, tested), and the "How a note is read"/"Your notes stay yours" cards that were missing entirely. Watering holes gained grouped, coloured source cards. **The hillside**: new `GET /api/hillside` (`HillsideService`, computed fresh from `daily_logs`/`routine_items`/`users` on every call, nothing stored) ranks participants by days-shown-up per field (reusing the same 3-distinct-routines threshold `tally.py`'s tally stick already uses for "general") and window (7/28 days), with deterministic, id-derived pseudonyms (`HillsidePseudonym`, never from email or name) and a "walking near you" neighbour list. `asUserId` is an explicit demo-mode parameter, not real auth — documented, not hidden. New `/hillside` page matches the reference's layout: field/window segmented controls as server-rendered links, a band card, a histogram, the neighbour list, and the "why this hillside is calm" rules card. 9 new backend tests (159 total, up from 150): pseudonym determinism/shape, ranking, private-item exclusion, the general-field threshold, and the HTTP layer. 13 new frontend tests (formatDate, markQuotes). Verified: `./mvnw clean test` (159/159), `npm run lint`/`build`/`test` all clean, and live end-to-end with real seeded data — confirmed a real computed band, rank, and neighbour pseudonyms for an actual seeded user on `/hillside?field=READING&asUserId=2`. Still open: Path/Flock/Tally's own fidelity pass, a real hillside opt-in concept, pseudonym rotation, and replacing `asUserId`/`userId` with real auth once the Next.js session exists |
| 2026-10-17 | Phase 8 design fidelity: Path, Flock, Tally | Done | `lib/weekDates.ts` (tested: `mondayOf`, `weekDates`, `addDays`) powers Path's rewrite: a real "D to D Month" eyebrow, Last/This/Next week navigation as server-rendered links, a legend, and per-day status dots (tended/wandered/today/ahead) computed from each day's actual tasks via `GET /api/tasks?date=` — never `materializeDay`, which would wrongly create tasks for a week nobody has opened yet. Flock's routine items now render as coloured-initial-square rows (colour by category) with a `hillsideField` chip alongside the existing source/Private chips, plus a real "N routines, M goals" eyebrow. Tally's notches are now grouped in fives matching the reference, with a real "N notches in M days." headline and a "Since {date}" eyebrow. 6 new frontend tests (`weekDates.ts`), 17 total. Verified: `npm run lint`/`build`/`test` all clean, and all three pages checked live against the running Docker stack with real data (Path correctly showed a tended/today/ahead mix for an actual week; Flock showed real category groupings including the hillside demo items from the previous slice; Tally showed grouped notches and a real headline). **With this, every view has had the design-reference fidelity pass.** Remaining Phase 8 work is architectural, not visual: a real hillside opt-in, pseudonym rotation, and replacing demo-mode `asUserId`/`userId` with the Next.js session once it exists |

## Rules for working on this project

- TDD is not optional. Write the failing test before the implementation for every new piece
  of logic.
- Every component doc in `docs/` gets updated in the same session as the code change it
  describes. Do not batch doc updates for later.
- No secrets, API keys, or credentials in any file. Use environment variables and reference
  them by name only.
- Build only the current phase. Do not implement later-phase logic while working on an
  earlier one, even if it looks easy to add in passing.
- Do not start Phase 4 (active reprioritization) until Phase 2 has real logged data behind
  it, not synthetic data, and it's been mutually confirmed the statistics are actually
  usable, not just present.
- The `Current book:` line in UMWAYI's `journal/TEMPLATE.md` and the matching string checked
  in `docs/hub-sync.md` must be changed together, in the same change. If one changes without
  the other, hub-sync silently stops matching, with nothing to reveal it, no error, no failed
  test, the book simply stops updating.
- See `docs/DOCS_MAINTENANCE.md` for the full doc update process.
- If anything in these docs is ambiguous or wrong, flag it back rather than silently deciding
  and proceeding. This project is tracked alongside a separate journaling process; decisions
  made silently here don't get recorded there.