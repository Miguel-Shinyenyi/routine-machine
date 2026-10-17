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
makes a real outbound call — anywhere in the suite. `MeasurementServiceTest` instantiates
`MeasurementService` directly inside a `@DataJpaTest`, same pattern as `TaskServiceTest` and
`HubSyncServiceTest`, covering: a new `(source, metric, date)` creates a row; recording the
same `(source, metric, date)` twice updates the existing row's value instead of creating a
second one (the double-write case, written first); a different metric, a different source, and
a different date are each recorded as a separate row. `WakaTimeSyncServiceTest` follows the
same `@DataJpaTest`-direct-instantiation pattern against a fake `WakaTimeClient` (fixed daily
totals, no network): a day's total becomes a `coding_minutes` measurement in minutes; syncing
the same day twice updates that measurement instead of duplicating it; a routine item sourced
from `WAKATIME` gets a daily_log for the summarised day; syncing the same day twice does not
create a second daily_log for that routine item (written first); no matching routine item still
records the measurement but logs nothing; a day with zero seconds coded is skipped entirely.
`ActivityWatchSyncServiceTest` is the same six cases again against a fake `ActivityWatchClient`,
substituting active minutes for coding minutes. `GitHubSyncServiceTest` is the same six cases
once more against a fake `GitHubCommitClient`, substituting a commit count for a duration (and
asserting the resulting daily_log's `measuredMinutes` is null, since a count isn't a duration).
`RescueTimeSyncServiceTest` has those same six cases plus a seventh,
`theFeatureFlagDefaultsToDisabledSoSyncOnceIsNeverCalledByTheScheduler`: constructed with the
package-visible test constructor (no explicit `enabled`), calling `scheduledSync()` directly
writes nothing, confirming the opt-in default actually holds rather than just being described
in a doc. `MeasurementIngestControllerTest` is a `@SpringBootTest` + `MockMvc` API test (same
pattern as `DailyLogApiIntegrationTest`), covering a valid-token post, a missing token, a
wrong token, a missing `metric`, a negative `value`, and reposting the same day/metric
updating rather than duplicating; a second class,
`MeasurementIngestControllerNoTokenConfiguredTest`, has no `health-ingest.token` property set
at all and asserts every request is rejected, proving the fail-closed default. These two
assert on `measurementRepository.count()` deltas, not `findAll()`'s exact contents, because
`@SpringBootTest` methods in this codebase share one Postgres instance across a test class
with no per-method rollback — see the decisions log below for the bug this caught.
`WateringHoleStatusControllerTest` overrides every sync's enabled flag via `@TestPropertySource`
to a pattern that doesn't match any real default (HUB and ACTIVITYWATCH and RESCUETIME true,
WAKATIME and GITHUB false), so the test can tell the endpoint is actually reading each
property rather than echoing a hardcoded guess, plus cases for a source with a recorded
measurement, hub-sync's own last-activity source, and a source with no activity reporting
`null`. `BookApiIntegrationTest` covers creating two books, confirming the current one is the
latest and the list is newest-first, as one test method rather than several, since splitting
it across methods would hit the same shared-Postgres-per-class ordering risk the measurement
ingest tests already found; `rejectsABlankTitle` is a separate method since it writes nothing.
`BookApiNoBooksYetTest` is its own class (its own fresh Postgres container) just for the 404
case, so it isn't order-dependent on the other class's writes. `HubSyncServiceTest`'s two
current-book cases were updated to assert on `BookRepository` instead of
`CurrentReadingLogRepository`, same behavior, new target.
`ReadingNoteApiIntegrationTest` follows the same one-method-for-the-shared-state-sensitive-
cases pattern as `BookApiIntegrationTest`: one test posts an assessed note and an unassessed
one against two different books, then checks both the per-book and all-books list endpoints;
separate methods cover a blank `noteText`, an unknown `bookId`, and an out-of-range
`ownWordsPercentage`, none of which depend on what the first method already wrote. 125
backend tests, all passing.

Intelligence service: pytest. The suggestion rule (`choose_suggestion`), the pattern-
detection rule (`compute_patterns`), and the tally rule (`compute_tally`) are each tested
directly as pure functions against plain dicts/dates, no HTTP involved — all three take an
explicit `today` argument for exactly this reason, so date-window edge cases don't need the
system clock mocked. The FastAPI routes are tested with `TestClient` and `respx` mocking the
backend's HTTP responses. Phase 7's `assess_note.parse_assessment_response` gets the same
pure-function treatment as the other three rules (`test_assess_note.py`, 14 cases: a
well-formed response, no connected ideas or quoted phrases, stray prose around the JSON, each
required field missing in turn, an invalid `ownWordsPercentage` in four different bad shapes,
no JSON object at all, malformed JSON), and `POST /reading-notes/assess` is tested with
`TestClient` and `respx` mocking Anthropic's API the same way the backend's API is mocked
elsewhere: a successful call, no API key configured (503), and Anthropic itself returning an
error (502). One of these was also verified against the real Anthropic API during manual
testing (see the decisions log): a real key reached the real endpoint with the correct
request shape and got a real account-billing error back, which the 502 handling (added after
finding the unhandled-exception bug this surfaced) now reports cleanly instead of as an
opaque 500. 44 tests, all passing.

