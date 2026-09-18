# Testing

## Purpose

Test strategy and coverage rules for both services.

## Current state

Not started. Standing rule from `PROJECT.md`: TDD is not optional, the failing test comes
before the implementation for every new piece of logic, in both the backend and the
intelligence service.

## Decisions log

| Date | Decision | Reason |
|------|----------|--------|
| 2026-09-18 | TDD required for both services from the first line of code | Stated non-negotiable in PROJECT.md's rules section, not left to convention |

## Open questions

- Whether integration tests need a real Postgres (Testcontainers, matching settlement-engine's
  approach) or whether a lighter setup is justified for a single-user personal tool. Decide
  when Phase 1 implementation starts.
