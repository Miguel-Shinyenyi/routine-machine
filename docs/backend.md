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

**Phase 3 (not yet built):** endpoints planned for the schedule and its tasks, detailed in
`schedule.md`. Likely shape: `GET`/`POST` on `/api/schedule-templates`, and `GET
/api/tasks?date=` plus a status update (`PATCH /api/tasks/{id}`) that, on transition to
`DONE`, creates the corresponding `daily_log`. Exact shape to be finalized when
implementation starts, this is a plan, not a built contract yet.
- `GET /api/current-reading`, returns the latest row from `current_reading_log`.
- `POST /api/current-reading`, adds a new row (manual updates, separate from hub-sync's
  automatic ones).

## Decisions log

| Date | Decision | Reason |
|------|----------|--------|
| 2026-09-18 | `RoutineItemResponse` now includes `createdAt` | Needed by the intelligence service's Phase 2 pattern detection (completion rates and skip-heavy days are windowed per item from its creation date); the data already existed on the entity, this only exposes it. See `intelligence-service.md` |
| 2026-09-18 | `LearningTopicRepository` uses `JOIN FETCH` queries (`findAllWithGoal`, `findByGoalIdWithGoal`) instead of the derived `findAll`/`findByGoalId` | Found via manual end-to-end testing: `GET /api/learning-topics` 500'd with `LazyInitializationException` because `open-in-view` is disabled and the response mapping reads `topic.getGoal().getName()` after the transaction closes. No API test had asserted on the GET response body, only the POST path, which never touches a lazy `goal` proxy since it already holds a loaded `Goal`. Fixed by fetch-joining goal in the query itself; a regression test now asserts `goalName` is populated on list |
| 2026-09-18 | Foreign key validation failures return 400, not 404 | The bad ID is client input error (a field in the request body), not a missing resource being fetched directly |
| 2026-09-18 | Data layer only, no intelligence logic in this service | Keeps the data model stable while the intelligence service evolves independently, same separation settlement-engine uses between core and ml-service |

## Open questions

- Exact request/response shape for `schedule-templates` and `tasks` endpoints, to be decided
  when Phase 3 implementation starts.