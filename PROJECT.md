# Routine Machine

## What this is

A system that logs daily routine completion and daily learning, and over phases moves from
recording, to detecting patterns, to a fixed schedule with a tracking frontend, to actively
suggesting what to do next. Each person sees only their own records; one owner account runs the deployment.

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
5432 is already taken locally (a native Postgres install, for instance), set `POSTGRES_HOST_PORT`
(for example `5433`) in `infra/.env`; the services reach Postgres inside Docker and are not
affected. Point `ROUTINE_MACHINE_DB_URL` at that port if running the backend outside Docker too.

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
9. Hillside v2 and the credit ledger: an evidence-to-credit pipeline (`evidence_events`,
   `daily_credits`, a per-day cap, a versioned scoring rule), weekly seasons, and a
   transparent ledger with "question a credit." Per `docs/product-direction.md`.
10. Profile and analogies: interests, learning preferences, time zone and currency; an
    intelligence-service analogy endpoint explaining a topic through one interest, always
    alongside the precise definition; wired into Today's staff card.
11. The granary: daily small-saving entries, "the pause" decision log with 24-hour/30-day
    follow-ups, and M-Pesa statement import (category totals only, file deleted after
    parsing). Never holds or moves money, never gives advice wording.
12. Organisations and cohorts: `organisations`, `cohorts`, role-based memberships and
    seats, CSV invites, and an admin aggregate view (the pilot measures, each with its
    denominator; groups under 5 hidden).

Phases 5 to 8 are inserted alongside Phase 4, not after it: none of them need Phase 2's real
usage data, so Phase 4's gate is unchanged by their existence. Phases 9 to 12 build the
direction in `docs/product-direction.md` (developer-centred, usable by training programmes,
learning explained through each person's interests, small-money discipline, and a hillside
that cannot recreate hype culture), one phase at a time, each built, verified, and reported
on its own before the next starts.

Current state (25 October 2026; the dated detail is in the status log in `docs/status-log.md`):

- **Phases 1 to 3 and 5 to 12 are done.** Logging and suggestion, pattern statistics, the fixed weekly schedule and tracking frontend, the shepherd frontend, watering holes, reader's notes, the hillside and accounts, the credit ledger, profile and analogies, the granary, and organisations with cohort dashboards.
- **Phase 4 (active reprioritisation) is not started on purpose.** It waits on weeks of real logged data behind Phase 2's statistics. That data does not exist yet, only what manual verification produced.
- **Accounts and access control are in.** Password login exists; GitHub and Google sign-in are deferred. Every personal route takes its user from the signed token, never from the request. One account, named by `OWNER_EMAIL`, is the owner and sees the operations page and API documentation.
- **Staged work since Phase 12, all done:** access control (Prompt 2), observability and the owner-only operations page (Prompt 3), API documentation for both services (Prompt 4), consent-first product analytics (Prompt 5), the repository standard and first-run flow (Prompt 6: README, CI green on GitHub, MIT license, the new-user walk-through fixes, goal creation, the opt-in demo profile), and the understanding-first rules (Prompt 7: the two hub documents, a daily reminder on Today and a stronger one after missed days, the weekly failure-mode question with a day each person chooses, and the 111-row concerns record in `docs/architecture.md`).
- **Not built:** an admin screen to create an organisation or invite people by CSV (the cohort dashboard is read-mostly), hosting and deployment, a database backup (Backlog O). The weekly layout is entered through Flock's schedule-template form, and real durations are still an open question (`docs/schedule.md`).
- **Open work** is in the Backlog table below. Deployment secrets must be replaced before real users (Backlog).

## Repo structure decision

Monorepo, same reasoning as settlement-engine: one contributor, tightly coupled services,
no reason yet to pay the coordination cost of separate repos. Revisit if that changes.

**Folders stay as they are (decided 2026-10-25, Prompt 6).** `backend/`, `intelligence-service/`, `frontend/` and `infra/` keep their names. Renaming them to `apps/` and `services/` would break every doc path and every command in these docs, and gives no user anything.

## Status log

The status log lives in `docs/status-log.md` (moved 2026-10-26, archive entry A-001 in the UMWAYI hub). Add a row there when a phase starts or finishes or a real decision gets made. Read it only when a task needs history.

## Backlog

Work agreed but not started. Remove a row when it ships (the status log records it).

