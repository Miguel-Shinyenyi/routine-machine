# Database

## Purpose

PostgreSQL schema for routine items, daily logs, learning topics, and goals. Shared by both
services.

## Current state

Built via two Flyway migrations. Four Phase 1 tables:

- `goals(id, name unique, description, created_at)` — seeded with the three tracked goals
  (`cmu-masters-application`, `target-roles`, `ai-engineer-evidence`) in `V2__seed_goals.sql`.
- `routine_items(id, name, description, created_at)`
- `learning_topics(id, name, goal_id references goals, created_at)`
- `daily_logs(id, log_date, routine_item_id references routine_items nullable,
  learning_topic_id references learning_topics nullable, source, created_at)` — a check
  constraint enforces exactly one of `routine_item_id`/`learning_topic_id` is set, and another
  restricts `source` to `manual` or `hub-sync`. The JPA entity's factory methods
  (`DailyLog.forRoutineItem` / `forLearningTopic`) enforce the same rule at the application
  layer, so the constraint is defense in depth against anything writing to the table outside
  the entity's own API.

`created_at` on every table is a DB-side default (`now()`), read back into the entity after
insert via Hibernate's `@Generated(event = EventType.INSERT)` rather than set from the
application, so it always reflects when Postgres actually wrote the row.

## Decisions log

| Date | Decision | Reason |
|------|----------|--------|
| 2026-09-18 | Goals are seeded via migration, not created through the API | The three goals are fixed by the project's own structure (see PROJECT.md), not user-editable data; a migration is the current, reasonable place for fixed reference data |
| 2026-09-18 | Exactly-one-target enforced both in the DailyLog entity's factory methods and as a DB check constraint | The entity API can't construct an invalid instance, but the DB constraint guards against any future write path (e.g. a hub-sync script) that bypasses it |
| 2026-09-18 | DailyLog gets a source field (manual vs hub-sync) | Keeps provenance visible; a hub-synced completion and a manually logged one shouldn't be indistinguishable in the data |
| 2026-09-18 | Four Phase 1 tables: routine_items, learning_topics, daily_logs, goals | Matches the Phase 1 requirement exactly, no speculative tables for Phase 2 or 3 logic |
| 2026-09-18 | No user table, no auth | Single user, personal tool, see PROJECT.md; revisit only if this needs to be shared |
| 2026-09-18 | No edit history or versioning on daily_logs | Adds complexity with no current justification; revisit if incorrect logs become a real recurring problem |

## Open questions

- None currently open for Phase 1.
