# Routine Machine

## What this is

A system that logs daily routine completion and daily learning, and over phases moves from
recording, to detecting patterns, to actively suggesting what to do next. Built for one user.

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

## The underlying problem

Most personal habit tools either do nothing but record, or try to be intelligent from day
one with no data to be intelligent about. This project treats that as a sequencing problem,
not a scope problem: build the data layer correctly first, only add intelligence once there
is real data to learn from, and gate the most ambitious feature (active reprioritization) on
evidence, not on a target date.

## Tech stack

- Backend: Spring Boot (Java), same core stack as settlement-engine
- Intelligence service: Python, FastAPI
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
  backend/                      <- Spring Boot source
  intelligence-service/          <- Python source
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

## Build phases

1. Logging and rule-based suggestion: record daily routine completion and learning topics,
   suggest a topic weighted toward whichever of the three goals has had the least recent
   attention. No real intelligence yet, correct data modeling only.
2. Pattern detection: completion rates, streaks, skip-heavy days, and a first descriptive
   correlation between logging a learning topic and completing more or fewer routine items
   that day. Statistics on structured data, not a model.
3. Active reprioritization: suggest a next action for the current day, with a stated reason
   tied to a Phase 2 finding. Does not start until Phase 2 has produced real data, weeks of
   it, not a synthetic sample. This is a gate, not a target date.

Current phase: **Phase 2, built and verified.** Pattern detection (`GET /patterns`) is built,
tested, and confirmed working end-to-end via the full Docker Compose stack. Phase 3 remains
gated: it needs real, weeks-of-usage data behind Phase 2's statistics, which doesn't exist yet
— only the data produced by manual verification so far.

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
| 2026-09-18 | Phase 1 | Done | Backend: 4 entities (Goal, RoutineItem, LearningTopic, DailyLog), Flyway migrations, REST API, 23 tests passing (Testcontainers Postgres). Intelligence service: FastAPI `/suggestion` endpoint reading the backend's API, rule-based goal/topic selection, 8 tests passing. Verified end-to-end via `docker compose up --build`: created a routine item, a learning topic, a daily log, and got back a correct suggestion. Found and fixed a real bug in the process — `GET /api/learning-topics` 500'd on a Hibernate lazy-loading exception that no existing test had caught, since only the POST path asserted on a response body; see `backend.md` and `testing.md` |
| 2026-09-18 | Doc cleanup | Done | Fixed a stale test count in testing.md (said 22, actually 23) and explicitly logged the decision to leave intelligence-service.md's Phase 2 open question open rather than answer it prematurely |
| 2026-09-18 | Phase 2 | Done | Intelligence service: `GET /patterns` (completion rates, streaks, skip-heavy days, topic/routine correlation), computed by the pure `compute_patterns` function, 18 intelligence-service tests passing (up from 8). Backend: `RoutineItemResponse` now exposes `createdAt`, the one piece Phase 2 needed that Phase 1's API didn't already have. Verified end-to-end via Docker Compose with real routine items, logs, and a learning topic. Found and fixed a second real bug this way — streaks counted a log backdated to before its routine item existed, while completion rate already excluded it; both now agree. See `intelligence-service.md` and `testing.md` |

## Rules for working on this project

- TDD is not optional. Write the failing test before the implementation for every new piece
  of logic.
- Every component doc in `docs/` gets updated in the same session as the code change it
  describes. Do not batch doc updates for later.
- No secrets, API keys, or credentials in any file. Use environment variables and reference
  them by name only.
- Build only the current phase. Do not implement Phase 2 or 3 logic while working on Phase 1,
  even if it looks easy to add in passing.
- Do not start Phase 3 until Phase 2 has real logged data behind it, not synthetic data.
- See `docs/DOCS_MAINTENANCE.md` for the full doc update process.
- If anything in these docs is ambiguous or wrong, flag it back rather than silently deciding
  and proceeding. This project is tracked alongside a separate journaling process; decisions
  made silently here don't get recorded there.
