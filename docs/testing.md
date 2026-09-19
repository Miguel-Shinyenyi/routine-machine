# Testing

## Purpose

Test strategy and coverage rules for both services.

## Current state

Backend: JUnit 5 + AssertJ. Repository tests use `@DataJpaTest` against a real Postgres via
Testcontainers (`AbstractIntegrationTest`), not an in-memory substitute, since Flyway
migrations and Postgres-specific check constraints need to run against the real engine. API
tests use `@SpringBootTest` + `MockMvc` against the same Testcontainers Postgres. A pure unit
test (`DailyLogTest`) covers the entity's exactly-one-target invariant without needing a
database at all. `TaskServiceTest` instantiates `TaskService` directly (`new
TaskService(taskRepository, dailyLogRepository)`) inside a `@DataJpaTest`, rather than pulling
in a full `@SpringBootTest`, since the service only needs the two real repositories, not the
whole application context. 56 backend tests, all passing.

Intelligence service: pytest. The suggestion rule (`choose_suggestion`) and the pattern-
detection rule (`compute_patterns`) are each tested directly as pure functions against plain
dicts/dates, no HTTP involved — `compute_patterns` takes an explicit `today` argument for
exactly this reason, so streak/completion-rate edge cases don't need the system clock mocked.
The FastAPI routes are tested with `TestClient` and `respx` mocking the backend's HTTP
responses. 18 tests, all passing.

Frontend: Vitest, but only for `lib/schedule.ts`'s pure logic (which templates still need a
task, and computing a date's weekday independent of server timezone) — 5 tests. Page
components are verified by running the app and clicking through all four views and the full
materialize → start → done flow in a browser instead, matching settlement-engine's frontend,
which has no automated test framework at all (see decisions log).

## Decisions log

| Date | Decision | Reason |
|------|----------|--------|
| 2026-09-19 | Frontend gets Vitest for pure logic only, no component/page test framework | Matches settlement-engine's frontend precedent (no test framework there either); the pages are thin data-fetch-and-render, verified by actually running them in a browser (see PROJECT.md's rule on this), while the one function with real branching logic (`templatesNeedingMaterialization`, `dayOfWeekName`) is exactly the kind of thing worth a fast, deterministic unit test |
| 2026-09-18 | Manual Docker Compose verification remains part of finishing a phase, not just Phase 1 | It found a second real bug in Phase 2 (streak/completion-rate windowing inconsistency on backdated logs, see `intelligence-service.md`) that no unit test had a reason to check for, since nothing in the Phase 1 test suite exercised a log dated before its item's creation |
| 2026-09-18 | Corrected this doc's backend test count from 22 to 23 | Stale: the count was written before the `listsTopicsWithTheirGoalNamePopulated` regression test (added for the lazy-loading fix below) was accounted for here, and PROJECT.md's status log had already been updated to 23 while this file wasn't. Confirmed 23 by re-running `./mvnw test` rather than trusting either doc |
| 2026-09-18 | Every list endpoint gets an API-level test asserting on the response body, not just the create endpoint | A real bug (`GET /api/learning-topics` 500ing on a lazy `goal` proxy) shipped past a full green test suite because only the POST path had a body assertion; manual end-to-end testing against the running Docker Compose stack caught it, TDD alone didn't |
| 2026-09-18 | Integration tests use Testcontainers Postgres, not H2 or mocks | Flyway migrations and the daily_logs check constraints are Postgres-specific; a lighter substitute wouldn't actually test them, matching settlement-engine's approach |
| 2026-09-18 | Suggestion logic is unit-tested as a pure function, HTTP layer tested separately with respx | Keeps the rule's test cases fast and readable, and isolates HTTP mocking to the one test that needs it |
| 2026-09-18 | TDD required for both services from the first line of code | Stated non-negotiable in PROJECT.md's rules section, not left to convention |

## Open questions

- None currently open for Phase 3.
