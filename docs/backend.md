# Backend

## Purpose

Spring Boot service owning the data: routine items, daily logs, learning topics, goals, and
(from Phase 3) the fixed schedule and its tasks. Exposes a REST API. No suggestion or
pattern-detection logic lives here, that belongs to `intelligence-service.md`. Also holds
hub-sync (`HubJournalReader`, `HubSyncService`, `GitHubContentClient`), which reads UMWAYI's
journal and essays and turns them into routine completions, and the watering-hole syncs
(starting with `WakaTimeSyncService`), which do the same for external activity trackers. See
below and `hub-sync.md`.

## Current state

Phase 1 and 2 built: Spring Boot 3.5, Java 21, PostgreSQL via Flyway migrations. Four
endpoints, matching the four Phase 1 entities in `database.md`:

- `GET /api/goals?userId=`, `POST /api/goals`, list and create goals. The three seeded goals
  (Phase 1) have no `user_id`; until the owner claim hands them to the owner account they are
  the "unowned" goals, which a request with no `userId` still lists (since per-user scoping
  part 2c, *only* those, not every account's goal as it did through Phase 8). Phase 8 added the
  `userId`-scoped creation path: `userId` is optional on both the query (filters the list;
  omitted returns only the unowned goals) and the create body (400 on an unknown
  `userId`, matching every other foreign-key validation in this API). Goal names are unique
  per user (`UNIQUE(user_id, name)`, not a global `UNIQUE(name)` as before Phase 8); creating
  a second goal with the same name for the same `userId` is a 400.
  `GoalRepository.findFirstByName` replaced the old `findByName` for this reason, the lookup
  callers use to find one of the three fixed seeded goals by name can no longer assume the
  name is globally unique, only that it is unique among `NULL`-user goals in practice.
- `GET /api/routine-items`, `POST /api/routine-items`, define and list routine items. List
  responses include each item's `createdAt`, needed by the intelligence service's Phase 2
  pattern detection to bound a completion-rate/skip-heavy-day window per item. Phase 5 added
  `category`, `countRule`, `source` (defaults `MANUAL`), `hillsideField`, `isPrivate`
  (defaults `true`), and an optional `goalId`/`goalName` pair (400 on an unknown `goalId`,
  matching every other foreign-key validation in this API). `findAllWithGoal`/
  `findWithGoalById` use a `LEFT JOIN FETCH` (not the plain `JOIN FETCH` `LearningTopic` uses)
  since a routine item's goal is optional, where a learning topic's isn't.
- `GET /api/learning-topics?goalId=`, `POST /api/learning-topics`, define and list learning
  topics, each tied to a goal. `goalId` filter is optional.
- `GET /api/daily-logs?date=`, `POST /api/daily-logs`, record and list completions. A log
  targets exactly one of `routineItemId` or `learningTopicId`; `source` defaults to `manual`
  and accepts `hub-sync`, planned to also accept `schedule` in Phase 3. `date` filter is
  optional. Phase 5 added an optional `measuredMinutes`, read by the intelligence service's
  `/tally` for goal-hours roll-up; `DailyLog.forRoutineItem`/`forLearningTopic` gained a
  4-argument overload carrying it, the existing 2- and 3-argument overloads default it to
  `null`.

Validation errors and unresolvable foreign keys (bad `goalId`, `routineItemId`,
`learningTopicId`) return 400. No suggestion or pattern-detection logic lives here, the
intelligence service reads this API's data over HTTP to compute its own suggestions.

Phase 3 built: the schedule and its tasks, plus current reading.

- `GET /api/schedule-templates?dayOfWeek=`, `POST /api/schedule-templates` — define and list
  the recurring weekly definitions. `dayOfWeek` filter is optional. `routineItemId` is
  required when `targetType=ROUTINE_ITEM` and rejected when `targetType=LEARNING_SLOT`
  (a `LEARNING_SLOT` template doesn't name a specific topic; see `schedule.md`).
- `GET /api/tasks?date=`, `POST /api/tasks`, `PATCH /api/tasks/{id}` — materialize and track
  daily tasks. A task either names a `scheduleTemplateId` (and, only for a `LEARNING_SLOT`
  template, a caller-resolved `learningTopicId`, since the backend has no way to resolve one
  itself — see `schedule.md`'s frontend-orchestration decision) or is ad-hoc (a `label` plus
  an optional `routineItemId`/`learningTopicId`, at most one). `PATCH` takes `{"status":
  "TODO"|"IN_PROGRESS"|"DONE"}`; transitioning to `DONE` creates a `daily_log` with
  `source=schedule` if the task has a target, transitioning away from `DONE` deletes it,
  handled by `TaskService` (see decisions log). A nonexistent task id on `PATCH` returns 404,
  the one case in this API where the missing thing is the URL's own resource, not a field in
  the body.
- `GET /api/current-reading` returns the latest row from `current_reading_log`, 404 if
  nothing's been logged yet. `POST /api/current-reading` adds a new row, always
  `source=manual`. **As of Phase 7, `current_reading_log` no longer gets new rows from
  hub-sync** (see below); this endpoint and its manual Flock form still work exactly as
  before, they just won't reflect hub-sync's "Current book:" line going forward. Updating the
  frontend to read `books` instead is deferred to when the Reading page itself is built, not
  this slice.

**hub-sync Stage 1 built:** `HubJournalReader`
(`com.routinemachine.core.hubsync.HubJournalReader`), not wired to any endpoint or scheduler.
`readCurrentBook()`:

- Reads `UMWAYI_JOURNAL_KEY_B64` from the environment. Missing or blank, logs one line and
  returns empty; the app starts fine either way, nothing in startup depends on this variable.
- Makes an owner-only-permission temp directory, decodes the base64 into a key file inside it
  (mode `rw-------`), shallow-clones UMWAYI (`git clone --depth 1`, argument list, never a
  shell string) into the same temp directory, and runs `git-crypt unlock <keyfile>` on the
  clone.
- If clone or unlock fails, or a file still starts with the git-crypt header
  (`0x00 'GITCRYPT'`) afterward, treats the journal as locked: returns empty, logs one line
  with no file or entry content.
- On success, reads each file under `journal/entries/`, looks for a `Current book:` line
  (case-insensitive), and returns the newest entry's title, if any.
- Always deletes the key file (zeroed first) and the whole temp directory in a `finally`
  block, success or failure. No unlocked clone or key file is kept between calls.

Runtime image needs `git` and `git-crypt` (`apk add --no-cache git git-crypt` in
`backend/Dockerfile`); neither format is reimplemented in Java. `infra/docker-compose.yml`
passes `UMWAYI_JOURNAL_KEY_B64` through with no default value; `infra/.env.example` documents
the variable with an empty placeholder, `infra/.env` itself is gitignored.

**hub-sync Phase 1 built:** `HubSyncService`
(`com.routinemachine.core.hubsync.HubSyncService`), `@Scheduled` (default every 15 minutes,
`hub-sync.poll-interval-ms`; `hub-sync.enabled=false` turns the whole job off, set in every
test — see `testing.md`). `syncOnce()` is the testable unit the schedule calls:

- `GitHubContentClient` (interface, real impl `GitHubContentClientImpl` over
  `java.net.http.HttpClient` and the public GitHub REST API, no credential) lists
  `journal/entries/`, `site/philosophy/`, and `site/tech/`.
- A journal file's date comes from its `YYYY-MM-DD.md` name; an essay's date comes from its
  earliest commit (`firstCommitDate`, paginated up to 100 commits — undocumented beyond that
  for a path with more history, unlikely for a single essay).
- `hub_sync_processed_files` (keyed by each file's git blob sha, not its path) stops a file
  from being logged twice, including across a rename. A file with no matching `journaling` or
  `writing` routine item (by name, case-insensitive) is still recorded as seen, logging
  nothing, so adding the routine item later doesn't retroactively log every file hub-sync has
  ever looked at.
- Also calls `HubJournalReader.readCurrentBook()` and writes a new `books` row (Phase 7's
  `Book`, see below) only when the title changed from the current book.

`RoutineMachineApplication` now has `@EnableScheduling`.

**Measurements built:** `Measurement` (`com.routinemachine.core.domain.Measurement`), one row per
`(source, metric, day)`. `MeasurementService.recordMeasurement(source, metric, date, value)` is
the single write path a future sync job (WakaTime, ActivityWatch, GitHub, RescueTime) will call
once per day per metric: if a row already exists for that `(source, metric, measurement_date)`,
its `value` and `updated_at` are updated in place; otherwise a new row is inserted. This is the
idempotent-sync groundwork; `WakaTimeSyncService` (below) is the first real caller.

**WakaTime built:** `WakaTimeSyncService`
(`com.routinemachine.core.wakatime.WakaTimeSyncService`), `@Scheduled` (default every 15
minutes, `wakatime-sync.poll-interval-ms`; `wakatime-sync.enabled=false` turns the job off, set
in every test, same pattern as hub-sync). `syncOnce()`:

- `WakaTimeClient` (interface, real impl `WakaTimeClientImpl` over `java.net.http.HttpClient`
  and WakaTime's public summaries endpoint) fetches daily coding totals for the last 7 days
  (`SYNC_WINDOW_DAYS`), wide enough that a day whose total was still settling on the previous
  run gets corrected, not missed. Reads the key fresh from `WAKATIME_API_KEY` on every call,
  same no-caching choice `HubJournalReader` makes for `UMWAYI_JOURNAL_KEY_B64`; a missing key
  logs one line and returns no totals, the app still starts.
- Each day's total (seconds, rounded to the nearest minute) is written to `measurements` via
  `MeasurementService.recordMeasurement(WAKATIME, "coding_minutes", date, minutes)` — upserted,
  so re-running the sync for a day already recorded updates its value rather than duplicating
  the row. A day with zero seconds coded is skipped, not recorded as a zero.
- Every routine item with `source = WAKATIME` (`RoutineItemRepository.findBySource`, Phase 5's
  enum value built for exactly this) gets a `daily_log` for that day, `source = wakatime`,
  `measuredMinutes` set from the same total — but only if one doesn't already exist for that
  routine item and day (`DailyLogRepository.existsByRoutineItemIdAndLogDate`). Unlike
  `measurements`, a daily_log is never updated once written: `measurements` is the table of
  record for a day's possibly-corrected raw value, the log itself is a one-time "this happened"
  fact, same as hub-sync's files.
- `daily_logs.source` gained a fourth value, `wakatime` (migration V7).

Runtime needs `WAKATIME_API_KEY` set (`infra/.env`, gitignored) to actually sync; unset, the
backend still starts and the job simply does nothing each run.

**ActivityWatch built:** `ActivityWatchSyncService`
(`com.routinemachine.core.activitywatch.ActivityWatchSyncService`), same shape as
`WakaTimeSyncService`: `@Scheduled` every 15 minutes
(`activitywatch-sync.poll-interval-ms`/`activitywatch-sync.enabled=false`), fetches the last 7
days (`SYNC_WINDOW_DAYS`) of active-time totals from `ActivityWatchClient`, upserts each day
into `measurements` as `active_minutes`, and gives every routine item with
`source = ACTIVITYWATCH` one daily_log per day (`source = activitywatch`,
`daily_logs.source` now a fifth value, migration V8), created once, never updated.

Unlike WakaTime and GitHub, **ActivityWatch has no cloud API**: `aw-server` runs locally,
normally at `http://localhost:5600`, on whatever machine is actually using the computer.
`ActivityWatchClientImpl` assumes the backend runs on that same machine, reachable at
`ACTIVITYWATCH_BASE_URL` (default `http://localhost:5600`; `docker-compose.yml` instead
defaults it to `http://host.docker.internal:5600`, since the backend there runs in its own
container while `aw-server` runs on the host). This was flagged to the user rather than
decided silently — see the decisions log — and would need rethinking if the backend is ever
deployed somewhere other than the machine `aw-server` runs on. It lists buckets
(`GET /api/0/buckets/`), finds the one whose id contains `"afk"`, fetches that bucket's events
in range, and sums the `duration` of events whose `data.status` is `"not-afk"` per calendar
day (by the event's own timestamp, not corrected for timezone beyond that).

**GitHub built:** `GitHubSyncService`
(`com.routinemachine.core.github.GitHubSyncService`), same shape again, as a **backup
signal** (PROJECT.md's own framing, not a primary source): `@Scheduled` every 15 minutes
(`github-sync.poll-interval-ms`/`github-sync.enabled=false`), fetches the last 7 days of
commit counts from `GitHubCommitClient`, upserts each day into `measurements` as
`commit_count`, and gives every routine item with `source = GITHUB` one daily_log per day
(`source = github`, `daily_logs.source` now a sixth value, migration V9) — created once,
never updated, same as the other two syncs, but with no `measuredMinutes`: a commit count
isn't a duration, so the daily_log for this source carries none.

`GitHubCommitClientImpl` uses GitHub's public events API (`GET /users/{username}/events/public`,
no credential, same as `GitHubContentClientImpl`'s public reads of UMWAYI), counting a
`PushEvent`'s commits against the day the push happened. This is deliberately a backup, not a
full history: the events API only returns a user's most recent public activity (an
undocumented cap, in practice roughly the last 90 days or ~300 events), and pushes to private
repos aren't included. `GITHUB_USERNAME` (default `Miguel-Shinyenyi`, same default pattern as
`GitHubContentClientImpl`'s repo slug) selects whose events to read.

**RescueTime built:** `RescueTimeSyncService`
(`com.routinemachine.core.rescuetime.RescueTimeSyncService`), same shape as
`WakaTimeSyncService`/`ActivityWatchSyncService`: 7-day trailing window, upserts
`measurements` as `active_minutes`, gives every routine item with `source = RESCUETIME` one
daily_log per day (`source = rescuetime`), created once, never updated.

**This one is explicitly optional**, per PROJECT.md's own framing of Phase 6 ("optionally
RescueTime"), and that's a real behavioural difference, not just a label: every other sync in
this phase defaults its `*-sync.enabled` flag to `true` and is only switched off in tests;
`rescuetime-sync.enabled` defaults to **`false`**, in production too. Someone has to
explicitly set `RESCUETIME_SYNC_ENABLED=true` (and `RESCUETIME_API_KEY`) to turn it on at all.
`RoutineItemSource` and `daily_logs.source` both gained a `RESCUETIME`/`rescuetime` value for
this (migration V10, which also finally closes the gap the measurements-table decisions log
flagged: `RoutineItemSource` was missing `RESCUETIME` even though `MeasurementSource` already
had it).

`RescueTimeClientImpl` calls RescueTime's Analytic API Data endpoint at day granularity,
`restrict_kind=overview`, which returns one row per day per productivity level (-2 to 2); a
day's total active time is the sum of every row's seconds for that date, not any single row.
Reads `RESCUETIME_API_KEY` fresh on every call, same as `WAKATIME_API_KEY`.

**Health-data ingest endpoint built:** `POST /api/measurements`
(`com.routinemachine.core.api.MeasurementIngestController`). Unlike the four syncs above,
which all pull, health data has no API for the backend to pull from — this is the push side
instead, the role a companion app would otherwise fill (building that app is explicitly out of
scope for this phase, per PROJECT.md). The request body is `{metric, date, value}`, no
`source` field: every measurement this endpoint writes is `MeasurementSource.HEALTH`, fixed by
the controller, not the caller, so this one narrow endpoint can't be used to spoof any other
source. Delegates to the same `MeasurementService.recordMeasurement` every sync uses, so a
repeat post for the same day and metric updates the existing row rather than duplicating it.

**Token-protected, fail closed:** the request needs `Authorization: Bearer <token>` matching
`HEALTH_INGEST_TOKEN`. If that variable is unset or blank, every request is rejected with 401,
never accepted — an unconfigured token must mean "nobody can push," not "anyone can." This is
a plain header check in the controller, not Spring Security: nothing else in this API needs
auth, and pulling in a whole security framework for one endpoint's one header comparison would
be a disproportionate dependency for what it does.

**Strava: deferred, not built.** `RoutineItemSource.STRAVA` and `HillsideField`'s values
already exist (Phase 5), but no `StravaClient`, no sync service, nothing that calls Strava's
API. This is a deliberate decision, not an oversight: every source built so far authenticates
with a single static secret the backend reads from its own environment (an API key, or for
ActivityWatch/GitHub, no credential at all). Strava is the first source that would need real
user-facing OAuth2 — an authorization-code redirect flow, a stored refresh token, and token
refresh on expiry — which is exactly the shape of infrastructure Phase 8 (the hillside) is
already explicitly waiting to agree an accounts/auth architecture for before any of its own
code gets written. Building Strava's own one-off token store now risks either duplicating that
decision or conflicting with it once it's made. See the decisions log and `PROJECT.md`'s own
framing of this as "a deferred Strava OAuth decision," not an unplanned gap.

**Phase 7 (reader's notes) started: `Book` built.** `Book`
(`com.routinemachine.core.domain.Book`), `books` table: title, author, currentChapter,
totalChapters, source (reuses `ReadingSource`, same `MANUAL`/`HUB_SYNC` values
`current_reading_log` already used). `GET /api/books` lists all, newest first.
`GET /api/books/current` returns the latest row (404 if none), the same latest-row-wins rule
`current_reading_log` used. `POST /api/books` creates one, always `source=manual`.

**`Book` supersedes `current_reading_log`** as hub-sync's write target for the "Current book:"
line (agreed with the user before building, see the decisions log): `HubSyncService.
syncCurrentBook()` now writes a `Book` row instead of a `CurrentReadingLog` row.
`current_reading_log`, `CurrentReadingController`, and the frontend's existing manual form
against it are all untouched and still work; they just won't reflect hub-sync's automatic
updates anymore.

**Reading notes built:** `ReadingNote`, `reading_notes` table: a book reference, the raw
`noteText` the user wrote, and the intelligence service's optional assessment fields
(`chapterPlaced`, `ownWordsPercentage`, `ideasConnected`, `quotedPhrases`, `questionBack`).
`GET /api/reading-notes?bookId=` lists notes (optionally scoped to one book), newest first.
`POST /api/reading-notes` creates one, 400 on an unknown `bookId`, matching every other
foreign-key validation in this API. The assessment fields are all nullable: posting a note
with none of them succeeds, since writing the note is the user's actual action and assessment
is an addition to it, not a precondition — the frontend is expected to call the intelligence
service's `POST /reading-notes/assess` first and include the result here, but doesn't have to.
`ideasConnected`/`quotedPhrases` (string arrays) are stored as JSON in a single TEXT column
via `StringListConverter`, not their own tables: nothing queries into them, they're only ever
read back whole for one note.

**The named ratio threshold and goal-hours rollup built:** `ReadingNoteService`
(`com.routinemachine.core.service.ReadingNoteService`), coordinating `ReadingNoteRepository`
and `DailyLogRepository` the same way `TaskService` already coordinates `TaskRepository` and
`DailyLogRepository` — saving a note is real business logic once it can also mark the day's
reading routine done, not a data-layer passthrough, so it earned a service rather than living
in the controller. `ReadingNoteController` now calls it instead of the repository directly.

- `OWN_WORDS_THRESHOLD = 70`, the named ratio threshold PROJECT.md's Phase 7 description asks
  for: a note whose `ownWordsPercentage` is at least 70 counts as real reflection, not
  mostly-copied text.
- When a note clears the threshold, every routine item with `source = READER_NOTES` gets a
  `daily_log` for today (`source = reader-notes`, `daily_logs.source` now an eighth value,
  migration V13) — same "find by source, log once per day" pattern WakaTime/ActivityWatch/
  GitHub/RescueTime already use, down to the same idempotency check
  (`existsByRoutineItemIdAndLogDate`). A note below the threshold, or with no assessment at
  all (`ownWordsPercentage` null), still saves; it just doesn't mark anything done.
- **This is the whole goal-hours rollup**, deliberately: nothing new was added to the
  intelligence service. A reading note's `daily_log` carries no `measuredMinutes` — a written
  reflection has no duration to report, and inventing one (a flat "15 minutes per note",
  say) would be a fabricated number, not a measured one, the same reasoning `GitHubSyncService`
  already applies to commit-count logs. `/tally`'s existing `goal_week_hours` only accumulates
  `measuredMinutes`, so reading notes don't add hours to that chart; they do count toward
  `daysShownUp` and `routineShownUp`, which only need the log to exist. If the "Reading"
  routine item is linked to a goal (set through Flock, already possible), a qualifying note
  marks that goal's day shown up, the correct shape for a qualitative activity.

Only the Reading frontend page itself remains unbuilt for Phase 7.

**Phase 8 (the hillside) started: accounts groundwork built.** Explicitly gated on an
accounts/auth architecture decision before any code (PROJECT.md); three real decisions were
asked and confirmed with the user before building, not guessed:

1. **The hillside compares users within one shared deployment**, not separate opt-in
   reporting between independent single-user instances. This is a much bigger change than
   the rest of this app: every existing table will eventually need a `user_id` scope, which
   is **not done yet** — see below for exactly what this slice does and doesn't touch.
2. **Both password and GitHub OAuth** login are supported.
3. **Next.js owns the actual login session**, not this backend. The backend never renders a
   login page or handles an OAuth redirect; it verifies a short-lived signed token Next.js's
   server attaches to its own calls, and separately verifies credentials when asked to.

**What's built:** `User` (`com.routinemachine.core.domain.User`), `users` table: email
(unique), an optional bcrypt password hash, an optional GitHub id (unique), a display name.
A user needs at least one login method (a DB check constraint, same defense-in-depth pattern
`DailyLog`'s exactly-one-target rule uses). `AuthService` coordinates the login logic:

- `POST /api/auth/register` — email + password (min 8 characters), 400 if the email's
  already registered.
- `POST /api/auth/login` — verifies a password against the stored bcrypt hash, 401 on any
  failure (unknown email, GitHub-only account with no password, wrong password) — never
  which part was wrong, so a failed guess can't be used to enumerate registered emails.
- `POST /api/auth/github-login` — finds or creates an account by GitHub id (linking to an
  existing password account with the same email on first GitHub login). **Trusts the caller
  already verified the GitHub identity** through a real OAuth flow; this endpoint itself
  proves nothing about GitHub, so it's protected by its own service-to-service token
  (`AUTH_INTERNAL_TOKEN`), fails closed if unset, same rule `HEALTH_INGEST_TOKEN` already
  follows. Only Next.js's own server should ever call it.
- `GET /api/users/me` — the one endpoint this phase actually requires a valid token for, not
  a real feature, proof the JWT verification wiring works end to end. Reads the token's
  `sub` claim as a user id and returns that user, including their `hillsideOptIn` flag.
- `PUT /api/users/me/hillside-opt-in` — `{"optIn": true|false}`, toggles the calling user's
  `hillsideOptIn` and returns the updated user. Requires the same valid token as `GET
  /api/users/me`. This is the real consent step the hillside was missing: see below.

Spring Security and `spring-boot-starter-oauth2-resource-server` are new dependencies. Two
separate `SecurityFilterChain` beans, not one — see the decisions log for why a single chain
broke `MeasurementIngestController` and `github-login`'s own `Authorization: Bearer` headers.
`AUTH_JWT_SECRET` (the secret both this backend and Next.js's future token-minting will
share) has no production default and the app won't start without it in a real deployment;
`docker-compose.yml` supplies an insecure development-only default so `docker compose up`
still works out of the box, since nothing in the app actually requires login yet.

**What's deliberately not touched:** `goals`, `books`, `reading_notes`, `measurements`, and
every other table stay exactly as they were — single-user, no `user_id` column. No endpoint
requires authentication except the one proof-of-concept above. Building the hillside's own
k-anonymous aggregation, the actual Next.js login UI and GitHub OAuth app registration, and
migrating existing dev data to a seed user are all separate, much larger pieces of work, not
started.

**Optional `user_id` added to `routine_items` and `daily_logs`** (migration V15), the two
tables that actually drive "days shown up" — the one signal needed to demo multiple users
having genuinely different data, without first inventing a goals-creation API or touching
every table in the schema. This is additive scoping, not enforced auth: `userId` is an
optional query param on `GET /api/routine-items`/`GET /api/daily-logs` and an optional field
on their `POST` bodies. Omitted, both list only the legacy, unowned rows (`NULL` `user_id`;
through Phase 8 they listed every account's) — the existing frontend and every Phase 6 sync service
(`WakaTimeSyncService`, `HubSyncService`, etc.) are untouched and keep working against that
same unscoped data, since none of them pass `userId`. Passed, the response is scoped to that
one user's own rows instead. `RoutineItem.assignUser`/`DailyLog.assignUser` are plain setters
used only by these two controllers (and seeding), not part of either entity's constructor, so
every existing call site across the sync services is unaffected.

**The hillside built: `GET /api/hillside`.** `HillsideService` computes bands fresh on every
call, nothing is stored. Query params: `field` (one of `HillsideField`'s names, omitted means
"general" — a day counts once 3+ distinct non-private routine items were tended, the same
`TALLY_STICK_THRESHOLD` `intelligence-service/tally.py` uses for its own tally stick, duplicated
here as `HillsideService.GENERAL_THRESHOLD` since the two live in different languages and can't
share a constant), `windowDays` (7 or 28), and `asUserId` — **a demo-mode parameter, not a real
logged-in identity**, since the Next.js session this would normally come from isn't built yet.
Omit it and the endpoint still returns the real histogram and participant count, just no
personal band/rank/neighbours.

- Reads every non-private (`routine_items.private = false`), user-owned `daily_log` in the
  window, from a user who has opted in (`users.hillside_opt_in = true`)
  (`DailyLogRepository.findForHillside`) and groups it by user and day in Java, not SQL — the
  dataset this phase operates on is small enough that a clear, testable Java loop beat a
  harder-to-verify aggregate query. `hillside_opt_in` defaults `false` (migration V17): a user
  with non-private routine items is not a participant until they explicitly opt in through
  `PUT /api/users/me/hillside-opt-in`. This closes the "no real opt-in concept" gap flagged
  when the hillside was first built.
- Ranks participants by days-shown-up (ties broken by user id, for determinism), computes a
  percentile (`1 - rank/total`), and buckets it into the same three band descriptions the
  design reference uses ("Near the front" / "In the middle" / "Walking at the back, and still
  walking").
- `HillsidePseudonym.forUserId` derives a two-word-plus-number pseudonym deterministically
  from the user id and the current "season" (a 90-day block of the epoch, folded into the
  hash input) — never the email or display name — so the same person gets the same pseudonym
  for roughly a quarter, then automatically a different one, with nothing stored anywhere.
- "Walking near you" is three ranked entries above and below, clamped at the edges of the
  list, not a fixed seven; `isYou` marks exactly one row when `asUserId` matches. Each
  `NeighborRow` also carries `rank`, its 1-indexed position in the *full* ranked list (not
  its position within this slice of neighbours) — the design reference shows a rank number
  per row, which this field exists to support.

**Phase 9: the evidence-to-credit pipeline built, `EvidenceCreditService`.** Implements
`docs/product-direction.md`'s pipeline (ingest, validate, normalise, deduplicate, qualify;
aggregate/publish live in `HillsideV2Service`). `ingest(user, goal, source, sourceEventId,
occurredAt, minutes)`:

- **Dedupe** (step 4): looks up `(userId, source, sourceEventId)` first; an exact repeat
  returns the existing `EvidenceEvent` untouched. A true race on the exact same event is still
  safe — `evidence_events`' own `UNIQUE(user_id, source, source_event_id)` constraint catches
  it, and a caught `DataIntegrityViolationException` falls back to re-reading the winner's row
  rather than failing the request.
- **Local date**: computed once, at ingest time, from `occurredAt` (UTC) and the user's
  `time_zone` (migration V18) — stored on the event, never recomputed later, so a user
  changing time zone doesn't silently rewrite which day a past credit landed on.
- **Qualify**: `MANUAL` source is always `SELF_REPORTED` (self-reports count, but stay
  labelled). Otherwise looks up the goal's newest `ScoringRule`; no rule means presence-based
  `ACCEPTED` (see `database.md`'s decisions log on why this app's real goals mostly hit this
  path); a rule with `minMinutes == null` means presence-based `ACCEPTED` too; a rule with a
  minute threshold and no `minutes` on the event means `PENDING` (data not confirmed yet, e.g.
  a sync still catching up); at or above the threshold is `ACCEPTED`, below is `REJECTED`
  ("still in your tally" — rejected evidence is kept, not discarded, so the ledger can show
  why).
- **Aggregate**: an `ACCEPTED`/`SELF_REPORTED` event upserts into `daily_credits` via
  `DailyCreditRepository.awardCreditIfAbsent`, a native `INSERT ... ON CONFLICT
  (user_id, goal_id, local_date) DO NOTHING`. This is the entire concurrency-safety mechanism
  for "one credit per qualifying goal per day, extra evidence never multiplies it, two
  concurrent inserts produce one row" — the database's own constraint decides, not an
  application-level check-then-insert that a race could slip past.

**Season aggregation built, `HillsideV2Service`.** `seasonScore(userId, start, end, goalId)`
sums each day's `daily_credits` (`goalId == null` means every goal) capped at
`DAILY_CREDIT_CAP = 3` *before* summing — a day with 4 qualifying goals still only contributes
3, and a missed day contributes 0, never a penalty. `countReturns` finds every credited day in
the window whose immediately preceding calendar day (looking past the window's own edge) had
no credit, excluding a user's very first-ever credited day anywhere (a start, not a comeback).
`compute(goalId, seasonStart, seasonEnd, asUserId)` ranks every `hillside_opt_in = true` user
with a nonzero season score (ties broken by user id), returns your rank/score/band/goal count,
three-above-three-below neighbours (reusing `HillsidePseudonym`, now keyed off the season's
own start date for its "season" input), and `cohortReturns` — the count of opted-in users with
at least one return this window, shown as a cohort total per the design, never a per-person
rank.

**The hillside v2 API built: `GET /api/hillside/v2`, `POST`/`GET /api/evidence-events`,
`PATCH /api/evidence-events/{id}/question`.** `seasonStart` defaults to the most recent Monday
in UTC when omitted (a stand-in for "the cohort's time zone," see the decisions log); `seasonEnd`
is always `seasonStart + 6 days`. The ledger (`GET /api/evidence-events?userId=&seasonStart=&seasonEnd=`)
lists every event in the window whatever its status, newest first, for the "How your credits
were counted" table. "Question a credit" just stamps `questioned_at`, matching the explicit
"simple form that records the request" scope — no review workflow yet.

**Phase 10: profile data built.** `users` gains (migration V21) `interests`/`thinkers`/
`learning_preferences` (JSON arrays in a TEXT column via `StringListConverter`, same pattern
`reading_notes` already uses), `currency` (varchar(8)), `day_end_hour` (0-23, DB check
constraint, default 0), and `analogies_enabled` (default true). `User.updateProfile(...)` is a
single partial-update method — each parameter only changes the field if non-null, so
`PUT /api/users/me/profile` can update just one field without the caller resending everything.
New endpoints `GET`/`PUT /api/users/me/profile`, JWT-protected via the same `/api/users/**`
chain `GET /api/users/me` already uses, deliberately **not** folded into `UserResponse`/
`GET /api/users/me` itself — see the decisions log on why this is its own response type.

`day_end_hour` now feeds `EvidenceCreditService`'s local-date rule directly: an evidence event
whose local hour (in the user's `time_zone`) is before `dayEndHour` is credited to the
*previous* calendar day, not the day it was actually logged on — so a 1am study session with a
3am day-end hour still counts as "today." Defaults to 0 (midnight), which is mathematically a
no-op (`hour < 0` is never true), so every existing Phase 9 test and every user who hasn't set
this is completely unaffected.

Privacy is enforced structurally, not just by convention: `HillsideV2Response`/
`EvidenceEventResponse`/`NeighborCreditRowResponse` have no path to a `User`'s profile fields
at all — they're built from `HillsideV2Result`/`NeighborCreditRow`/`EvidenceEvent`, none of
which carry profile data. `HillsideV2ApiIntegrationTest.profileDataNeverAppearsInTheHillsideResponse`
proves this against the real serialized JSON, not just by reasoning about the types, per
`docs/product-direction.md`'s explicit "write a test that proves it" instruction.

**Phase 11: the granary built.** `GranaryService` + two new tables (migration V22,
`savings`/`pause_decisions`) and `users.saving_target_amount`. Never holds or moves money —
every write only records that a saving or a decision happened.

- **Savings**: `storeSaving(user, today)` is idempotent via `savings`' own
  `UNIQUE(user_id, local_date)` plus a find-first check — tapping twice the same day is one
  saving, not two. `monthSummary(user, month, today)` computes `storedThisMonth`,
  `targetThisMonth` (the daily amount times days *elapsed* so far this month, not the whole
  month — a target on day 3 of October isn't already "31 days behind"), `daysStored`, the
  per-day `jar` array (one boolean per elapsed day), and `totalStoredEver` (every saving,
  any month).
- **The pause**: `PauseDecision` sets both `followUp24hAt` and `followUp30dAt` at creation
  (`now + 24h`, `now + 30d`). `outcome` stays null until `decidePause` is called — from the
  24-hour follow-up or immediately ("I've already decided"); `isDueForTwentyFourHourFollowUp`
  only fires while `outcome` is still null. `reflectionThirtyDay` is separate and independent:
  `isDueForThirtyDayFollowUp` fires regardless of what `outcome` ended up being, since the
  30-day look-back is about how the decision feels in hindsight, not a second chance to
  change it.
- **New endpoints**: `POST /api/granary/savings`, `PUT /api/granary/saving-target-amount`,
  `GET /api/granary/summary`, `POST /api/granary/pauses`, `GET /api/granary/pauses`,
  `GET /api/granary/pauses/due` (what Today prompts about), `PATCH .../decide`,
  `PATCH .../reflect`. None are JWT-protected (same `permitAll` pattern `/api/evidence-events`
  already uses) — the frontend resolves `userId` from the verified session itself before
  calling any of them, never from client-submitted form data.
- **Privacy**: same structural guarantee as Phase 10's profile data — no hillside/credit
  response type has a path to `savings`/`pause_decisions` at all.
  `HillsideV2ApiIntegrationTest.granaryDataNeverAppearsInTheHillsideResponse` proves it against
  the real serialized JSON, the explicit "test it" requirement.
- **A new `IllegalStateException` → 400 handler** was added to `GlobalExceptionHandler`:
  `storeSaving` throws it when no `savingTargetAmount` is set yet, a real client-fixable
  precondition failure ("set an amount first"), not a server bug that should 500.

**M-Pesa statement import built.** Proposed to the user before building (password-protected
PDFs, a statement format that can change without notice) and confirmed before any of this was
written.

- **The file is never persisted.** `GranaryController.importMpesaStatement` reads the
  uploaded `MultipartFile`'s bytes straight into memory (`file.getBytes()`) and passes them to
  `GranaryService.importMpesaStatement`, which only ever holds them in a local `byte[]`
  variable scoped to that one call. Nothing writes them to disk, a temp directory, or any
  table — "delete the file after parsing" is true by construction, since the file is never
  saved in the first place, not a delete step that could be skipped or fail.
- **Password-protected PDFs**: `MpesaPdfReader.extractText(bytes, password)` tries the given
  password (or none) via PDFBox's own decryption (`Loader.loadPDF(bytes, password)`). A wrong
  or missing password on an encrypted PDF throws `MpesaStatementPasswordRequiredException`,
  mapped to its own `400` with `"type": "PASSWORD_REQUIRED"` — distinct from a generic parse
  failure, so the frontend can prompt for a password specifically rather than showing
  "couldn't read this statement."
- **Format changes, fail loudly**: `MpesaStatementParser.parse` matches each transaction row
  against a single regex (receipt id, completion date/time, free-text details, a status word,
  three decimal amounts) and categorises each one by keyword-matching the details text against
  a fixed map (`MpesaCategory`: FOOD, TRANSPORT, FAMILY_SUPPORT, DATA_AND_AIRTIME, LEARNING,
  BETTING, OVERDRAFT_FEES, OTHER — matching the design reference's own category list, with
  OTHER as the honest catch-all for anything unmatched). If the text has *zero* recognisable
  transaction rows at all, parsing throws `MpesaStatementParseException` ("the statement's
  format may have changed") rather than returning empty or silently wrong totals — the real
  M-Pesa statement text layout hasn't been verified against a real statement (see the open
  questions), so a confident wrong answer would be worse than a clear failure.
- **Only Withdrawn amounts count.** Paid In amounts (money arriving) are read but never
  summed into a category total — these totals answer "where did spending go," matching the
  design reference's "Where it went" framing, not "where did money come from."
- **New table** (migration V23): `mpesa_category_totals(user_id, statement_month, category,
  amount)`, unique on `(user_id, statement_month, category)`. Re-importing the same month's
  statement replaces that month's totals via a native `ON CONFLICT ... DO UPDATE` upsert, not
  a second set of rows — "only category totals are kept," and a corrected or re-uploaded
  statement should overwrite, not accumulate alongside the old one.
- **New endpoints**: `POST /api/granary/mpesa-statement` (multipart: `userId`, `file`,
  optional `password`), `GET /api/granary/mpesa-categories` (defaults to last month).
- **Tested against a synthetic fixture only**, per the task's explicit instruction, never a
  real statement: `MpesaStatementParserTest` covers the parser as a pure function against
  plain text in the documented row shape (summing by category, Paid In never counting, OTHER
  as the fallback, each named category's keywords, the "no rows at all" failure case).
  `MpesaPdfReaderTest` builds real PDF fixtures with PDFBox itself (unprotected, and
  password-protected with a known password) to prove the actual text-extraction and
  decryption path, not just the parser's string handling. `GranaryServiceTest` and
  `GranaryApiIntegrationTest` cover the full pipeline end to end, including re-import
  replacing totals and the password-required path.

**Phase 12 part 1: organisations and cohorts, data model.** `OrganisationService` +
three new tables (migration V24: `organisations`, `organisation_admins`, `cohorts`,
`cohort_memberships`).

- **Admin is organisation-scoped, not cohort-scoped.** `organisation_admins` is a separate
  table from `cohort_memberships` on purpose: the design reference's admin view oversees
  multiple cohorts ("Cohort 14", "Cohort 13") under one organisation header, while a learner's
  `cohort_memberships` row is a seat in exactly one cohort. There's no "cohort admin" role —
  only an organisation admin, checked against every cohort under that organisation.
- **Authorization is a service-layer check, not a JWT-protected endpoint.** `/api/organisations/**`
  stays `permitAll`, same pattern as `/api/granary/**` — every write takes a
  `requestingUserId` and `OrganisationService` throws `NotAnOrganisationAdminException`
  (→ `403`, a new `GlobalExceptionHandler` entry) if that user isn't an admin of the relevant
  organisation. `createCohort`/`addCohortMember`/`listCohortMembers` all check this before
  doing anything else.
- **Adding the same learner to a cohort twice is a no-op, not a duplicate.**
  `addCohortMember` checks `findByCohortIdAndUserId` first — same idempotent-write shape as
  `GranaryService.storeSaving` for a day that's already been logged.
- **New endpoints**: `POST /api/organisations` (creates an org and makes the given user its
  founding admin in one call), `POST /api/organisations/{id}/cohorts`,
  `POST /api/organisations/cohorts/{id}/members`, `GET /api/organisations/cohorts/{id}/members`.
- Tested with `OrganisationServiceTest` (service-layer admin checks, idempotent membership)
  and `OrganisationApiIntegrationTest` (the full create-org → create-cohort → add-member →
  list-members flow end to end, plus the 403 path).

**Phase 12 part 2: bulk CSV invites.** Proposed to the user before building (there's no
outbound email infrastructure in this app, and the spec doesn't say what a "seat" caps or
what happens for an email with no account yet) and confirmed before any of this was written.
Migration V25: `organisations.seat_limit` (nullable — null means unlimited) and a new
`cohort_invites` table.

- **"Invite" means reserving a seat, not sending mail.** `OrganisationService.bulkInviteByCsv`
  parses the CSV (one email per line, or email-first-column; a literal `email` header line
  and blank lines are skipped) and, per row: an email with an existing `User` is added to the
  cohort immediately (`ADDED_IMMEDIATELY`); an unknown email gets a `CohortInvite` reservation
  (`INVITE_RESERVED`). No actual email is sent to anyone — notifying the invitee is a future
  integration, not built now.
- **Invites are fulfilled on registration, not polled for.** `AuthController.register` and
  `.githubLogin` both call `OrganisationService.fulfillInvitesForNewUser(user)` right after
  the account is created — every pending `CohortInvite` matching that email (case-insensitive,
  same as `UserRepository.findByEmailIgnoreCase`) becomes a real `CohortMembership` and the
  invite row is deleted. Called unconditionally on both paths since it's a safe no-op for a
  user with no pending invites, rather than threading a "was this a brand-new account" flag
  through `AuthService`.
- **Every CSV row gets a reported outcome — `ADDED_IMMEDIATELY`, `INVITE_RESERVED`,
  `ALREADY_A_MEMBER`, `ALREADY_INVITED`, `SEAT_LIMIT_REACHED`, or `INVALID_EMAIL`.** Nothing
  is silently dropped from the response, same "fail loudly, never silently wrong" instinct as
  the M-Pesa statement parser — an admin pasting in a messy list can see exactly what happened
  to every row, not just a count.
- **Seats are counted across the whole organisation, not per cohort**, and a batch stops
  admitting new people once the limit is hit mid-batch (rows after the limit report
  `SEAT_LIMIT_REACHED`) rather than failing the whole CSV or silently over-admitting.
- **A real `IllegalStateException`-shaped bug caught by the full test suite, not the new
  tests themselves**: the first version of `setSeatLimit` mutated the `Organisation` entity
  without an explicit `organisationRepository.save(organisation)` call. It passed in
  `OrganisationServiceTest` (a `@DataJpaTest`'s single shared persistence context returns the
  same mutated in-memory object on a re-fetch) but failed in
  `OrganisationApiIntegrationTest` (each `MockMvc` request is its own transaction, so the
  mutation was silently lost) — caught by running the full suite before reporting, same
  "verify with a real build/system, not memory of one" rule that found Phase 2's lazy-loading
  bug. Fixed by adding the explicit `save`, matching `GranaryController.setSavingTargetAmount`'s
  existing precedent for the same shape of write.
- **New endpoints**: `PUT /api/organisations/{id}/seat-limit`,
  `POST /api/organisations/cohorts/{id}/bulk-invite` (JSON body with the raw CSV text, not a
  multipart file upload — a CSV of emails is small enough that the frontend can just read the
  file client-side and send its text).
- Tested with `OrganisationServiceTest` (every outcome, the seat-limit-mid-batch case, the
  non-admin rejection, invite-then-register-fulfills-it) and `OrganisationApiIntegrationTest`
  (the full bulk-invite → register → membership-appears flow end to end, and the seat-limit
  cap enforced over a real HTTP request).

**Phase 12 part 3: the cohort dashboard's four pilot measures.** Flagged to the user before
building — `docs/product-direction.md` references "the operating plan" for exact definitions
of these measures, but no such document exists in this repo, and the supportive/pressuring
survey had no capture mechanism at all — and the user chose concrete definitions before any
of this was written. New `CohortDashboardService`, migration V26 (`cohort_survey_responses`).

- **"Checked in" is any `DailyLog`, the same thing Today already tracks for every user — not
  the hillside's `daily_credits`.** The user's explicit choice: cohort participation
  shouldn't require opting into the hillside, which is a separate, unrelated concern (see
  `docs/database.md`'s decisions log on these two modules staying uncoupled).
- **Activation** = a member who created ≥1 `Goal` and logged ≥1 `DailyLog`, both dated within
  7 days of `cohort_memberships.joined_at` — matches the design reference's "set a goal and
  checked in once." Doesn't require the full 7-day window to have elapsed; a member who does
  both on day one is activated immediately.
- **Weekly participation** ("checked in this week") is computed out of activated members only,
  not the whole cohort, matching the design reference's own "of 34 activated" denominator —
  the current week is derived from `cohort.startsOn`, not a calendar week.
- **"Returned after a missed day"** is a first-cut definition, not the same algorithm as
  `HillsideV2Service.countReturns` (which counts every return across a season from the
  hillside credit pipeline): per activated member, only the *first* gap in their logged dates
  since joining is examined — did a full un-logged day occur, and did their next log come
  within 7 days of it. The denominator is members who had at least one such gap, not the
  whole cohort — matches the design reference's "11 of 14."
- **The survey** (`CohortSurveyResponse`, unique per `(cohort_id, user_id)`) lets any cohort
  member submit one of `SUPPORTIVE`/`NEUTRAL`/`PRESSURING`; resubmitting replaces the answer
  rather than adding a second row. The denominator for "felt supportive" is total responses
  received, not total members — matches the design reference's "19 of 23" with the
  neutral/pressuring counts shown separately, not folded into one percentage.
- **Viewing the dashboard requires being either an organisation admin or a member of that
  specific cohort** — "admins see aggregates only, learners can read the same page"
  (docs/product-direction.md) means learners get read access too, not just admins. A new
  `NotACohortParticipantException` (→ `403`) is distinct from `NotAnOrganisationAdminException`
  since the rule here is "admin OR member," not "admin only."
- **Every measure is a `{count, denominator}` pair, never a bare number or percentage** —
  "each figure with its denominator" is explicit in `docs/product-direction.md`.
- Tested with `CohortDashboardServiceTest` (every measure's numerator/denominator, the
  resubmit-replaces-the-answer case, both access-control branches) and
  `OrganisationApiIntegrationTest` (the full survey-submit → dashboard-reflects-it flow for a
  learner, and the 403 for a non-participant).

**Phase 12 part 4: study groups, the "by study group" breakdown, and small-groups-hidden.**
Migration V27: `cohort_memberships.study_group` and `cohort_invites.study_group`, both
nullable — a member with no group just doesn't appear in the breakdown.

- **A group is set via the CSV's optional second column** (`email,group`), not a separate
  endpoint — matches the design reference, where groups are a property of how someone was
  invited ("Morning group", "Evening group"). `CohortInvite` carries the group through to the
  `CohortMembership` it eventually becomes, whether the person was added immediately
  (existing account) or fulfilled later on registration.
- **`CohortDashboardService.summarizeByGroup`'s denominators are every member of the group,
  not just activated ones** — deliberately different from the cohort-wide measures above,
  which denominate by activated members only. This matches the design reference's own
  "12 of 14" where 14 is the group's whole headcount, not an activated subset.
- **"Small groups stay hidden"**: a group under 5 members returns its member count but
  `checkedInThisWeek`/`returnedAfterAMissedDay` are both `null` and `hidden` is `true` — the
  count itself is shown (so an admin can see the group exists and how big it is) but no rate
  is computed, so no individual in a near-1:1 denominator can be picked out.
- **New endpoint**: `GET /api/organisations/cohorts/{id}/dashboard/by-group`.
- Tested with `OrganisationServiceTest` (the group carried through both the immediate-add and
  invite-then-register paths), `CohortDashboardServiceTest` (a 5+ group's real rates, a
  under-5 group hidden but still counted, members with no group excluded entirely), and
  `OrganisationApiIntegrationTest` (the hidden-group response over a real HTTP request).

**Phase 12 part 5: the weekly check-in chart.** `CohortDashboardService.weeklyCheckIns`, one
bar per week of `Cohort.weeks` (the pilot's stated length), matching
`docs/design-reference/pages/cohort.html`'s "Weekly check-ins, eight-week pilot."

- **A single shared denominator across every bar** — `activatedDenominator`, the cohort's
  current activated-member count — not a denominator recomputed per week, matching the design
  reference's one caption ("of 34 activated") under the whole chart rather than per bar.
- **A week that hasn't started yet reports `null`, not `0`.** `checkedIn` only has a real
  count once the week has actually begun; a future week is a blank/dashed bar in the
  reference, not a bar claiming zero people checked in during a week that hasn't happened.
- **The current week is flagged (`isCurrentWeek`) separately from being future or past.**
  Matches the reference's highlighted "now" bar, distinct styling from both completed weeks
  and the dashed weeks ahead.
- **A real test-fragility bug found while writing this, fixed properly on 2026-10-21**:
  `CohortMembership.joinedAt` and `Goal.createdAt` are DB-generated at insert time (`now()`),
  not settable through the entities, but activation is measured against them. The first fix
  (anchor test dates to the real "today") only moved the problem: Stage 1's read-only pass
  found `checkedInThisWeekIsOutOfActivatedMembersOnly` failing two days after it was written.
  The tests now pin both timestamps to the date they are about with a native `UPDATE`
  (`pinTimestamp` in `CohortDashboardServiceTest`), and `activationDoesNotDependOnTheRealDate`
  runs the same scenario for cohorts dated 2031 and 2019.
- **New endpoint**: `GET /api/organisations/cohorts/{id}/dashboard/weekly-check-ins`.
- Tested with `CohortDashboardServiceTest` (the elapsed-weeks' real counts, future weeks
  reporting `null`, the current-week flag, the access-control check) and
  `OrganisationApiIntegrationTest` (the chart's shape over a real HTTP request).
- **With this, every backend piece of Phase 12's cohort dashboard is built. Only the frontend
  remains.**

**Frontend built, two real bugs found and fixed live before reporting.** `/cohort/[cohortId]`
(see `docs/frontend.md`) exercised two endpoints that had never been called by any frontend
code before:

- `setSeatLimit` and `submitSurveyResponse` both returned a plain `200` with an empty body
  (ordinary `void`-returning `@PutMapping`/`@PostMapping` methods) instead of `204`. Nothing
  caught this until the frontend's `request()` helper tried `response.json()` on an empty body
  and threw `"Unexpected end of JSON input"` — not just the survey form's write, any future
  caller of either endpoint would have hit the same thing. Fixed with
  `@ResponseStatus(HttpStatus.NO_CONTENT)` on both `OrganisationController` methods, matching
  how `request()` already special-cases `204`, rather than loosening the frontend's parsing.
  `OrganisationApiIntegrationTest`'s assertions updated from `isOk()` to `isNoContent()`.

**Per-user scoping, part 1: ownership data and the owner claim.** The app began as one
person's tool, so most personal data was stored with no owner (`user_id IS NULL`), and every
page showed it to every visitor, including a brand-new account. The fix is built in parts (the
rule: more than three parts, one at a time); this is the first and changes no behaviour a user
can see.

- **What it adds**: migration V28 (a nullable `user_id` on `schedule_templates`, `tasks`,
  `books`, `current_reading_log`; see `database.md`), the matching `user` field and `assignUser`
  on those four entities, and `OwnerClaimService` + `OwnerClaimRunner`.
- **The owner claim**: when the environment variable `OWNER_EMAIL` is set, the backend gives
  every row with a NULL `user_id` in `goals`, `routine_items`, `daily_logs`, `schedule_templates`,
  `tasks`, `books` and `current_reading_log` to that account at startup. The owner's address is
  never in code or config files (only the variable's name, in `application.yml` and
  `infra/docker-compose.yml`), and the log line reports counts per table, not the address.
- **Safe by construction**: only NULL rows move, so anything already owned stays put and a
  second run claims nothing. It is one transaction, all tables or none. An unknown email changes
  nothing and is logged as an error, not a crash, so the app still starts. With the variable
  unset the runner does nothing at all.
- **A known sharp edge**: `goals` is unique on `(user_id, name)`. If the owner already has a goal
  with the same name as an unowned one, that update fails and the whole claim rolls back (and is
  logged). It has not happened with the data seen so far; it would need a rename, not a silent
  merge.
- **Integration data is owner-only, by decision**: `measurements` (WakaTime, ActivityWatch,
  GitHub, RescueTime, health) and `hub_sync_processed_files` get no `user_id`. They come from
  credentials set for the whole deployment, so they belong to the deployment's owner; a second
  user has no integration of their own to read. Later parts make the Watering holes page and the
  intelligence service respect this.
- **Not done yet** (parts 2 to 6): no query filters on the new columns, so every page still
  shows the same data to everyone. Backend scoping, the frontend empty start, the intelligence
  service, owner-only integrations and cross-user tests follow. The backend still trusts a user
  id sent in the request on several routes; closing that is the access-control stage.
- Tested with `OwnerClaimServiceTest` (every table claimed, someone else's rows untouched, second
  run a no-op, email match ignoring case, unknown email changes nothing) and
  `OwnerClaimRunnerTest` (nothing without the variable, the configured address, a wrong address
  not fatal). Checked that the tests can fail by dropping a table from the claim list: two failed.

**Per-user scoping, part 2: the backend answers for exactly one user.** Built in three slices
(2a tasks and schedule templates, 2b reading and learning topics, 2c goals, routine items and
daily logs). The rule on every personal endpoint:

- **`userId` given**: exactly that user's rows. Creates are owned by that user.
- **`userId` omitted**: only *unowned* (pre-account) rows, never a real user's. This tightens the
  Phase 8 behaviour, where omitted meant "everything", so the userId-less view can no longer
  leak another account. It stays only so the app works until part 3 moves every page onto the
  session user; the access-control stage then removes it.
- **A row can only link to rows with the same owner.** A task, template or topic that points at
  someone else's routine item, template or goal is rejected with the same 400 an unknown id
  gets, so a foreign id reveals nothing.
- **Changing a row you do not own answers 404**, exactly like one that does not exist.
- **Shared helper**: `service/Ownership` (`idOf`, `ownedBy`, `sameOwner`). `idOf` reads the id off
  a lazy proxy without loading it, which matters because `open-in-view` is off.
- **2a (done)**: `GET /api/tasks`, `GET /api/schedule-templates` take `userId`; `POST` on both
  takes `userId` in the body; `PATCH /api/tasks/{id}` takes `userId` as a query parameter.
  `TaskResponse` and `ScheduleTemplateResponse` now carry `userId`. Marking a task done creates a
  daily log owned by the task's user (it used to create an unowned one, which would have put a
  new user's day into nobody's, then the owner's, history). New derived queries use
  `@EntityGraph` for the same lazy-loading reason as the older fetch-join queries, which stay
  because repository tests and hub-sync use them.
  Tested by `ScopedTasksAndTemplatesApiIntegrationTest` (10 tests), checked by mutation:
  removing the ownership filter from `TaskService.changeStatus` and the link check from
  `TaskController` made two of them fail.
- **2b (done)**: `GET /api/books`, `/api/books/current`, `/api/current-reading`,
  `/api/reading-notes` and `/api/learning-topics` take `userId`; `POST` on books, notes, current
  reading and topics takes it in the body. A note belongs to whoever owns its book and a topic to
  whoever owns its goal, so neither table got its own column. Writing a note on someone else's
  book, or adding a topic under someone else's goal, is rejected with the unknown-id 400. A note
  of 70% own words or more marks only the *author's* reader-notes routine item done and logs the
  day for the author; it used to mark every account's. `BookResponse` carries `userId`.
  `BookRepository.findCurrent()` and `CurrentReadingLogRepository.findLatest()` stay global on
  purpose: hub-sync still uses the first, and moving hub-sync onto the owner account is part 5.
  Tested by `ScopedReadingAndTopicsApiIntegrationTest` (11 tests), checked by mutation: removing
  the book-ownership filter and the goal-ownership filter made two of them fail.
- **2c (done)**: `GET /api/goals`, `/api/routine-items` and `/api/daily-logs` with no `userId`
  list only unowned rows (they listed every account's before). A routine item cannot be created
  under someone else's goal, and a day cannot be logged against someone else's routine item or
  learning topic; each is rejected with the unknown-id 400. Logging a day now always assigns the
  day to the request's user (it was the same in effect, now through one code path).
  Tested by `ScopedGoalsItemsAndLogsApiIntegrationTest` (8 tests), checked by mutation (restoring
  the old goal list and dropping the daily-log link check made two fail). One Phase 8 test,
  `UserScopedDataApiIntegrationTest.dailyLogsCreatedForOneUserAreNotVisibleWhenListingAnother`,
  logged two accounts against one shared unowned routine item, which is exactly the cross-account
  link the new rule rejects; it now gives each user their own item and still proves the isolation.
- **Part 3 (frontend) is done** (see `frontend.md`): every personal page and Server Action now
  sends the session user's id, so the userId-less view is no longer used by the app. It remains
  in the API only until the access-control stage replaces `userId` with the signed token. The one
  consumer still reading unowned rows is the intelligence service (part 4).
- **Part 5 (integrations are the owner's) is done.** The integrations run on credentials set for
  the whole deployment, so what they pull in belongs to the deployment's owner. New
  `service/OwnerAccount` resolves the owner from `OWNER_EMAIL` (the same variable the owner claim
  uses): `owner()`, `isOwner(id)`, `isIntegrationTarget(itemOwner)`. With no owner configured, or
  no account registered with that email yet, there is no owner and integrations write only to
  unowned rows, which is what they have always written to.
  - **WakaTime, ActivityWatch, GitHub, RescueTime** now log only into routine items owned by the
    owner or by nobody, and the log belongs to the item's owner. They used to log into *every*
    routine item with their source whoever owned it (any account could create an item with source
    `WAKATIME` and receive the owner's coding hours), and the log they created belonged to nobody,
    so after the owner claim a new synced day would not have shown on the owner's pages.
  - **Hub-sync** looked up the "journaling" and "writing" items by name across every account, so a
    new user's "journaling" item could receive the owner's journal entries. It now picks the
    owner's item (or an unowned one), assigns the log to the item's owner, and keeps the
    hub-synced current book on the owner's side: the latest of the owner's books and the unowned
    ones, never another user's, so a new user adding a book no longer makes it write a duplicate.
  - **`GET /api/watering-holes?userId=`**: the owner (and the userId-less legacy view) get the real
    status; any other account gets every source "not connected" with no activity, which is true
    for it.
  - **Measurements and hub-file records stay global.** Only the Watering holes status page reads
    them. Health ingest is unchanged (token-protected, writes measurements only).
  - Tested by `OwnerAccountTest` (4), `IntegrationOwnershipTest` (6, covering all four sync
    services), four new `HubSyncServiceTest` cases and `WateringHolesOwnerOnlyApiTest` (3); three
    deliberate breaks (WakaTime writing to every item, hub-sync ignoring ownership, Watering
    holes ignoring the user) each made tests fail. The existing sync tests construct the services
    with no owner configured, so they still prove the pre-account behaviour.
- **A build trap found on the way**: with VS Code's Java language server running, an edit that
  does not compile can leave Eclipse-compiled `Unresolved compilation problem` stubs in
  `target/classes`, which Maven then treats as up to date. `./mvnw -q compile` printed nothing
  and the failure only appeared at runtime. Use `./mvnw clean test` (or `clean test-compile`)
  when sources changed while the IDE is open.

**Per-user scoping, part 6: id-only routes closed, and the route map.** Writing the
end-to-end test found four holes the per-route tests had missed:
- `PATCH /api/granary/pauses/{id}/decide` and `/reflect` and `PATCH /api/evidence-events/{id}/question`
  took only a row id, so any caller could change any account's pause or question its credit.
  They now require `userId`. Someone else's row answers 404, like one that does not exist, and a
  missing `userId` answers 400.
- `POST /api/evidence-events` accepted another account's goal. A goal must now have the same
  owner as the user (400 otherwise), so an unowned founder goal cannot receive a new user's evidence.

Who sees what, by route group (all rules also hold for creates: the new row belongs to `userId`):

| Routes | `userId` given | `userId` omitted | Someone else's row |
|---|---|---|---|
| `/api/goals`, `/routine-items`, `/learning-topics`, `/schedule-templates`, `/tasks`, `/daily-logs` (**token**, access control part 2) | The token's user only; a `userId` in the request is not read | 401 | Linking to it is 400; changing a task is 404 |
| `/api/books`, `/reading-notes`, `/current-reading`, `/books/current` (**token**, access control part 3) | The token's user only | 401 | A note on another user's book is 400 |
| `/api/granary/summary`, `/pauses`, `/pauses/due`, `/savings`, `/saving-target-amount` (**token**, access control part 1) | The token's user only | 401 | Not reachable |
| `/api/granary/pauses/{id}/decide`, `/reflect` (**token**) | Must own the row | 401 | 404 |
| `/api/evidence-events` (ingest, ledger) and `/{id}/question` (**token**) | The token's user; a goal must be the user's own | 401 | Question: 404; goal: 400 |
| `/api/watering-holes` (**token**) | Real status for the owner, all sources off for anyone else (and for everyone when no owner is configured) | 401 | Not applicable |
| `/api/organisations/**` (**token**, access control part 4) | The token's user must hold the right the route needs (founding admin, or cohort member for the dashboard and survey) | 401 | 403 |

`CrossUserIsolationApiIntegrationTest` (17 tests) creates data for two accounts on every personal
route, reads each list as A, as B and as nobody, and tries each cross-account write. It fails if a
route is added or changed without following the rules above. A deliberate break (the pause
ownership filter removed) made it fail.

**Access control, part 1: the Granary takes its user from the token.** The stage is split into
five parts (the Granary first, then personal data, books and credits, cohorts and the owner role,
and finally the intelligence and sync services plus a 401 sweep).
- `/api/granary/**` joins `/api/users/**` in the JWT filter chain (`SecurityConfig`). No token, or a
  bad one, is 401.
- The user is `TokenUser.idOf(jwt)`, the token's subject. Granary routes no longer have a `userId`
  query parameter, body field or form field (`StoreSavingRequest` is gone; `CreatePauseRequest` and
  `SetSavingTargetAmountRequest` lost the field). A `userId` sent anyway is not read.
- Someone else's pause is 404, as before, now judged against the token's user.
- The frontend's `backendFetch` and `backendFetchMultipart` send the session token as a bearer
  header on every call when a session exists. A caller's own `Authorization` header wins, so the
  login, GitHub and health calls are unchanged.
- Tests: `GranaryTokenApiIntegrationTest` (5); the other Granary tests and
  `CrossUserIsolationApiIntegrationTest` now sign in with `TestTokens.asUser(id)`. Removing
  `/api/granary/**` from the protected chain makes the new test fail.
- **Rollback**: revert the commit. No migration, no data change.

**Access control, part 2: personal data takes its user from the token.** Goals, routine items,
learning topics, daily logs, tasks and schedule templates join the JWT filter chain. No token is 401.
Their `userId` query parameter and request-body field are gone, and one sent anyway is ignored.
- The "no `userId` shows only unowned rows" rule is gone for these routes: nobody can list unowned
  rows through the API any more. They still exist (the seeded goals, and anything the owner claim
  has not moved), and the sync services still reach them directly, not over HTTP.
- `POST /api/goals` and the others require a user that exists: a token whose user is gone is 400.
- The intelligence service reads these routes, so it now forwards the caller's `Authorization`
  header (see `docs/intelligence-service.md`). The frontend sends the session token to it.
- Tests: `PersonalDataTokenApiIntegrationTest` (4). Eight tests for the removed "unowned view"
  rule were deleted; the rest of the older tests now sign in with `TestTokens.asUser`. Removing
  `/api/tasks/**` and `/api/goals/**` from the protected chain made the new test error out.
- **Rollback**: revert the commit. No migration, no data change.

**Access control, part 3: reading, Watering holes and credits take their user from the token.**
Books, reading notes, current reading, `GET /api/watering-holes` and the evidence routes (ingest,
ledger, question) join the JWT filter chain. No token is 401, and a `userId` in the request is not
read (`BookRequest`, `ReadingNoteRequest`, `CurrentReadingRequest` and `EvidenceEventRequest` lost
the field).
- Watering holes: the owner (from `OWNER_EMAIL`) sees the real status; every other account sees all
  sources off. With no owner configured nobody is the owner, so everyone sees all sources off. The
  old no-`userId` view, which showed the real status to anyone, is gone.
- Tests: `ReadingAndCreditsTokenApiIntegrationTest` (5). The "unowned books" test for the removed
  view rule was deleted. Removing `/api/books/**` and `/api/evidence-events/**` from the protected
  chain made the new test error out.
- **Rollback**: revert the commit. No migration, no data change.

**Access control, part 4: cohort routes and the owner role.**
- `/api/organisations/**` joins the JWT filter chain. No token is 401. The founding admin of a new
  organisation is the token's user; `requestingUserId` and `foundingAdminUserId` are gone from every
  request, and one sent anyway is ignored. Phase 12's rights are unchanged (admins read aggregates and
  manage the cohort, members read the dashboard and answer the survey, outsiders get 403), now judged
  against the token. Anyone signed in can still create an organisation, as before.
- **Owner role** (migration `V29__user_role.sql`, `User.role`, `UserRole`, `OwnerRoleService`).
  Every account is `USER`. At startup `OwnerClaimRunner` calls `OwnerRoleService`, which gives
  `OWNER` to the account whose email is in `OWNER_EMAIL` and takes it from any other account, so there
  is exactly one owner. It runs separately from the data claim, so a failed claim cannot leave the
  owner without the role. The role column is read-only for the application (`updatable = false`); only
  that startup update changes it. No address is in code, SQL or logs. Nothing reads the role yet:
  the operations page and API docs (staged Prompts 3 and 4) will check it on the server.
- Tests: `OrganisationTokenApiIntegrationTest` (4), `OwnerRoleServiceTest` (5), two new
  `OwnerClaimRunnerTest` cases. Unprotecting the organisation routes and removing the demotion step each
  made a test fail.
- **Rollback**: revert the commit. `V29` only adds a column with a default; it can stay.

Every route group in the table above is now on the token except the intelligence service's `userId`
parameter (part 5).

**Still open**: outside the Granary, the user id is claimed by the caller, not proven by a token. Anyone who can reach
the backend can send any `userId`. That is the access-control stage.

## Decisions log

| Date | Decision | Reason |
|------|----------|--------|
| 2026-10-21 | The owner account is defined once (`OwnerAccount`, from `OWNER_EMAIL`) and used by the claim, every integration and the Watering holes status | The same address answers "whose data does the deployment's own credentials produce" everywhere; three separate readings of the variable would let the claim and the integrations disagree about who the owner is |
| 2026-10-21 | An integration may write to the owner's items and to unowned items, nobody else's | Unowned items are the pre-account data the integrations have always written to, so they must keep working before the owner claim runs; the owner's items are what that data becomes after it. A rule of "owner's items only" would stop every integration in the gap between deploying this and running the claim |
| 2026-10-21 | Another account sees every watering hole as "not connected", not an error or a hidden page | It is true: that account has no integration of its own. Per-user integrations (each person's own WakaTime key) would be a separate feature |
| 2026-10-21 | A request with no `userId` sees only unowned rows, instead of everything | "Omitted means everything" was the Phase 8 compromise to keep the single-user app working, but it means the userId-less view shows every account's data. Unowned rows are the founder's pre-account data, which the owner claim hands to the owner account, so after the claim the userId-less view is empty. It is kept only until part 3 moves the frontend onto the session user |
| 2026-10-21 | Someone else's task, template or routine item answers exactly like one that does not exist (404 or the same 400) | A different answer for "exists but is not yours" would let anyone probe which ids exist and how many rows other accounts have |
| 2026-10-21 | Pre-account data is given to the owner by a startup claim driven by `OWNER_EMAIL`, not by a migration | A migration cannot know whose account is the founder's, and putting an address in a migration or a config file would publish it. The variable is read once at startup, only NULL rows move, and it is idempotent, so leaving it set is harmless |
| 2026-10-21 | The owner claim is all-or-nothing and a failed claim is logged, not fatal | A half-claimed database (some tables owned, some not) is worse than an unclaimed one, and a typo in an environment variable should not stop the app from starting |
| 2026-10-21 | `measurements` and `hub_sync_processed_files` are not given a `user_id` | They hold what the deployment's own integrations pull in (one set of credentials for the whole deployment). Giving each user a copy of the owner's WakaTime hours would be wrong, and per-user integrations are a separate feature |
| 2026-10-19 | M-Pesa statement parsing uses one regex matching the whole transaction-row shape, not a general-purpose PDF table extractor | PDFBox's basic `PDFTextStripper` doesn't preserve table column boundaries reliably; a row-shape regex anchored on the structurally distinctive parts (a receipt id, an ISO-ish date/time, a status word, three trailing decimal amounts) degrades to "no match, fail loudly" when the layout differs, rather than silently misreading columns — matching the proposed "fail loudly on format changes" approach |
| 2026-10-19 | Category classification is a fixed keyword-to-category map with an OTHER fallback, not a model or a configurable rule set | Matches `tally.py`'s own precedent for simple, stated rules over fitted parameters (Phase 2's skip-heavy threshold); a spending category guess from free text is inherently approximate, and OTHER makes that honest instead of forcing every transaction into a category it doesn't really belong to |
| 2026-10-19 | The PDF's bytes are read into a plain `byte[]` and never touch a file on disk, rather than "save then delete" | A save-then-delete step is one more place a bug, a crash between the two steps, or a forgotten cleanup path could leave the file behind; never writing it in the first place removes that failure mode entirely instead of relying on a delete call always running |
| 2026-10-19 | `MpesaStatementPasswordRequiredException` is a distinct exception/response type from `MpesaStatementParseException`, both separately caught in `GlobalExceptionHandler` | The two failures need different frontend behaviour (prompt for a password vs. show "couldn't read this, format may have changed") — collapsing them into one generic 400 would make that distinction only inferable from message text, which is fragile compared to a `type` field |
| 2026-10-19 | Re-importing the same statement month overwrites that month's category totals (an upsert), not a new set of rows alongside the old one | A user re-uploading a statement (a correction, or the same one twice by accident) should get one true set of totals for that month, not two conflicting sets the page would have to somehow reconcile or pick between |
| 2026-10-19 | Phase 12 built as data-model groundwork only this slice — no CSV invites, no dashboard metrics, no frontend | The full feature (bulk CSV invites, four pilot measures with denominators, weekly check-in history, small-groups-hidden, a frontend admin/learner dashboard) is more than three parts; per the standing rule, only the first (the data model and the admin/learner access rule everything else depends on) is built now |
| 2026-10-19 | Admin status lives on `organisation_admins`, scoped to the organisation, not to a cohort | The design reference's admin view spans multiple cohorts under one organisation header ("Cohort 14", "Cohort 13" both under the same admin nav); a per-cohort admin role would need to be granted separately for each new cohort an admin creates, which doesn't match "run a programme," not "run one cohort" |
| 2026-10-19 | "Invite" means reserving a cohort seat via `CohortInvite`, never sending an actual email | Flagged to the user and confirmed before building, same as Phase 11's M-Pesa handling: this app has no outbound email infrastructure at all. Fabricating a "sent" state for mail that was never sent would be worse than being honest that notifying the invitee is a future integration |
| 2026-10-19 | A CSV bulk invite reports a per-row outcome for every row, never a bare success count | Matches the M-Pesa parser's "fail loudly, never silently wrong" instinct — an admin who pastes in a messy list (typos, existing members, a seat limit reached partway through) needs to see exactly which rows did what, not infer it from a number |
| 2026-10-19 | `fulfillInvitesForNewUser` is called unconditionally from both `AuthController.register` and `.githubLogin`, not gated on "was this a brand-new account" | It's a safe no-op when there are no pending invites for that email, so there's no need to thread a new/existing flag through `AuthService` just to skip calling it — simpler than the alternative for no behavioural cost |
| 2026-10-19 | Cohort "checked in" uses `DailyLog`, not the hillside's `daily_credits` | User's explicit choice, flagged as a real fork with behavioural consequences (the credit pipeline requires hillside opt-in, which cohort members shouldn't need) before building either way |
| 2026-10-19 | The supportive/neutral/pressuring survey was built this slice rather than deferred | User's explicit choice over leaving the fourth pilot measure unavailable; scoped to one question, one answer per learner per cohort, no reminders or UI beyond a bare submit |
| 2026-10-19 | `CohortDashboardService` is a new service, not more methods added to `OrganisationService` | `OrganisationService` is about who's in what and who administers it; the dashboard is read-mostly aggregation over several other tables (`Goal`, `DailyLog`, survey responses) that has nothing to do with membership CRUD — same separation `HillsideV2Service` already has from `GranaryService` despite both reading user-scoped data |
| 2026-10-19 | A study group is set via the CSV's optional second column, not a separate endpoint | Matches the design reference: groups are assigned at invite time ("Morning group," "Evening group"), not edited afterward as a standalone action — no need for more surface area than the one place groups are actually set |
| 2026-10-19 | The by-group breakdown denominates by every group member, not just activated ones, unlike the cohort-wide measures | Matches the design reference's own group table ("12 of 14" where 14 is the group's full headcount) exactly, rather than silently reusing the cohort-wide measures' activated-only convention where the design doesn't ask for it |
| 2026-10-19 | The weekly check-in chart shares one `activatedDenominator` across every bar, instead of recomputing a per-week activated count | Matches the design reference's single "of 34 activated" caption under the whole chart; a per-week denominator would also be a stranger number to explain (activation is evaluated as of now, not as of that past week) |
| 2026-10-19 | A future week's `checkedIn` is `null`, never `0` | A week that hasn't started can't have "zero people checked in" reported about it — that would read as a real, bad result rather than "no data yet," which is what the design reference's dashed, blank bar actually means |
| 2026-10-21 | A test whose result depends on `CohortMembership.joinedAt` or `Goal.createdAt` pins both to the date it is about, instead of using a date near the real "now" | They are DB-generated (`DEFAULT now()`) and not settable through the entities, so a test using a literal date only passes on the day it was written. The earlier rule ("anchor to the real wall-clock date") was wrong: it made the test pass that day and fail two days later (Stage 1, 2026-10-21). `activationDoesNotDependOnTheRealDate` is the regression guard |
| 2026-10-19 | `setSeatLimit`/`submitSurveyResponse` return `204`, via `@ResponseStatus(HttpStatus.NO_CONTENT)`, not a bare `200` | Found live: the frontend's shared `request()` helper only special-cases `204` and otherwise always tries `response.json()`, so a `void` controller method's default `200`-with-empty-body threw `"Unexpected end of JSON input"` the first time either endpoint was actually called from the frontend. An honestly-empty write response should be `204`, matching the rest of this app's convention, rather than teaching the frontend to tolerate a malformed `200` |
| 2026-10-19 | `GranaryController`'s endpoints are `permitAll`, not JWT-protected like `/api/users/me/**` | Matches `/api/evidence-events`' own precedent, not a weaker standard: the frontend already resolves identity from the verified session cookie server-side and only ever sends that id, never a client-editable `userId` field in a form. Adding JWT verification here specifically, when nothing else at this app's current endpoints enforces it except the one `/api/users/me` proof-of-concept, would be inconsistent rigor rather than real additional safety |
| 2026-10-19 | `isDueForTwentyFourHourFollowUp`/`isDueForThirtyDayFollowUp` are methods on `PauseDecision` itself, not computed in the service or controller | The due-ness of a pause is a property of its own state (its two stored dates plus whether it's been decided/reflected on), not a computation that needs anything external; keeping it on the entity means `GranaryService`, the controller, and any future caller all get the identical, single definition of "due" for free |
| 2026-10-19 | The granary frontend's "Decline"/"Proceed" buttons are available on every undecided pause, not only once `dueForTwentyFourHourFollowUp` is true | Found while building the "I've already decided" button the design reference shows: gating the decide buttons behind the 24-hour mark would make that button a dead end with nowhere for an immediate decision to land. A real decision is always valid whenever the user actually makes it, not just once a timer says so — the UI still shows "Waiting · {date}" alongside the buttons when the 24-hour mark hasn't passed yet, so the timing information isn't lost |
| 2026-10-18 | Profile is its own `GET`/`PUT /api/users/me/profile` response type, not added to `UserResponse`/`GET /api/users/me` | `GET /api/users/me` is already called from several places that have no reason to ever see profile data (the sidebar's "signed in as," Hillside's session check); keeping it lean means there's no response path that could ever leak interests/thinkers/currency by accident just because something reused the "get the current user" call. The privacy test (`profileDataNeverAppearsInTheHillsideResponse`) is stronger for having two genuinely separate response shapes to prove apart, not one shape with fields that happen to be omitted sometimes |
| 2026-10-18 | `User.updateProfile(...)` takes every field as nullable and only applies the ones that aren't null, rather than requiring the full profile on every `PUT` | Matches this project's existing partial-update precedent (`HillsideOptInRequest` aside, most PATCH/PUT endpoints here update one concern at a time); a Profile form with many independent fields (interests, thinkers, currency, time zone, day-end hour, the analogies switch) shouldn't need to resend fields the user isn't currently touching |
| 2026-10-18 | `day_end_hour` defaults to 0, which is a mathematical no-op in `EvidenceCreditService`'s `hour < dayEndHour` check | Every existing Phase 9 test, and every real user until they visit Profile and change it, needs to see exactly the same local-date behavior as before this column existed — an hour comparison that can never be true at the default value guarantees that without a separate "is this feature on" flag |
| 2026-10-18 | `scoring_rules`, `interests`, `thinkers`, and `learning_preferences` all reuse `StringListConverter` (JSON array in a TEXT column) rather than join tables | None of these are queried into — a profile is always read back whole, never filtered by "users who like football" — so a join table would add schema and query complexity for a capability nothing in this app needs yet. Same reasoning `reading_notes.ideas_connected`/`quoted_phrases` already established |
| 2026-10-18 | Phase 9: one implicit "cohort" (every `hillside_opt_in = true` user) backs both the "cohort" and "open" scopes, rather than building minimal cohort groundwork ahead of Phase 12 | Asked the user directly, since `organisations`/`cohorts` are explicitly Phase 12 work and `CLAUDE.md`'s "build only the current phase" rule applies; this keeps Phase 9's schema stable (no `cohort_id` to retrofit later) while still shipping the full credit pipeline and real UI, including the scope segmented control, now |
| 2026-10-18 | Phase 9: `users.time_zone` added now (migration V18), ahead of Phase 10's full Profile page | Asked the user directly, since Phase 9's own test list requires "a credit decided on Nairobi time near midnight lands on the right local date," which needs *some* per-user time zone to exist first; one column is the same narrow-groundwork pattern `V14__users.sql` used for accounts ahead of full auth, not later-phase logic |
| 2026-10-18 | `EvidenceCreditService.ingest` is not itself `@Transactional` across its dedupe-lookup/save/award sequence | Each step's own safety comes from the database, not from Java-level transaction boundaries: the dedupe lookup is a fast-path check backed by `evidence_events`' `UNIQUE` constraint (with a caught `DataIntegrityViolationException` fallback), and the credit award is a native `ON CONFLICT DO NOTHING` upsert. Wrapping the whole method in one transaction would add nothing these two constraints don't already guarantee, and would hold a transaction open longer than necessary |
| 2026-10-18 | `HillsideV2Service.compute` iterates every opted-in user and recomputes each one's season score, rather than one aggregate SQL query | Matches `HillsideService` v1's existing precedent (see the 2026-10-17 "computed fresh on every request" entry below) — the dataset is small, and a clear, testable Java loop beats a harder-to-verify aggregate query for the same reason v1 chose it |
| 2026-10-18 | `NeighborRow` gained `rank` (the row's 1-indexed position in the full ranked list) | The frontend's hillside page had no way to show a rank number per "walking near you" row, which the design reference does; computed once in `HillsideService.compute` from the loop index already used to build the slice, not a second pass |
| 2026-10-17 | Only `routine_items` and `daily_logs` get an optional `user_id`, not every table | These two drive "days shown up," the one signal needed to demo real multi-user data; scoping every table (`goals` especially, which has no creation API at all today) is separate, much larger work that doesn't change what this slice needs to prove |
| 2026-10-17 | User scoping is an optional `userId` query param/field, not required authentication | Full auth enforcement needs the Next.js session side (not built) and would break the existing single-user frontend and every Phase 6 sync service immediately, since none of them have a user to pass yet. Optional scoping is additive: omit it, nothing changes; pass it, the new per-user data is isolated and testable |
| 2026-10-17 | `assignUser` is a plain setter called after construction, not a new constructor parameter | `RoutineItem` and `DailyLog` are each constructed from many places (eight sync services' worth, plus their own controllers); adding a required constructor parameter would mean touching every one of those call sites for a value most of them have no user to supply yet |
| 2026-10-17 | `/api/hillside` takes `asUserId` as a plain query param instead of waiting for real login | The same demo-mode pattern already used for `routine_items`/`daily_logs` scoping: it lets the real aggregation logic be built and tested against real seeded data now, rather than blocking the whole feature on the Next.js session, which is separate, larger work |
| 2026-10-17 | Band/rank/histogram are computed fresh on every request, nothing is cached or stored | The dataset is small (seeded test data, not a real userbase) and the computation is cheap; a stored leaderboard would need its own invalidation logic for no benefit yet |
| 2026-10-17 | `HillsideService.GENERAL_THRESHOLD` duplicates `tally.py`'s `TALLY_STICK_THRESHOLD` rather than sharing it | Backend (Java) and intelligence-service (Python) are separate codebases with no shared constants module; duplicating one named, documented constant is simpler than building cross-language constant sharing for a single number. Both must be kept in sync by hand if the threshold ever changes |
| 2026-10-17 | Pseudonyms are derived from the user id alone, with no stored mapping and no rotation | Keeps the privacy property simple to reason about (nothing new to leak, nothing new to store) at the cost of pseudonyms never changing on their own; real rotation is future work, tracked in the open questions |
| 2026-10-18 | Pseudonym rotation folds a "season" number (days-since-epoch ÷ 90, from the same `today` already threaded through `HillsideService.compute`) into the pseudonym hash, instead of storing a pseudonym plus a rotated-at timestamp | The open question noted a stored, explicitly-rotated pseudonym needs real design work and breaks the "no stored mapping" property. Folding the season into the existing deterministic hash gets real rotation (a different pseudonym roughly every 90 days, "change every season" per the design reference) with no new column, no migration, and no rotation job to run — the hash input changing on its own as time passes is the entire mechanism |
| 2026-10-17 | The hillside compares users within one shared deployment, not separate single-user instances reporting in | Asked the user rather than guessing, since this is a far bigger change (every table eventually needs a `user_id`) than the alternative (a lightweight opt-in reporting endpoint). Confirmed before writing any code, per PROJECT.md's explicit gate on this phase |
| 2026-10-17 | Both password and GitHub OAuth login are supported, not just one | Asked the user directly; they wanted both rather than picking one |
| 2026-10-17 | Next.js owns the login session; this backend only verifies a signed token Next.js attaches to its own server-side calls | Asked the user rather than guessing, since it determines whether this backend ever renders a login page or handles an OAuth redirect (it won't) and keeps today's "frontend does all the fetching server-side" architecture intact, just adding identity to those calls |
| 2026-10-17 | Two `SecurityFilterChain` beans (`/api/users/**` requiring a JWT, everything else `permitAll`), not one chain with a path-based authorization rule | Found as a real bug while wiring this in: a single chain with `oauth2ResourceServer().jwt()` intercepts *any* request bearing an `Authorization: Bearer` header globally and 401s if it isn't a valid JWT, even on a `permitAll` path — `MeasurementIngestController` and `github-login` both read their own app-level Bearer tokens, and presenting one made Spring Security treat it as a failed authentication attempt rather than deferring to the permitAll rule. Scoping the JWT filter to only the one path that actually needs it fixed this without touching those controllers |
| 2026-10-17 | `POST /api/auth/login` returns a generic 401 on every kind of failure (unknown email, GitHub-only account, wrong password) | Distinguishing them in the response would let a caller enumerate which emails are registered just by trying logins; a single generic failure gives nothing away |
| 2026-10-17 | `POST /api/auth/github-login` is protected by its own `AUTH_INTERNAL_TOKEN`, separate from the per-user JWT | This endpoint has no password or OAuth proof of its own — it trusts whoever calls it already verified the GitHub identity through a real OAuth flow. Without this, anyone could call it directly with an arbitrary email/githubId and get back that account's id, since nothing else in the request proves identity |
| 2026-10-17 | `AUTH_JWT_SECRET` has no default in `application.yml` but gets an insecure one in `docker-compose.yml` | A real deployment with real accounts must never run with a guessable secret, so the app fails to start without one by default; a local `docker compose up` would otherwise break for everyone, even though nothing in the app requires login yet, so the compose file supplies a clearly-labeled dev-only placeholder instead |
| 2026-10-17 | `User.passwordHash`/`githubId` are both nullable, enforced by a DB check constraint requiring at least one | Mirrors `DailyLog`'s existing exactly-one-of-two-targets pattern: the application layer (the `User` constructor) can't construct an invalid instance, and the DB constraint guards against any future write path that bypasses it |
| 2026-10-17 | Reader's notes will call the Anthropic Claude API for assessment, not OpenAI or another provider | Asked the user rather than guessing, since this is the first LLM integration in the codebase and involves a new credential. Also matches the project's own tooling |
| 2026-10-17 | `Book` supersedes `current_reading_log` as the source of truth for "what am I reading"; hub-sync now writes `Book` rows, not `current_reading_log` rows | Asked the user rather than deciding silently, since it changes an existing integration point. `Book` is a richer entity (chapter progress, author) that the plain title-only log can't represent; keeping both as parallel, equally-authoritative writers would create two answers to "what book is this" with no rule for which wins |
| 2026-10-17 | `current_reading_log`, `CurrentReadingController`, and Flock's manual reading form are left as-is, not removed or migrated this slice | They still work for manual entries; only hub-sync's automatic writes moved. Updating the frontend to read `Book` instead is real work belonging to the Reading page itself, not this groundwork slice, and deleting a still-used endpoint without replacing its caller would break Flock and Tally for no reason |
| 2026-10-17 | `Book.source` reuses the existing `ReadingSource` enum rather than a new one | Same two origins (`manual`, `hub-sync`) `current_reading_log` already modeled; a second enum with identical values would be duplication with no distinction to justify it |
| 2026-10-17 | `ReadingNote`'s assessment fields are all nullable; the note can be created without them | Writing the note down is the user's real action; requiring a successful LLM assessment first would block that action on an external API call that can fail, time out, or simply not be configured (`ANTHROPIC_API_KEY` unset) |
| 2026-10-17 | `ideasConnected`/`quotedPhrases` are stored as JSON in a TEXT column (`StringListConverter`), not their own join tables | Nothing in this phase queries into individual ideas or phrases, only reads a note's full list back; a join table would be real schema and query complexity for a feature nothing uses |
| 2026-10-17 | `OWN_WORDS_THRESHOLD = 70` is a plain named constant in `ReadingNoteService`, not configurable | PROJECT.md asks for a "named ratio threshold," not a tunable one; a fixed, documented number is honest about being a stated choice, same reasoning `patterns.py`'s `SKIP_HEAVY_THRESHOLD` and `tally.py`'s `TALLY_STICK_THRESHOLD` already use for their own thresholds |
| 2026-10-17 | A qualifying reading note's `daily_log` carries no `measuredMinutes` | A written reflection has no duration to report; inventing one would be a fabricated number, not a measured one, the same reasoning `GitHubSyncService`'s commit-count logs already apply. The goal-hours rollup needed no new code: `/tally`'s existing logic already only accumulates logs that do carry `measuredMinutes`, and already counts any log toward `daysShownUp`/`routineShownUp` regardless |
| 2026-10-17 | A note's `daily_log` is dated by today (`LocalDate.now()`), not the book's start date or anything else | The note is written today; this matches every other sync's dating rule (log the day the signal was observed), and a reader's note has no other natural date to use |
| 2026-10-17 | No `chapters` table; `Book` has plain `currentChapter`/`totalChapters` integer columns instead | `docs/design.md`'s Reading page needs "chapter progress" (a number out of a number), not a maintained catalog of chapter entities; the LLM's "chapter placed" field (Phase 7, not built yet) is a free-text guess about where a note falls, not a foreign key into a chapters table the user would have to keep in sync by hand |
| 2026-10-17 | `ActivityWatchClientImpl` assumes the backend and `aw-server` run on the same machine, reachable via `ACTIVITYWATCH_BASE_URL` | ActivityWatch has no cloud component, unlike WakaTime and GitHub; a pull from the backend only works if it can reach `aw-server`'s local API. Flagged to the user as a real architectural choice rather than decided silently; agreed to pull directly for now, revisit if the backend is ever deployed away from the machine running ActivityWatch (the push-via-health-endpoint alternative was declined for this slice) |
| 2026-10-17 | `GitHubCommitClientImpl` reads GitHub's public events API, not the search-commits API or a per-repo commit listing | No credential needed (matches hub-sync's existing no-token reads), at the cost of being a true backup signal rather than a complete history: only recent public push events are visible this way. That tradeoff matches how PROJECT.md itself frames GitHub in Phase 6, "optionally RescueTime" aside, as a backup, not a primary source |
| 2026-10-17 | A `daily_log` written by `GitHubSyncService` has no `measuredMinutes` | Unlike WakaTime/ActivityWatch, the underlying measurement is a commit count, not a duration; forcing a minutes value onto it would be a fabricated number, not a measured one |
| 2026-10-17 | `rescuetime-sync.enabled` defaults to `false`, the only one of the four sync flags that does | PROJECT.md names RescueTime as optional ("optionally RescueTime"), unlike WakaTime/ActivityWatch/GitHub which Phase 6 lists as given; the feature flag's production default reflects that difference instead of treating "optional" as just descriptive text |
| 2026-10-17 | `RescueTimeSyncService`'s package-visible test constructor defaults `enabled` to `false`, not `true` like the other three syncs' test constructors | Matches the production default exactly, so a test using that constructor without overriding the flag sees the same behaviour a real deployment would, rather than a convenience default that happens to disagree with it |
| 2026-10-17 | `POST /api/measurements` has no `source` field; the controller always writes `HEALTH` | A caller-supplied source would let this one endpoint impersonate any watering hole, not just push health data; fixing it removes that whole class of spoofing without needing per-source authorization |
| 2026-10-17 | A missing or blank `HEALTH_INGEST_TOKEN` rejects every request, rather than falling back to "accept anything" | An unconfigured secret is far more likely to mean "this was never set up" than "auth is intentionally off"; failing open here would turn a deployment mistake into an open write endpoint |
| 2026-10-17 | Token check is a plain header comparison in the controller, not Spring Security | This is the only endpoint in the API that needs auth at all; adding a security framework for one header comparison would be a disproportionate dependency, matching the project's stated aversion to infrastructure the current need doesn't justify |
| 2026-10-17 | Strava is deferred, not built, until Phase 8's accounts/auth architecture is agreed | Strava is the first watering hole needing real OAuth2 (redirect flow, stored refresh token, token refresh), not a static secret; that's the same category of decision Phase 8 is already explicitly gated on making deliberately rather than by precedent. Building a one-off token store for Strava first risks being redone once Phase 8's architecture is settled |
| 2026-10-17 | `GET /api/watering-holes` reads each sync's `enabled` flag via the same `@Value` property, rather than hardcoding a status list | A hardcoded "WakaTime: on" would silently drift from reality the moment someone sets `WAKATIME_SYNC_ENABLED=false`; reading the real property means the page always reflects what the backend is actually doing, not what it did when this endpoint was written |
| 2026-10-17 | The "how each activity is counted" table is built by the frontend from `GET /api/routine-items`, not duplicated into `GET /api/watering-holes` | The task specifically asked for this table to be generated from routine items, not hard-coded; `RoutineItemResponse` already exposes `source` and `countRule`, so a second endpoint returning the same data in a different shape would be duplication with no benefit |
| 2026-10-17 | `ActivityWatchSyncService` matches the afk bucket by `id.contains("afk")`, not an exact name | aw-server names the bucket `aw-watcher-afk_<hostname>`, so the hostname suffix varies by machine; a substring match avoids hardcoding a specific machine's bucket id |
| 2026-10-17 | A routine item is matched to WakaTime by `source = WAKATIME`, not by name | hub-sync matches `journaling`/`writing` by name because those are fixed, singular routine items; `WAKATIME` is a `RoutineItemSource` value Phase 5 already built for exactly this purpose, and matching by it lets more than one routine item (e.g. "deep work" and a specific project) be wired to the same external source without new code |
| 2026-10-17 | `WakaTimeSyncService` writes a `daily_log` once per routine item per day and never updates it, while `measurements` is upserted for the same day | A day's WakaTime total can still be settling when first synced and change on a later run; `measurements` needs to reflect the corrected value, but the daily_log is just "did this happen that day", which doesn't need to change once true — matches the project's no-edit-history stance on `daily_logs` (`database.md`) while still letting the raw number underneath it improve |
| 2026-10-17 | The sync window is the last 7 days, not just today | A day's WakaTime summary can be incomplete if synced while still in progress; re-fetching a trailing week on every run, upserting into `measurements`, lets a later, more complete total overwrite an earlier partial one without needing a separate "finalize yesterday" job |
| 2026-10-17 | `WakaTimeClientImpl` has no automated test against the real API, same as `GitHubContentClientImpl` | Same reasoning: the real API needs a real key and is a live external dependency; `WakaTimeSyncServiceTest` fakes `WakaTimeClient` instead, same seam pattern |
| 2026-10-17 | `measurements` is upserted by `(source, metric, measurement_date)`, not append-only keyed by an external id | Unlike `hub_sync_processed_files` (one UMWAYI file is one immutable fact), a day's WakaTime/ActivityWatch total can change if queried again before the day is over, or be corrected later; upserting means re-running the sync job converges on the same row instead of accumulating stale duplicates for the same day |
| 2026-10-17 | `Measurement.updatedAt` is a plain app-managed field, not DB-generated like `createdAt` | `created_at`'s `@Generated(event = EventType.INSERT)` pattern only fires on insert; there's no equivalent "fires on every update" annotation already used in this codebase, and adding a DB trigger would be new infrastructure for one column, so the service sets it explicitly in both the create and update branches of `recordMeasurement` |
| 2026-10-17 | `MeasurementSource` is a separate enum from `RoutineItemSource`, not reused | `RoutineItemSource` includes `MANUAL`, `HUB`, and `READER_NOTES`, none of which are external measurement sources; it also lacks `RESCUETIME`, which Phase 6 names as a measurement source. A measurement's source and a routine item's completion source are different concepts that happen to overlap on four names |
| 2026-10-17 | `GitHubContentClient` is an interface with a real `java.net.http.HttpClient` implementation, `HubSyncService` depends on the interface | Lets `HubSyncServiceTest` substitute a fake client with fixed files/commit dates instead of hitting the real, rate-limited GitHub API in every test run, same seam pattern as `HubJournalReader`'s injectable repo URL and key supplier |
| 2026-10-17 | `HubSyncService` has two constructors, a Spring-selected one taking `hub-sync.enabled` via `@Value`, and a package-visible one defaulting it to `true` | Tests call `syncOnce()` directly and don't care about the scheduler flag at all; production wiring needs the property to turn polling off in every test context (see `testing.md`) |
| 2026-10-16 | `RoutineItemCategory`, `RoutineItemSource`, `HillsideField` use `@Enumerated(EnumType.STRING)` directly, no converter | Their values are already valid Java enum constant names (`BODY`, `WAKATIME`, `DEEP_WORK`, ...); `DailyLogSource`'s converter exists only because its stored values are lowercase and hyphenated, which doesn't apply here |
| 2026-10-14 | `HubJournalReader` shells out to the real `git` and `git-crypt` binaries via `ProcessBuilder` with an argument list, not a shell string, and not a reimplementation of the git-crypt format | Avoids ever constructing a shell command from the key path or repo URL (no injection surface), and git-crypt's on-disk format is undocumented-as-a-library-contract; its own CLI is the only supported way to unlock it |
| 2026-10-14 | `HubJournalReader` takes a `Supplier<String>` for the key and a constructor-injected repo URL, both package-visible overrides of the public no-arg constructor | Lets tests substitute a throwaway key and a throwaway local repo without touching the real `UMWAYI_JOURNAL_KEY_B64` variable or the real UMWAYI remote, while Spring still wires the plain `HubJournalReader()` bean in production |
| 2026-09-19 | `TaskService`, the first real service class in this codebase, owns the DONE-transition's daily_log create/delete | Phase 1/2 controllers called repositories directly since their logic was pure CRUD/validation; a status change that coordinates `TaskRepository` and `DailyLogRepository` together (and must delete the log again if the task moves back out of DONE) is real business logic, not a data-layer passthrough, so it earned a service rather than being stuffed into the controller |
| 2026-09-19 | `ScheduleTemplateRepository` and `TaskRepository` use `JOIN FETCH` queries from the start | Same `LazyInitializationException` risk as `LearningTopicRepository` in Phase 1 (open-in-view is disabled); applied proactively this time instead of waiting to hit it again in manual testing |
| 2026-09-19 | `RoutineItemResponse` now includes `createdAt` | Needed by the intelligence service's Phase 2 pattern detection (completion rates and skip-heavy days are windowed per item from its creation date); the data already existed on the entity, this only exposes it. See `intelligence-service.md` |
| 2026-09-18 | `LearningTopicRepository` uses `JOIN FETCH` queries (`findAllWithGoal`, `findByGoalIdWithGoal`) instead of the derived `findAll`/`findByGoalId` | Found via manual end-to-end testing: `GET /api/learning-topics` 500'd with `LazyInitializationException` because `open-in-view` is disabled and the response mapping reads `topic.getGoal().getName()` after the transaction closes. No API test had asserted on the GET response body, only the POST path, which never touches a lazy `goal` proxy since it already holds a loaded `Goal`. Fixed by fetch-joining goal in the query itself; a regression test now asserts `goalName` is populated on list |
| 2026-09-18 | Foreign key validation failures return 400, not 404 | The bad ID is client input error (a field in the request body), not a missing resource being fetched directly |
| 2026-09-18 | Data layer only, no intelligence logic in this service | Keeps the data model stable while the intelligence service evolves independently, same separation settlement-engine uses between core and ml-service |

**Watering holes status endpoint built:** `GET /api/watering-holes`
(`com.routinemachine.core.api.WateringHoleStatusController`), read by the frontend's watering
holes page (see `frontend.md`). Returns one entry per source Phase 6 names (HUB, WAKATIME,
ACTIVITYWATCH, GITHUB, RESCUETIME, HEALTH; Strava excluded, deferred above) with `enabled`
(the sync's own feature flag, read the same way the sync service itself reads it, so this
never drifts from what's actually running), `lastActivityAt` (the latest timestamp recorded
for that source — `hub_sync_processed_files.created_at` for HUB, the latest
`measurements.updated_at` for everything else, null if nothing has ever synced), and a fixed
`description`. Deliberately does not expose the per-routine-item "how each activity is
counted" table; the frontend builds that directly from `GET /api/routine-items`'s existing
`source`/`countRule` fields instead of this endpoint duplicating that data.

## Open questions

- M-Pesa's real statement PDF text layout has never been verified against a real statement —
  per the task's own instruction, `MpesaStatementParser`/`MpesaPdfReader` are tested only
  against a synthetic fixture built in-test. The row-shape regex is a best-effort match for
  the publicly-documented statement structure (receipt no., completion time, details,
  transaction status, paid in, withdrawn, balance); if the real extracted text differs enough
  that the regex never matches, every import will fail loudly (by design) until someone
  checks it against a real statement and adjusts the pattern.
- The category keyword map (`MpesaStatementParser.CATEGORY_KEYWORDS`) is a first pass, not
  validated against real M-Pesa transaction description text — merchant names, Till/Paybill
  numbers, and phrasing it hasn't seen will land in OTHER rather than their real category
  until the map is extended with real examples.
- Bank statement CSV import and a typed-in/read money-market-fund balance (both named in the
  design reference's "Where the granary drinks from" card) aren't built — only M-Pesa and the
  manual tap exist as sources.
- `pause_decisions.amount`/`heard_from` are free text; there's no structured currency amount
  or a fixed enum for "where did you hear it" (the frontend offers a fixed radio set, but the
  backend accepts anything) — fine for the current single-field-per-pause scope, but would
  need revisiting if this data were ever aggregated or charted.
- No endpoint deletes a profile's data or exports it (the design reference shows "Export my
  data"/"Delete my account" buttons; not built — out of this phase's explicit scope).
- Nothing yet calls `PUT /api/users/me/profile` except the frontend's own Profile page; no
  other feature (e.g. the granary, Phase 11) reads profile fields yet.
- Phase 9's "cohort-scoped by default" hillside isn't real yet — `/api/hillside/v2`'s
  "cohort" and "open" scopes both resolve to the same opted-in population, since
  `organisations`/`cohorts` don't exist until Phase 12. The decisions log records this as a
  deliberate choice, asked and confirmed, not an oversight — Phase 12 should be able to add
  real cohort filtering without changing `daily_credits`' schema.
- `seasonStart`'s UTC-Monday default is a stand-in for "the cohort's time zone" (the design's
  own phrase); with no cohort entity yet, there's no cohort-level time zone to use. Revisit
  once Phase 12 gives a cohort its own zone.
- `scoring_rules` has no authoring UI or endpoint; rows exist only if written directly (see
  `database.md`'s decisions log). A goal that should have a real minute threshold has no way
  to get one through the product yet.
- `GoalRepository` has no way to list only goals with `daily_credits` activity, so the
  frontend's hillside goal filter currently lists every one of a user's own goals, including
  ones with no evidence at all this season — a minor UX gap, not a correctness one.
- Whether `HubJournalReader` should hold the full journal-decrypting key at all, versus a
  least-privilege alternative that needs no key. Tracked in `hub-sync.md`'s open questions,
  to revisit before Stage 3.
- Strava's own sync (client, token storage, refresh, the actual OAuth redirect flow) is still
  not designed. Phase 8's accounts/auth architecture is now agreed (see above), so this is no
  longer blocked on that decision specifically, but Strava's own OAuth design is separate
  work, not started.
- Whether `POST /api/reading-notes` should itself call the intelligence service's
  `POST /reading-notes/assess` server-side, instead of expecting the frontend to call it
  first and pass the result along, isn't decided. The current shape matches the project's
  existing frontend-orchestration precedent (`schedule.md`'s materialize flow), but hasn't
  been tested against how the Reading page actually ends up calling it.
- Whether `Book`'s `currentChapter`/`totalChapters` should be settable through
  `POST /api/books` only, or need their own `PATCH` for updating progress on an existing book
  without re-creating it, isn't decided; no caller needs it yet.
- `goals` now has a `user_id` and a creation API (see above); no other table is scoped yet.
- `hillside_opt_in` now has a real frontend toggle (`/hillside`'s "Join the hillside"/"Leave
  the hillside" link, a Server Action calling `PUT /api/users/me/hillside-opt-in` — see
  `frontend.md`). Still true: a real user defaults to opted-out until they use it.
- Pseudonym rotation is now live (season folded into the hash — see the decisions log above),
  not just an open question.
- `asUserId` on `/api/hillside` is now overridden by the real session when one exists (the
  frontend ignores the query param entirely once a user is signed in — see `frontend.md`);
  `userId` on the two scoped endpoints (`routine_items`, `daily_logs`) is still a plain,
  unauthenticated param nothing in the frontend writes through yet, since neither endpoint has
  a creation flow in the UI that would need it.
- The Next.js side of password login is now built and live-verified (`/login`, `/register`,
  `lib/auth/jwt.ts`/`session.ts` — see `frontend.md`): `GET /api/users/me` has been proven
  against a real token Next.js itself minted and signed, not just the hand-built token
  `UserControllerApiIntegrationTest` uses. The GitHub OAuth side (next-auth or equivalent
  wiring, the GitHub OAuth app registration and its real client id/secret, the login UI's
  GitHub button) is still not built — explicitly deferred, not an oversight.
- Sessions last a fixed 7 days (`SESSION_TTL_SECONDS` in `frontend/lib/auth/jwt.ts`) with no
  refresh — a user who stays logged in past that is just signed out, same as the cookie never
  existing. Whether JWTs should carry a shorter expiry Next.js actively refreshes, and what
  that refresh looks like across the frontend/backend boundary, isn't decided; nothing
  exercises refresh behavior yet, since nothing refreshes.