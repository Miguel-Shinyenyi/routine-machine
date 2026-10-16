# Backend

## Purpose

Spring Boot service owning the data: routine items, daily logs, learning topics, goals, and
(from Phase 3) the fixed schedule and its tasks. Exposes a REST API. No suggestion or
pattern-detection logic lives here, that belongs to `intelligence-service.md`. Also holds
hub-sync (`HubJournalReader`, `HubSyncService`, `GitHubContentClient`), which reads UMWAYI's
journal and essays and turns them into routine completions, see below and `hub-sync.md`.

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
  `source=manual` (hub-sync's own writes, once built, won't go through this human-facing
  endpoint).

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
- Also calls `HubJournalReader.readCurrentBook()` and writes a new `current_reading_log` row
  only when the title changed from the latest row.

`RoutineMachineApplication` now has `@EnableScheduling`.

## Decisions log

| Date | Decision | Reason |
|------|----------|--------|
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

## Open questions

- Whether `HubJournalReader` should hold the full journal-decrypting key at all, versus a
  least-privilege alternative that needs no key. Tracked in `hub-sync.md`'s open questions,
  to revisit before Stage 3.