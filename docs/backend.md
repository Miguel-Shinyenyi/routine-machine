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

- `GET /api/goals`, lists the three seeded goals. Read-only; goals are fixed reference data,
  not user-editable in Phase 1.
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
updates anymore. Chapters, reader's notes themselves, the LLM assessment call, and the
goal-hours rollup are not built yet, deliberately scoped out of this groundwork slice.

## Decisions log

| Date | Decision | Reason |
|------|----------|--------|
| 2026-10-17 | Reader's notes will call the Anthropic Claude API for assessment, not OpenAI or another provider | Asked the user rather than guessing, since this is the first LLM integration in the codebase and involves a new credential. Also matches the project's own tooling |
| 2026-10-17 | `Book` supersedes `current_reading_log` as the source of truth for "what am I reading"; hub-sync now writes `Book` rows, not `current_reading_log` rows | Asked the user rather than deciding silently, since it changes an existing integration point. `Book` is a richer entity (chapter progress, author) that the plain title-only log can't represent; keeping both as parallel, equally-authoritative writers would create two answers to "what book is this" with no rule for which wins |
| 2026-10-17 | `current_reading_log`, `CurrentReadingController`, and Flock's manual reading form are left as-is, not removed or migrated this slice | They still work for manual entries; only hub-sync's automatic writes moved. Updating the frontend to read `Book` instead is real work belonging to the Reading page itself, not this groundwork slice, and deleting a still-used endpoint without replacing its caller would break Flock and Tally for no reason |
| 2026-10-17 | `Book.source` reuses the existing `ReadingSource` enum rather than a new one | Same two origins (`manual`, `hub-sync`) `current_reading_log` already modeled; a second enum with identical values would be duplication with no distinction to justify it |
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

- Whether `HubJournalReader` should hold the full journal-decrypting key at all, versus a
  least-privilege alternative that needs no key. Tracked in `hub-sync.md`'s open questions,
  to revisit before Stage 3.
- Strava's own sync (client, token storage, refresh, the actual OAuth redirect flow) is not
  designed yet, deliberately, pending Phase 8's accounts/auth architecture decision. Revisit
  once that's agreed, not before.
- Phase 7's chapters, reader's notes themselves, the Claude API assessment call (own-words
  percentage, ideas connected, quoted phrases, the one question back, chapter placed), the
  named ratio threshold that counts a note toward the reading goal, and the Reading frontend
  page are all not designed or built yet. `Book` is groundwork only.
- The ratio threshold's exact value and what it's named are not decided yet; this needs its
  own design pass once the assessment call itself is being built, not guessed at now.
- Whether `Book`'s `currentChapter`/`totalChapters` should be settable through
  `POST /api/books` only, or need their own `PATCH` for updating progress on an existing book
  without re-creating it, isn't decided; no caller needs it yet.