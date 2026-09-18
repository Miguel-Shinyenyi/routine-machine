# Backend

## Purpose

Spring Boot service owning the data: routine items, daily logs, learning topics, and goals.
Exposes a REST API. No suggestion or pattern-detection logic lives here, that belongs to
`intelligence-service.md`.

## Current state

Not started.

## Decisions log

| Date | Decision | Reason |
|------|----------|--------|
| 2026-09-18 | Data layer only, no intelligence logic in this service | Keeps the data model stable while the intelligence service evolves independently, same separation settlement-engine uses between core and ml-service |

## Open questions

- Exact endpoint list for Phase 1, to be filled in when Phase 1 implementation starts.