Frontend: Vitest, but only for `lib/schedule.ts`'s pure logic (which templates still need a
task, and computing a date's weekday independent of server timezone) — 5 tests, unchanged by
Phase 5's redesign or Phase 6's watering holes page since neither added new pure logic to the
frontend. Page components are verified by running the app and clicking through the views and
the full materialize → start → done flow in a browser instead, matching settlement-engine's
frontend, which has no automated test framework at all (see decisions log). `npm run lint` and
`npm run build` (strict TypeScript) also run as part of finishing a phase.

## Decisions log

| Date | Decision | Reason |
|------|----------|--------|
| 2026-10-17 | `assess_reading_note`'s automated tests fake Anthropic's API via `respx`, but it was also manually verified against the real API | Same offline/repeatable reasoning as every other faked external call in this project, but this one happened to have a real `ANTHROPIC_API_KEY` available in the session environment, so the real integration was checked too, not left purely theoretical. It surfaced a real bug (an unhandled `httpx.HTTPStatusError` producing an opaque 500), fixed and covered by a new test before moving on |
| 2026-10-17 | `BookApiIntegrationTest` covers creating two books, confirming the current one is the latest and the list is newest-first, as one test method rather than several | Splitting it across methods would hit the same shared-Postgres-per-class ordering risk the measurement ingest tests already found; `rejectsABlankTitle` is a separate method since it writes nothing and isn't order-sensitive |
| 2026-10-17 | `BookApiNoBooksYetTest` is its own test class, not a method in `BookApiIntegrationTest` | Its own class gets its own fresh Postgres container, so the 404-when-nothing-logged-yet case isn't order-dependent on what `BookApiIntegrationTest`'s other tests already wrote |
| 2026-10-17 | `hub-sync.enabled=false` is set once, on `AbstractIntegrationTest`, inherited by every subclass | `HubSyncService`'s `@Scheduled` job would otherwise fire inside any full `@SpringBootTest` context and make a real outbound GitHub/UMWAYI call during the suite; one shared property beats adding `@TestPropertySource` to each of the eight affected test classes individually |
| 2026-10-17 | `HubSyncServiceTest` fakes `GitHubContentClient` instead of calling the real GitHub API | The real API is rate-limited (60 requests/hour unauthenticated) and the test suite needs to run repeatedly and offline; the fake's fixed files and commit dates make the dedupe/double-count/missing-routine-item cases exact and deterministic, which a live call couldn't guarantee anyway |
| 2026-10-17 | `WakaTimeSyncServiceTest` fakes `WakaTimeClient` instead of calling the real WakaTime API | Same reasoning as `HubSyncServiceTest`'s fake `GitHubContentClient`: the real API needs a live key and real account data, neither of which belongs in a repeatable, offline test run; the fake's fixed daily totals make the upsert/double-log/zero-day cases exact |
| 2026-10-17 | `ActivityWatchSyncServiceTest` fakes `ActivityWatchClient` instead of calling a real `aw-server` | Same reasoning again, plus `aw-server` is a local process that may not even be running on the machine executing the test suite; the fake removes that dependency entirely |
| 2026-10-17 | `GitHubSyncServiceTest` fakes `GitHubCommitClient` instead of calling the real public events API | Same reasoning once more; also avoids the test suite's own commit activity (from running `./mvnw test` repeatedly during this very session) leaking into what's being asserted on |
| 2026-10-17 | `RescueTimeSyncServiceTest` fakes `RescueTimeClient`, and adds a test for the feature flag's own default | Same real-API/offline reasoning as the other three fakes; the flag test exists because this is the one sync where the default itself (`false`, not `true`) is the behaviour worth protecting, not just the sync logic behind it |
| 2026-10-17 | `MeasurementIngestControllerTest` asserts on `measurementRepository.count()` deltas and metric names unique per test, not `findAll()`'s exact size | Found as a real bug while writing this test: two cases asserting `findAll()` is empty failed because an earlier test method in the same `@SpringBootTest` class had already written a row, since this codebase's API tests share one Testcontainers Postgres instance per class with no per-method transaction rollback. A count delta is correct regardless of what other methods in the class already wrote |
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
- `WakaTimeClientImpl` has no automated test against the live WakaTime API either, same
  reasoning, and has not yet been checked manually against a real account (no `WAKATIME_API_KEY`
  available in this session) — unlike `GitHubContentClientImpl`, it is unverified against the
  real service, only against the fake in `WakaTimeSyncServiceTest`.
- `ActivityWatchClientImpl` has the same gap, and additionally has not been checked manually
  against a real `aw-server` (none running in this session) — unverified against anything but
  `ActivityWatchSyncServiceTest`'s fake.
- `GitHubCommitClientImpl` has no automated test either, but unlike the other two real clients
  it needs no credential and reads a public endpoint, so it was checked manually with `curl`
  against the real GitHub events API for the real `Miguel-Shinyenyi` account during this
  session (see the decisions log in `backend.md`), same verification `GitHubContentClientImpl`
  got in an earlier Phase 6 slice.
- `RescueTimeClientImpl` has the same gap and, like `WakaTimeClientImpl`, has not been checked
  manually against the real API — no `RESCUETIME_API_KEY` available in this session.