| Added | Item | Why |
|---|---|---|
| 2026-10-21 | Reading page: an "I'm reading something else" control that adds a book and makes it current | The add-book form shows only when no book exists, so a user with a book cannot switch in the UI. The only other route is hub-sync's `Current book:` line, which needs the journal key. Found when the owner's Sapiens showed as Atomic Habits (the page reads the `books` table, Sapiens was in the legacy `current_reading_log`). Fixed for now by adding the book through the API |
| 2026-10-21 | **Before real users or any production deployment: replace every development secret and credential.** Production gets its own values, generated there and never copied from this machine. List: `AUTH_JWT_SECRET` (the dev value lives in git-ignored `infra/.env`; generate a new one with `openssl rand -base64 48`, same value for backend and frontend; changing it signs everyone out); the owner account's password (set in the dev database during local testing; set a new one, and prefer a different owner email or account for production); `OWNER_EMAIL`; `AUTH_INTERNAL_TOKEN` and `HEALTH_INGEST_TOKEN` (blank in dev, so those two endpoints are off; generate real ones when enabled); `UMWAYI_JOURNAL_KEY_B64`, `WAKATIME_API_KEY`, `RESCUETIME_API_KEY`, `ANTHROPIC_API_KEY` (supply real ones for production); the dev database user and password in compose. Do this when the production infrastructure is built, not before | Compose no longer ships a default JWT secret (it refuses to start without one) but the development one is a throwaway for this machine. The owner password was shared in a chat session for testing, so treat it as exposed: swap it before real users arrive. Never put any of these values in a file the repository tracks |
| 2026-10-21 | Production secrets handling: decide where production secrets live (a secrets manager or the host's environment, not files in the repo), and rotate them on a schedule | Part of building the production infrastructure; concern list item on key management |
| 2026-10-25 | **D. Loki's `/ready` answered 503 briefly three times.** Mitigated: the owner page retries a failing probe once. **Likely reason found**: Loki's calls to itself time out at the same moments Prometheus's scrapes of the backend, the intelligence service and Prometheus itself are all slow (20 such moments in 12 hours), so the whole Docker environment stalls and Loki reports it loudest. **Not found**: what starves it outside my own builds and test runs | Details in `docs/observability.md` (piece 1). Open: watch a quiet day without builds; check Docker Desktop's resource limits |
| 2026-10-25 | **F. Prompt 5 (PostHog analytics) is built; four things are Miguel's.** All eight named events are sent, consent first, with the build-failing field-name test. **Needs from Miguel**: look at PostHog's Live events and check the events appear and the person is only a number; switch on "Discard client IP data" in PostHog; decide **how long PostHog keeps events** and add it to `/privacy`; have the Kenya Data Protection Act wording on `/privacy` reviewed (and set `PRIVACY_CONTACT`, row K). Details in `docs/frontend.md`, "Analytics" | I cannot open your PostHog or give legal advice |
| 2026-10-25 | **G. The staged prompts are done; what is left is the "Later" list.** Prompts 1 to 7 are built and recorded in the status log. Prompt 6's one open piece is the before images for walk-through items c, d and e (row L; a and b have them). **Not built, by design, the "Later" list**: cloud hosting and file storage, CI/CD deployment, Kubernetes, caching at the edge. The gaps the concerns record found are rows N to U below | What remains of the staged prompts |
| 2026-10-25 | **H. Still to do from observability and the API docs**: measure the Prometheus and Loki volumes again after seven days (baseline 25 Oct 2026 16:52 UTC: Prometheus 28.55 MB, Loki 0.41 MB, Grafana 302.6 MB); ReDoc is not provided (both specs are in the Swagger UI) | Said in `docs/observability.md` and `docs/backend.md`; listed here so they are not forgotten |
| 2026-10-25 | **I. HUB and GitHub fetches fail often: causes found, fix built, waiting for a token.** The recent failures are GitHub's unauthenticated quota (60 an hour per address; the sync spends 4 a run, every 15 minutes and at every start). The earlier `ConnectException` ones were the JVM failing to resolve `api.github.com` in the first seconds after a container was created; each call now retries once. An optional `GITHUB_TOKEN` (5,000 an hour) is built and proved on the wire with a fake token (GitHub answered 401) | **Miguel**: create a fine-grained token with no permissions and public-repositories read-only, put it in `infra/.env` as `GITHUB_TOKEN`, restart the backend (`docs/hub-sync.md`). Not checked: a real token's limit. Not seen live: the retry firing |
| 2026-10-25 | **K. Two things only Miguel can switch on.** (1) GitHub private vulnerability reporting: repository Settings, Code security, "Private vulnerability reporting". `SECURITY.md` points to it, so until it is on the link leads nowhere. (2) `PRIVACY_CONTACT` in `infra/.env`: an address or web page for people to reach about their data; the public `/privacy` page says "the person who runs this app" until it is set (do not commit the value) | Both are settings in accounts I cannot change |
| 2026-10-25 | **L. The Prompt 1 walk-through, all items approved by Miguel on 25 Oct 2026.** Done: a, b, c, d (covered by tests, not re-tapped), e, the empty states on Flock, Path and Tally, the duplicate-goal message, and backend time-zone validation (parts 3 to 5). **Still open**: a friendly message when a bad time zone is typed on Profile (it shows the error page; nothing is saved); a day-zero account's Tally says "Since 2026-09-28"; **before and after images for items c, d and e** (check 3 is met for a and b, see `docs/frontend.md`) |
| 2026-10-26 | **N. Today can hang on a model call.** After its parallel calls, `frontend/app/today/page.tsx:65-67` awaits the analogy call. For a (topic, interest) pair not seen before that is a model call of up to 30 s (`intelligence-service/main.py:350`), and the frontend sets no timeout (`frontend/lib/api.ts`). Analogies are on by default for every account (`User.java:99-100`). Later views are fast because the answer is cached. **Options for Miguel**: show the page first and load the analogy after (a streamed or client-side fetch); give that one call a short limit and show nothing when it runs out; add a timeout to every frontend fetch (concern 11). Found by the concerns record (`docs/architecture.md`, rows 11 and 94) | A page that can wait 30 s on its first view, for anyone who has chosen an interest |
| 2026-10-26 | **O. There is no database backup, and no restore has ever been tried.** The owner's real data lives in one local Docker volume (`routine_machine_postgres_data`) on one machine; a `docker compose down -v` in the wrong project or a lost disk ends it. I searched the repository for backup tooling and found none. **Options for Miguel**: a scheduled `pg_dump` to another disk or a cloud store, plus a written and **tested** restore (a backup that has not been restored is a hope). Found by the concerns record (`docs/architecture.md`, rows 83 and 84) | The most serious gap the record has found: one mistake or one disk failure loses everything |
| 2026-10-26 | **P. "Today" comes from the server's clock, not the person's time zone. Fixed in three places (the frontend, the intelligence service, reading notes); only the owner-only syncs remain.** **Fixed**: Today, Path, Tally and the sidebar's count now use the date in the person's saved zone (`lib/userToday.ts`, `lib/auth/userToday.ts`; 8 tests; checked live, below in the status log). **Same cause in other places** (a and b are done, c is open): (a) **fixed on 26 Oct 2026: the intelligence service's tally window and patterns now use the person's day** (`intelligence-service/user_today.py`); (b) **fixed on 26 Oct 2026: a reading note now logs the author's day** (`ReadingNoteService`, with a `Clock`); (c) the sync services use `LocalDate.now()` (owner only). **Left as it is on purpose**: the hillside's season start, so everyone shares one board. Found by the concerns record (`docs/architecture.md`, row 96) | The zone fix for new accounts (Prompt 6) is only half useful until all of these use it |
| 2026-10-26 | **Q. Security basics before public hosting.** No rate limit anywhere, `/api/auth/login` included (concern 1, 75); no TLS and no encryption between services (71, 73); **a session token lives 7 days, cannot be revoked and has no key rotation** (70); development secrets are still in the compose file and there is no rotation schedule or secret scan in CI (67; see the two 2026-10-21 rows above); no WAF (74). `docs/architecture.md` | Everything here is fine on one machine and wrong on a host |
| 2026-10-26 | **R. Call safety.** The frontend's `fetch` has no timeout (11); there is no circuit breaker anywhere (10); **the intelligence service returns a server error when the backend is unreachable** because nothing catches it (95); no test simulates a partition. Row N covers Today's model-call wait. `docs/architecture.md` | A hung or failing service should cost one panel, not the whole page |
| 2026-10-26 | **S. Running and deploying safely.** The compose file has no `healthcheck:` and `depends_on` waits only for start (56, 57); no graceful-shutdown setting (54); **no alert reaches anyone** (58, 62); the five scheduled syncs have no lock (a second backend instance would run each twice) and **no alert fires for a sync that stops without failing** (21, 38, 46); rollbacks are documented but never rehearsed (55); `TRACING_ENABLED` is missing from `.env.example` (51). `docs/architecture.md` | The gaps between "it runs" and "it can be run by someone else" |
| 2026-10-26 | **T. Know the limits.** No load test, no p99, no SLO or SLI or error budget (63 to 65, 92, 93); JVM memory and GC metrics reach Prometheus but no panel or alert uses them (41, 42); the application containers have no memory or CPU limit (47); no query plan has been read and no test counts queries, so an N+1 could return unseen (26, 27); the connection pool is HikariCP's default, never sized (28). `docs/architecture.md` | Numbers to decide before a training programme's cohort uses it together |
| 2026-10-26 | **U. Guards and tests still missing.** No optimistic locking, so the last write wins (36); no idempotency key, so a double-submitted form without a database unique rule (a pause) can be recorded twice (14); no test of our own for CSRF (77) or for a script in a reading note staying inert (79); the health-ingest push is not signed (101); the Watering holes page does not say synced data can be 15 minutes old (35); no tool proposes dependency updates (108); no clean build time recorded (107). `docs/architecture.md` | Small, cheap and each one a real hole |

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
- Work by the understanding-first rules (`docs/understanding-first.md`) and keep the concerns record for the 111 concepts (`docs/engineering-concerns.md`; this project's record is `docs/architecture.md`). Copies of the UMWAYI hub's files; change them there first.
- If anything in these docs is ambiguous or wrong, flag it back rather than silently deciding
  and proceeding. This project is tracked alongside a separate journaling process; decisions
  made silently here don't get recorded there.