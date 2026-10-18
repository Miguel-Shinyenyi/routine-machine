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
`ownWordsPercentage`, none of which depend on what the first method already wrote.
`ReadingNoteServiceTest` instantiates `ReadingNoteService` directly inside a `@DataJpaTest`,
same pattern as `TaskServiceTest`/`HubSyncServiceTest`: a note at or above the threshold
creates a `daily_log` for the `READER_NOTES` routine item; a note below the threshold, or
with no assessment at all, does not; posting two qualifying notes the same day creates only
one `daily_log` (the idempotency case, tested before the "no matching routine item" case,
matching the project's own "test the double-count case first" instruction); no `READER_NOTES`
routine item means no log but the note still saves.
`AuthServiceTest` (`@DataJpaTest`, `TaskServiceTest`-style direct instantiation) covers
registration (hashed password, duplicate-email rejection), login (right password, wrong
password, unknown email, GitHub-only account with no password), and `findOrCreateFromGithub`
(new account, idempotent repeat login, linking to an existing password account by email).
`AuthControllerApiIntegrationTest` covers the HTTP layer (register-then-login in one method,
duplicate email, a too-short password, `github-login` with and without the correct internal
token). `UserControllerApiIntegrationTest` is the one test in the suite that builds a real
signed JWT (via `nimbus-jose-jwt`, already on the classpath through the resource-server
starter) with the same shared secret every test context uses, rather than mocking Spring
Security's authentication context — proof the actual signature verification wiring works,
not just the authorization rule around it; also covers a missing token and one signed with
the wrong secret.
`UserScopedDataApiIntegrationTest` covers the optional `userId` scoping added to routine
items and daily logs: two registered users each get their own routine item and daily log,
and listing with `?userId=` returns only that user's own rows, not the other's; a third case
confirms an unknown `userId` on create fails. This is additive scoping, not a full auth test
suite — see `backend.md`'s decisions log for why.
`HillsidePseudonymTest` covers the pseudonym scheme as a pure function: deterministic for the
same id, different for different ids, and matches the "Adjective Noun 1234" shape exactly, no
digits or words leaking in from anywhere else. `HillsideServiceTest` (`@DataJpaTest`) covers
the real aggregation against real rows: a user with fewer qualifying days ranks behind one
with more; a private routine item's logs never reach the hillside at all (zero participants);
omitting `asUserId` returns the histogram with no personal band; the "general" field only
counts a day once 3+ distinct routines were tended, not just one; and (added for the
frontend's rank-number display) a rival who outranks you gets `rank: 1` while you get `rank:
2`, the row's real 1-indexed position in the full ranked list, not its position within the
returned neighbours slice.
`HillsideApiIntegrationTest` covers the HTTP layer.
`GoalApiIntegrationTest` covers `POST /api/goals` (Phase 8's goal user-scoping): creating a
goal for a registered user and returning its `userId`, creating one with no `userId` and
getting `null` back, 400 on a missing name and on an unknown `userId`, 400 on a second goal
with the same name for the same user (the new `(user_id, name)` uniqueness), and that
`?userId=` on the list endpoint isolates one user's goals from another's.
`HillsideServiceTest` gained a case proving a user who hasn't opted in never reaches the
hillside even with a qualifying public log; its other cases' `saveUser` helper now opts the
user in, since that's the real precondition for participating, not an incidental default.
`UserControllerApiIntegrationTest` covers `PUT /api/users/me/hillside-opt-in`: opting in,
opting out, and that the endpoint 401s with no token, same as `GET /api/users/me`. 169 backend
tests, all passing.

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
opaque 500. `test_suggestion_ignores_goals_and_logs_owned_by_a_hillside_demo_user` proves the
`_unowned()` filter works: a goal/log pair with `userId` set must not change `/suggestion`'s
`lastLoggedOn`, a case chosen because it would silently pick the wrong (more recent) date if
the filter were removed, not just return extra rows.
`test_since_start_windows_from_the_earliest_log_not_a_fixed_calendar_window` and
`test_since_start_with_no_logs_at_all_windows_from_today` cover `compute_tally`'s
`since_start` option: a deliberately tiny `window_days` is passed alongside `since_start=True`
to prove it's ignored once set, and the no-logs case confirms the window still has a sane
(single-day) fallback rather than exploding. 47 tests, all passing.

Frontend: Vitest, for pure logic only — `lib/schedule.ts` (which templates still need a task,
a date's weekday independent of server timezone), `lib/formatDate.ts` and `lib/weekDates.ts`
(Phase 8's design-fidelity pass: an eyebrow date, and a week's Monday/seven dates, both
UTC-based for the same server-timezone-independence reason `schedule.ts` already established),
`lib/markQuotes.ts` (splitting a note's text around its quoted phrases for inline `<mark>`
rendering), `lib/tallyInsights.ts` (`steepDayInsight`/`learningInsight`, the real
computations behind Tally's "Steep days"/"Learning and the rest" headlines: which weekday a
user's skip-heavy days cluster on, and whether learning-day routine completions are higher,
lower, or tied against other days), and `lib/auth/jwt.ts` (`signSessionToken`/
`verifySessionToken`: a round trip, expiry just past and just before the cutoff, a token
signed with the wrong secret, a tampered payload, and garbage input) — 29 tests total.
`submitReadingNote` stays untested as a unit, since it's
a sequence of two external calls, not a pure function worth isolating. Page components are
verified by running the app and clicking through the views and the full materialize → start
→ done flow in a browser instead, matching settlement-engine's frontend, which has no
automated test framework at all (see decisions log). `npm run lint` and `npm run build`
(strict TypeScript) also run as part of finishing a phase. Reading's full flow (create a
book, submit a note, see it rendered back), Path's week navigation, Flock's routine-item
rows, Tally's grouped notches, and the hillside's field/window switching and real computed
band were all verified live via Docker Compose against real seeded data, not just the
automated suite. Reading's live check included a real call through to
running the app and clicking through the views and the full materialize → start → done flow
in a browser instead, matching settlement-engine's frontend, which has no automated test
framework at all (see decisions log). `npm run lint` and `npm run build` (strict TypeScript)
also run as part of finishing a phase. Reading's full flow (create a book, submit a note, see
it rendered back) was verified live via Docker Compose, including a real call through to the
Anthropic API (which failed on account billing, caught and shown as "wasn't assessed," not a
page crash) — the same real-API verification the intelligence service's own tests already
recorded, now confirmed end to end through the browser too.

## Decisions log

| Date | Decision | Reason |
|------|----------|--------|
| 2026-10-17 | `UserControllerApiIntegrationTest` builds a real signed JWT with `nimbus-jose-jwt` instead of using Spring Security's test mock-authentication support | Spring Security's `SecurityMockMvcRequestPostProcessors.jwt()` injects an authenticated principal directly, bypassing real token parsing and signature verification entirely; that's the right tool for testing authorization rules, but this phase needed at least one test proving the actual HS256 verification against the real shared secret works, since that's the part a hand-wired `JwtDecoder` bean could get subtly wrong |
| 2026-10-17 | Adding Spring Security surfaced a real bug, caught by running the full suite, not by a new test written in advance | A single `SecurityFilterChain` with `oauth2ResourceServer().jwt()` broke five unrelated, already-passing tests (`MeasurementIngestControllerTest`'s and `AuthControllerApiIntegrationTest`'s own `Authorization: Bearer` checks) by intercepting their app-level tokens as failed JWTs. Running `./mvnw clean test` immediately after wiring in a new cross-cutting concern like security is exactly the kind of check that catches this before it reaches a report |
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
| 2026-10-18 | `lib/auth/jwt.ts` is unit-tested with Vitest, not covered only by the live browser login check | It's a pure function (sign/verify) with real edge cases — expiry boundary, wrong secret, tampered payload, garbage input — exactly the kind of logic `schedule.ts`'s other pure functions already get fast, deterministic tests for, separate from the slower end-to-end browser verification of the full login flow |
| 2026-10-18 | Password login's register → login → logout round trip (plus the wrong-password error path) was verified live in a real browser against a rebuilt Docker Compose stack, not just unit tests | `lib/auth/jwt.ts`'s tests cover the token logic in isolation but not the Server Actions, the cookie actually being set/cleared, or the redirect targets; a live click-through is the only check that exercises the whole path end to end |

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
