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
whole application context. `HubJournalReaderTest` is a plain JUnit test, no Spring context at
all, against throwaway git-crypt repos and keys built per test under `@TempDir`
(`GitCryptTestFixture`); it skips cleanly with `Assumptions.assumeTrue` when `git-crypt` isn't
on `PATH`, rather than failing the build. Log assertions use a Logback `ListAppender` attached
directly to `HubJournalReader`'s own logger, not console-output capture, so they only see what
that class logs. Phase 5 added repository and API tests for `RoutineItem`'s new columns
(default `source`/`private` when not given, a fully categorised item linked to a goal via
`findWithGoalById`, rejecting an unknown `goalId`) and for `DailyLog.measuredMinutes`.
`HubSyncServiceTest` instantiates `HubSyncService` directly inside a `@DataJpaTest`, same
pattern as `TaskServiceTest`, against a fake `GitHubContentClient` (fixed files and commit
dates, no network) for the listing/dedupe/missing-routine-item cases, and reuses
`GitCryptTestFixture` (same skip-if-no-`git-crypt` guard) for the current-book-sync cases.
Every `@SpringBootTest`/`@DataJpaTest` class sets `hub-sync.enabled=false`
(`AbstractIntegrationTest`), so `HubSyncService`'s `@Scheduled` job never fires — and never
makes a real outbound call — anywhere in the suite. 76 backend tests, all passing.

Intelligence service: pytest. The suggestion rule (`choose_suggestion`), the pattern-
detection rule (`compute_patterns`), and the tally rule (`compute_tally`) are each tested
directly as pure functions against plain dicts/dates, no HTTP involved — all three take an
explicit `today` argument for exactly this reason, so date-window edge cases don't need the
system clock mocked. The FastAPI routes are tested with `TestClient` and `respx` mocking the
backend's HTTP responses. 26 tests, all passing.

Frontend: Vitest, but only for `lib/schedule.ts`'s pure logic (which templates still need a
task, and computing a date's weekday independent of server timezone) — 5 tests, unchanged by
Phase 5's redesign since no new pure logic was added to the frontend. Page components are
verified by running the app and clicking through all four views and the full
materialize → start → done flow in a browser instead, matching settlement-engine's frontend,
which has no automated test framework at all (see decisions log). `npm run lint` and
`npm run build` (strict TypeScript) also run as part of finishing a phase.

## Decisions log

| Date | Decision | Reason |
|------|----------|--------|
| 2026-10-17 | `hub-sync.enabled=false` is set once, on `AbstractIntegrationTest`, inherited by every subclass | `HubSyncService`'s `@Scheduled` job would otherwise fire inside any full `@SpringBootTest` context and make a real outbound GitHub/UMWAYI call during the suite; one shared property beats adding `@TestPropertySource` to each of the eight affected test classes individually |
| 2026-10-17 | `HubSyncServiceTest` fakes `GitHubContentClient` instead of calling the real GitHub API | The real API is rate-limited (60 requests/hour unauthenticated) and the test suite needs to run repeatedly and offline; the fake's fixed files and commit dates make the dedupe/double-count/missing-routine-item cases exact and deterministic, which a live call couldn't guarantee anyway |
| 2026-10-16 | `compute_tally` gets its own pure-function tests, separate from `compute_patterns`'s | Different windowing rules (clipped to an item's lifetime vs the completion-rate window) and a different aggregation shape (cumulative per goal per week) deserve their own focused test cases rather than growing one already-long test module |
| 2026-10-14 | `HubJournalReaderTest` builds its own throwaway git-crypt repos and keys per test, never touching the real UMWAYI repo or key | The task is explicitly about handling a real secret correctly; testing against the real key would mean a real secret has to exist somewhere reachable by the test suite, which is the exact risk this design avoids. A throwaway git-crypt repo exercises the identical clone/unlock/extract path |
| 2026-10-14 | Tests requiring `git-crypt` skip via `Assumptions.assumeTrue` rather than failing | A machine without `git-crypt` on `PATH` can still build and run the rest of the suite; failing the whole build over one missing optional binary would block unrelated work |
| 2026-09-19 | Frontend gets Vitest for pure logic only, no component/page test framework | Matches settlement-engine's frontend precedent (no test framework there either); the pages are thin data-fetch-and-render, verified by actually running them in a browser (see PROJECT.md's rule on this), while the one function with real branching logic (`templatesNeedingMaterialization`, `dayOfWeekName`) is exactly the kind of thing worth a fast, deterministic unit test |
| 2026-09-18 | Manual Docker Compose verification remains part of finishing a phase, not just Phase 1 | It found a second real bug in Phase 2 (streak/completion-rate windowing inconsistency on backdated logs, see `intelligence-service.md`) that no unit test had a reason to check for, since nothing in the Phase 1 test suite exercised a log dated before its item's creation |
| 2026-09-18 | Corrected this doc's backend test count from 22 to 23 | Stale: the count was written before the `listsTopicsWithTheirGoalNamePopulated` regression test (added for the lazy-loading fix below) was accounted for here, and PROJECT.md's status log had already been updated to 23 while this file wasn't. Confirmed 23 by re-running `./mvnw test` rather than trusting either doc |
| 2026-09-18 | Every list endpoint gets an API-level test asserting on the response body, not just the create endpoint | A real bug (`GET /api/learning-topics` 500ing on a lazy `goal` proxy) shipped past a full green test suite because only the POST path had a body assertion; manual end-to-end testing against the running Docker Compose stack caught it, TDD alone didn't |
| 2026-09-18 | Integration tests use Testcontainers Postgres, not H2 or mocks | Flyway migrations and the daily_logs check constraints are Postgres-specific; a lighter substitute wouldn't actually test them, matching settlement-engine's approach |
| 2026-09-18 | Suggestion logic is unit-tested as a pure function, HTTP layer tested separately with respx | Keeps the rule's test cases fast and readable, and isolates HTTP mocking to the one test that needs it |
| 2026-09-18 | TDD required for both services from the first line of code | Stated non-negotiable in PROJECT.md's rules section, not left to convention |

## Open questions

- `GitHubContentClientImpl` (the real HTTP implementation) has no automated test of its own
  against the live GitHub API; its correctness was checked manually with `curl` against the
  real UMWAYI repo instead, to avoid a test that's flaky, slow, and rate-limited on every run.
