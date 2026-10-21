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
same id within the same season, different for different ids, matches the "Adjective Noun 1234"
shape exactly with no digits or words leaking in from anywhere else, and two new cases for
rotation — the same id gets a different pseudonym once a 90-day season boundary has passed,
and stays the same pseudonym for dates still inside the same season. `HillsideServiceTest` (`@DataJpaTest`) covers
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
opting out, and that the endpoint 401s with no token, same as `GET /api/users/me`.
`EvidenceCreditServiceTest` (`@DataJpaTest`) covers the evidence-to-credit pipeline's own
test list from `docs/product-direction.md`: a duplicate event earns no second credit; two
concurrent inserts for the same user/goal/day produce one row (a dedicated test, real raw-JDBC
concurrency against two separate connections — see the decisions log for why `@DataJpaTest`'s
default per-test transaction had to be disabled for this one case); a missed day subtracts
nothing; the daily cap (3) holds across four qualifying goals in one day; a credit decided on
Nairobi time near midnight lands on the correct local date; evidence below/at/above a minute
threshold is rejected/accepted; manual evidence is self-reported and still earns credit; a
goal with no scoring rule falls back to presence-based acceptance; evidence with no minutes
against a minute-based rule is pending. `HillsideV2ServiceTest` and `HillsideV2ComputeTest`
cover season-score aggregation and ranking: a return is counted only after a gap and never on
a user's first-ever credited day; ranking by season score; a non-opted-in user is never a
participant; filtering by goal only scores that goal's credits; cohort-wide returns count
every participant with at least one comeback, not just yours. `EvidenceEventApiIntegrationTest`
and `HillsideV2ApiIntegrationTest` cover the HTTP layer: ingest creates an accepted event with
no rule present, an unknown user 400s, the ledger lists every event in a window whatever its
status, questioning a credit records the request, and `/api/hillside/v2` returns a real
band/rank for an explicit season and no personal band for a user with no credits yet. 193
backend tests, all passing (171 plus 22 new for Phase 9).
`EvidenceCreditServiceTest` gained two Phase 10 cases: evidence just before the user's
configured `dayEndHour` still counts for the previous calendar day, and evidence at or after
it counts for the new one. `UserControllerApiIntegrationTest` covers `GET`/
`PUT /api/users/me/profile`: a new user gets the documented defaults (`dayEndHour: 0`,
`timeZone: "UTC"`, `analogiesEnabled: true`), updating sends only the fields that changed and
leaves the rest alone (two sequential `PUT`s, the second touching only `analogiesEnabled`,
confirms `interests` from the first call survived), an out-of-range `dayEndHour` (24) 400s, and
both endpoints 401 with no token. `HillsideV2ApiIntegrationTest.profileDataNeverAppearsInTheHillsideResponse`
is Phase 10's explicit "write a test that proves it" privacy requirement: sets distinctive
marker strings on every profile field, calls `GET /api/hillside/v2`, and asserts none of the
markers appear anywhere in the raw response body — not just that the response type doesn't
name those fields, which would be true by construction and wouldn't actually catch a future
regression that accidentally serialized the whole `User`. 200 backend tests, all passing (193
plus 7 new for Phase 10).
`GranaryServiceTest` covers the granary's own logic: storing twice the same day doesn't
duplicate it; a missed day subtracts nothing; the month's target is the daily amount times
days *elapsed* so far, not the whole month; the jar has exactly one entry per elapsed day;
`totalStoredEver` includes savings from other months; creating a pause sets both follow-up
dates; the 24-hour follow-up is due only once its time has passed and the pause is still
undecided, and stops being due once decided; the 30-day follow-up is due independently of
whether the pause was decided, and stops once reflected on; `pausesDueForFollowUp` finds only
one user's own due pauses, not another user's. `GranaryApiIntegrationTest` covers the HTTP
layer: setting the daily amount then storing updates the summary, storing twice the same day
doesn't double it, storing with no amount set 400s, creating a pause then deciding then
reflecting updates its state end to end, and deciding an unknown pause 404s.
`HillsideV2ApiIntegrationTest` gained `granaryDataNeverAppearsInTheHillsideResponse`, the same
marker-string technique as Phase 10's profile privacy test, for Phase 11's explicit "test it"
boundary. 214 backend tests at that point.
M-Pesa statement import added `MpesaStatementParserTest` (7 cases, a pure function against
plain text in the documented transaction-row shape, never a real statement per the task's
explicit instruction): sums withdrawn amounts by category; Paid In never counts; unmatched
details fall into OTHER; the BETTING/DATA_AND_AIRTIME/LEARNING/OVERDRAFT_FEES keyword sets;
how many transactions were parsed; the statement month is derived from the first transaction's
date; and text with no recognisable rows at all fails loudly (`MpesaStatementParseException`)
rather than returning empty totals. `MpesaPdfReaderTest` (5 cases) builds real PDF fixtures
with PDFBox itself — an unprotected PDF, a password-protected one with the right password, one
with no password given, one with the wrong password, and bytes that aren't a PDF at all —
proving the actual text-extraction and decryption path, not just the parser's string handling.
`GranaryServiceTest` gained 4 cases covering the full import pipeline (persists only category
totals, re-importing the same month replaces rather than adds, the password-protected path
with the right password, and with no password at all). `GranaryApiIntegrationTest` gained 2
multipart-upload cases (`MockMultipartFile`) covering a successful import and persistence, and
a clear `"type": "PARSE_ERROR"` on an unrecognisable statement.
`HillsideV2ApiIntegrationTest.granaryDataNeverAppearsInTheHillsideResponse` was extended to
also set a saving target amount and store a saving with a distinctive marker value, proving
that leaks through the same hillside-privacy boundary too. 232 backend tests, all passing (214
plus 18 new for the M-Pesa import slice).

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
Phase 10's `/analogy` gets the same pure-function/route split: `analogy.parse_analogy_response`
(`test_analogy.py`, 7 cases: a well-formed response, stray prose tolerated, each required field
missing in turn, no JSON object at all, malformed JSON, and the user-message builder names both
the topic and the interest) and `POST /analogy` itself tested with `TestClient`/`respx`: a
successful call, cached per `(topic, interest)` case-insensitively and whitespace-trimmed
(asserts the mocked Anthropic route was hit exactly once across three calls, two of them
differently-cased/spaced versions of the same pairing), no API key configured (503), and
Anthropic responding with unparseable content (502). One of these was also verified against the
real Anthropic API during manual testing, the same account-billing error `/reading-notes/assess`
already hit in Phase 7, caught cleanly as a 502 rather than a crash. 58 tests, all passing.

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
signed with the wrong secret, a tampered payload, and garbage input) — 29 tests total (Phase 9
added no new pure-logic module: `seasonNumber` and `evidenceDescription` in `/hillside`'s page
are small presentational helpers, same tier as the already-untested `formatDay` in the same
file, not isolated business logic; Phase 10 added no new pure-logic module either — `/profile`
and Today's analogy wiring are both plain data-fetch-and-render, verified live instead, same
as every other page; Phase 11's `/granary` is the same — all the real logic, month targets,
the jar, due-follow-up checks, and the M-Pesa parsing/categorisation, lives on the backend,
already covered by `GranaryServiceTest`/`MpesaStatementParserTest`/`MpesaPdfReaderTest`; the
frontend's `MpesaImportForm` is a thin multipart-upload form with no logic of its own beyond
reading the backend's error `type` field to decide which message to show). `submitReadingNote`
stays untested as a unit, since it's
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
recorded, now confirmed end to end through the browser too. Phase 9's hillside v2 was verified
live via Docker Compose against real evidence events posted through `POST /api/evidence-events`:
the band/returns/neighbours cards, the goal filter, the show/hide switch (both directions),
and questioning a credit (confirmed it persists across a reload) all checked in a browser, plus
`docs/design-reference/compare.mjs` run against `/hillside` and `/hillside` @390 — the
comparison surfaced three real structural gaps (no season number, the hide control was a text
link instead of the reference's toggle switch, the ledger was missing its Evidence/Credit
columns), fixed and re-compared before reporting. Phase 10's Profile page and Today's analogy
wiring were verified live via Docker Compose: signed in, toggled an interest chip and set a
time zone on Profile, saved, confirmed both persisted on reload; confirmed Today's staff card
reaches the real `/analogy` endpoint (visible in the intelligence service's own logs and via a
direct `curl`) without crashing the page when the call fails — which it did, on the same
account-billing issue already documented for `/reading-notes/assess`. This surfaced a real
deployment bug, not a logic bug: the intelligence service's Docker image crash-looped with
`ModuleNotFoundError: No module named 'analogy'` because its `Dockerfile` copies an explicit
file list that `analogy.py` hadn't been added to — caught by the live verification step, not by
`pytest` (which runs against the real filesystem, not the built image), fixed, and the
container confirmed actually starting before reporting. Phase 11's granary was verified live
via Docker Compose: set a daily amount, tapped "store," confirmed the stats/jar/target updated
correctly; logged a pause decision, confirmed both "Wait 24 hours" and "I've already decided"
land on the same list with working Decline/Proceed controls regardless of the 24-hour timer,
confirmed a decision persists on reload; confirmed the phone tab bar now matches the updated
design reference exactly (Today/Reading/Granary/Tally/Hillside) via `compare.mjs`.
Phase 11's M-Pesa statement import was verified live via Docker Compose with real
PDFBox-generated fixtures (never a real statement): an unprotected statement imported
and the "Where it went" category totals rendered correctly; a password-protected
statement submitted with no password showed the correct inline "password-protected"
error; the same statement resubmitted with the correct password imported successfully
and cleared the form. The first "correct password still fails" result was a browser-automation
testing mistake (a stale click coordinate landing on text below the Import button after the
error message had pushed it down), not an application bug, confirmed by a direct `curl` of
the same request succeeding throughout.

## Decisions log

| Date | Decision | Reason |
|------|----------|--------|
| 2026-10-21 | Real test runs use `./mvnw clean test` while VS Code's Java language server is running | The language server (Red Hat Java extension) compiles into `backend/target/classes` with the Eclipse compiler. A source error becomes an `Unresolved compilation problem` stub class that Maven treats as up to date, so `./mvnw -q compile` printed nothing and the error only appeared when a request hit the class. Found in per-user scoping part 2b; `clean` makes Maven compile from source and show the real error |
| 2026-10-21 | Cohort tests that depend on `joined_at` or a goal's `created_at` pin both with a native `UPDATE`; `activationDoesNotDependOnTheRealDate` runs the scenario for 2031 and 2019 | Both are `DEFAULT now()` and not settable through the entities. A test with a literal date passes only on the day it was written: `checkedInThisWeekIsOutOfActivatedMembersOnly` passed on 2026-10-19 and failed on 2026-10-21, found by Stage 1's read-only run (which also found the weekly-chart API test would have broken on 2026-12-07). The earlier "anchor to today" fix had only hidden it |
| 2026-10-21 | The owner-claim tests assert "no NULL rows left" and per-row ownership, not exact row counts | The seed migrations leave unowned rows (goals, routine items) in the test database, so a fixed count would break whenever a seed changes. The tests were also checked by mutation: dropping `books` from the claim list makes two of them fail |
| 2026-10-18 | `twoConcurrentInsertsForTheSameUserGoalAndDayProduceOneRow` disables `@DataJpaTest`'s default per-test transaction (`@Transactional(propagation = NOT_SUPPORTED)`) and uses two raw JDBC connections from the test's own `DataSource` bean | `@DataJpaTest` wraps each test method in one transaction, rolled back at the end — the setup rows (user, goal, evidence events) would stay uncommitted and invisible to two separate connections racing against them, which would make this "concurrency" test pass or fail for the wrong reason (a foreign-key violation on invisible rows, not a real test of the `daily_credits` unique-constraint race). Disabling the wrapper makes setup commit for real, so the two threads' raw `INSERT ... ON CONFLICT DO NOTHING` statements are a genuine concurrent-commit test against Postgres |
| 2026-10-18 | That same test surfaces each racing thread's exception via `Future.get()` instead of a bare `pool.submit(Runnable)` | First version of this test passed when it shouldn't have: a foreign-key violation inside the thread was silently swallowed (no `.get()` call to surface it), so the assertion saw 0 rows and failed for an unrelated reason before this was caught and fixed. A test that can pass for the wrong reason is worse than no test; `Future.get()` makes a racing thread's exception fail the test loudly |
| 2026-10-18 | `EvidenceEventApiIntegrationTest`/`HillsideV2ApiIntegrationTest` don't build a signed JWT the way `UserControllerApiIntegrationTest` does | The new endpoints (`/api/evidence-events`, `/api/hillside/v2`) aren't behind the JWT-protected `/api/users/**` filter chain (see `SecurityConfig`) — same `permitAll` pattern `/api/hillside` v1 already uses, so there's no auth layer for these tests to exercise |
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
| 2026-10-18 | Hillside's real-session wiring and opt-in toggle were verified live (signed-in user sees "Join the hillside", clicking it flips to "Leave the hillside" and the band card's messaging updates, clicking again reverts, signed out reverts to the demo-mode `?asUserId=` message), no new automated test for this frontend wiring | It's a thin Server Action (`setHillsideOptIn`) plus conditional JSX already covered structurally by the backend's own `PUT /api/users/me/hillside-opt-in` tests; a live click-through proves the frontend calls it correctly and `revalidatePath` actually refreshes the page, which a unit test of the action alone wouldn't catch |

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

## Signing in inside tests

Routes that take their user from the token (see `docs/backend.md`, access control) are called with `.with(TestTokens.asUser(userId))` (`backend/src/test/java/com/routinemachine/core/TestTokens.java`). It sets the token a signed-in request would carry, without a real signature. A request with no `.with(...)` is a signed-out request and gets 401 on those routes.
