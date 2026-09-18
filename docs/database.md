# Database

## Purpose

PostgreSQL schema for routine items, daily logs, learning topics, and goals. Shared by both
services.

## Current state

Not started. Planned entities for Phase 1: `RoutineItem`, `LearningTopic`, `DailyLog`,
`Goal`. See the Decisions log below for what each holds and why.

## Decisions log

| Date | Decision | Reason |
|------|----------|--------|
| 2026-09-18 | Four Phase 1 tables: routine_items, learning_topics, daily_logs, goals | Matches the Phase 1 requirement exactly, no speculative tables for Phase 2 or 3 logic |
| 2026-09-18 | No user table, no auth | Single user, personal tool, see PROJECT.md; revisit only if this needs to be shared |
| 2026-09-18 | No edit history or versioning on daily_logs | Adds complexity with no current justification; revisit if incorrect logs become a real recurring problem |

## Open questions

- Exact column types and constraints, to be finalized when the first migration is written.
