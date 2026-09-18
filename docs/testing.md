# Testing

## Purpose

Test strategy and coverage rules for both services.

## Current state

Backend: JUnit 5 + AssertJ. Repository tests use `@DataJpaTest` against a real Postgres via
Testcontainers (`AbstractIntegrationTest`), not an in-memory substitute, since Flyway
migrations and Postgres-specific check constraints need to run against the real engine. API
tests use `@SpringBootTest` + `MockMvc` against the same Testcontainers Postgres. A pure unit
test (`DailyLogTest`) covers the entity's exactly-one-target invariant without needing a
database at all. 22 backend tests, all passing.

Intelligence service: pytest. The suggestion rule (`choose_suggestion`) is tested directly as
a pure function against plain dicts, no HTTP involved. The FastAPI route is tested with
`TestClient` and `respx` mocking the backend's HTTP responses. 8 tests, all passing.

## Decisions log

| Date | Decision | Reason |
|------|----------|--------|
| 2026-09-18 | Every list endpoint gets an API-level test asserting on the response body, not just the create endpoint | A real bug (`GET /api/learning-topics` 500ing on a lazy `goal` proxy) shipped past a full green test suite because only the POST path had a body assertion; manual end-to-end testing against the running Docker Compose stack caught it, TDD alone didn't |
| 2026-09-18 | Integration tests use Testcontainers Postgres, not H2 or mocks | Flyway migrations and the daily_logs check constraints are Postgres-specific; a lighter substitute wouldn't actually test them, matching settlement-engine's approach |
| 2026-09-18 | Suggestion logic is unit-tested as a pure function, HTTP layer tested separately with respx | Keeps the rule's test cases fast and readable, and isolates HTTP mocking to the one test that needs it |
| 2026-09-18 | TDD required for both services from the first line of code | Stated non-negotiable in PROJECT.md's rules section, not left to convention |

## Open questions

- None currently open for Phase 1.
