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
  frontend/ <- Next.js source (not yet created)
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
`localhost:8080`, and the intelligence service on `localhost:8000`, wired together with the
env vars in `infra/docker-compose.yml`. If port 5432 is already taken locally (a native
Postgres install, for instance), don't edit the compose file — override the host port for a
local run only, e.g. `docker compose -f infra/docker-compose.yml -f <override-file> up -d`
with the override remapping `postgres`'s `ports` to `"5433:5432"`, and point
`ROUTINE_MACHINE_DB_URL` at that port if running the backend outside Docker too.

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

**Frontend:** not yet built. Commands added here once it exists.

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

Current phase: **entering Phase 3.** Phases 1 and 2 built and verified. Phase 4 remains
gated on real, weeks-of-usage data behind Phase 2's statistics, which doesn't exist yet,
only the data produced by manual verification so far.

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