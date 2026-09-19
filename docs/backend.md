# Backend

## Purpose

Spring Boot service owning the data: routine items, daily logs, learning topics, goals, and
(from Phase 3) the fixed schedule and its tasks. Exposes a REST API. No suggestion or
pattern-detection logic lives here, that belongs to `intelligence-service.md`.

## Current state

Phase 1 and 2 built: Spring Boot 3.5, Java 21, PostgreSQL via Flyway migrations. Four
endpoints, matching the four Phase 1 entities in `database.md`:

- `GET /api/goals`, lists the three seeded goals. Read-only; goals are fixed reference data,
  not user-editable in Phase 1.
- `GET /api/routine-items`, `POST /api/routine-items`, define and list routine items. List
  responses include each item's `createdAt`, needed by the intelligence service's Phase 2
  pattern detection to bound a completion-rate/skip-heavy-day window per item.
- `GET /api/learning-topics?goalId=`, `POST /api/learning-topics`, define and list learning
  topics, each tied to a goal. `goalId` filter is optional.
- `GET /api/daily-logs?date=`, `POST /api/daily-logs`, record and list completions. A log
  targets exactly one of `routineItemId` or `learningTopicId`; `source` defaults to `manual`
  and accepts `hub-sync`, planned to also accept `schedule` in Phase 3. `date` filter is
  optional.

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

## Decisions log

| Date | Decision | Reason |
|------|----------|--------|
| 2026-09-19 | `TaskService`, the first real service class in this codebase, owns the DONE-transition's daily_log create/delete | Phase 1/2 controllers called repositories directly since their logic was pure CRUD/validation; a status change that coordinates `TaskRepository` and `DailyLogRepository` together (and must delete the log again if the task moves back out of DONE) is real business logic, not a data-layer passthrough, so it earned a service rather than being stuffed into the controller |
| 2026-09-19 | `ScheduleTemplateRepository` and `TaskRepository` use `JOIN FETCH` queries from the start | Same `LazyInitializationException` risk as `LearningTopicRepository` in Phase 1 (open-in-view is disabled); applied proactively this time instead of waiting to hit it again in manual testing |
| 2026-09-19 | `RoutineItemResponse` now includes `createdAt` | Needed by the intelligence service's Phase 2 pattern detection (completion rates and skip-heavy days are windowed per item from its creation date); the data already existed on the entity, this only exposes it. See `intelligence-service.md` |
| 2026-09-18 | `LearningTopicRepository` uses `JOIN FETCH` queries (`findAllWithGoal`, `findByGoalIdWithGoal`) instead of the derived `findAll`/`findByGoalId` | Found via manual end-to-end testing: `GET /api/learning-topics` 500'd with `LazyInitializationException` because `open-in-view` is disabled and the response mapping reads `topic.getGoal().getName()` after the transaction closes. No API test had asserted on the GET response body, only the POST path, which never touches a lazy `goal` proxy since it already holds a loaded `Goal`. Fixed by fetch-joining goal in the query itself; a regression test now asserts `goalName` is populated on list |
| 2026-09-18 | Foreign key validation failures return 400, not 404 | The bad ID is client input error (a field in the request body), not a missing resource being fetched directly |
| 2026-09-18 | Data layer only, no intelligence logic in this service | Keeps the data model stable while the intelligence service evolves independently, same separation settlement-engine uses between core and ml-service |

## Open questions

- None currently open for Phase 3.