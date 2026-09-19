# Database

## Purpose

PostgreSQL schema for routine items, daily logs, learning topics, goals, and the fixed
schedule. Shared by both services.

## Current state

Built via two Flyway migrations. Four Phase 1 tables:

- `goals(id, name unique, description, created_at)`, seeded with the three tracked goals
  (`cmu-masters-application`, `target-roles`, `ai-engineer-evidence`) in `V2__seed_goals.sql`.
- `routine_items(id, name, description, created_at)`
- `learning_topics(id, name, goal_id references goals, created_at)`
- `daily_logs(id, log_date, routine_item_id references routine_items nullable,
  learning_topic_id references learning_topics nullable, source, created_at)`, a check
  constraint enforces exactly one of `routine_item_id`/`learning_topic_id` is set, and another
  restricts `source`. The JPA entity's factory methods (`DailyLog.forRoutineItem` /
  `forLearningTopic`) enforce the same rule at the application layer, so the constraint is
  defense in depth against anything writing to the table outside the entity's own API.

`created_at` on every table is a DB-side default (`now()`), read back into the entity after
insert via Hibernate's `@Generated(event = EventType.INSERT)` rather than set from the
application, so it always reflects when Postgres actually wrote the row.

Phase 3 built via `V3__schedule_and_reading.sql`. `daily_logs.source` now also accepts
`schedule` (its check constraint was dropped and recreated with the third value). Three new
tables:

- `schedule_templates(id, day_of_week, target_type ('ROUTINE_ITEM' | 'LEARNING_SLOT'),
  routine_item_id references routine_items nullable, target_duration_minutes, sort_order,
  label, created_at)`, the recurring weekly definitions. A check constraint enforces
  `routine_item_id` is set exactly when `target_type = 'ROUTINE_ITEM'`.
- `tasks(id, schedule_template_id references schedule_templates nullable, task_date, label,
  routine_item_id references routine_items nullable, learning_topic_id references
  learning_topics nullable, status ('TODO' | 'IN_PROGRESS' | 'DONE'), daily_log_id references
  daily_logs nullable, created_at)`. `schedule_template_id` nullable for ad-hoc one-off tasks.
  `label`, `routine_item_id`, and `learning_topic_id` weren't in the original plan sketched
  below (which had only `schedule_template_id`, `task_date`, `status`, `daily_log_id`) — see
  the decisions log for why they were added once implementation exposed the gap.
- `current_reading_log(id, title, source ('manual' | 'hub-sync'), created_at)`, append-only,
  the latest row by `created_at` is the current book. No `finished_at` or status field, a new
  row simply supersedes the last.

## Decisions log

| Date | Decision | Reason |
|------|----------|--------|
| 2026-09-19 | `tasks` also carries its own `label`, `routine_item_id`, and `learning_topic_id`, not just `schedule_template_id` | A `LEARNING_SLOT` template doesn't fix which topic applies — that's resolved per day (see `schedule.md`'s frontend-orchestration decision) — so it has to live on the task, not the template. Once `learning_topic_id` was needed there anyway, giving ad-hoc tasks (no template) the same optional target columns plus their own `label` was the direct extension, not a special case |
| 2026-09-18 | `daily_logs.source` gains a third value, `schedule` | A task completed through the Phase 3 board is a distinct origin from a manually logged entry or a hub-sync-detected one, and provenance should stay visible per the original source-field decision below |
| 2026-09-18 | Two new tables, `schedule_templates` and `tasks`, rather than extending `daily_logs` | `daily_logs` is an append-only record of completed facts; a task has mutable state (`TODO` to `DONE`) before it becomes one, which doesn't fit an append-only table |
| 2026-09-18 | Goals are seeded via migration, not created through the API | The three goals are fixed by the project's own structure (see PROJECT.md), not user-editable data; a migration is the current, reasonable place for fixed reference data |
| 2026-09-18 | Exactly-one-target enforced both in the DailyLog entity's factory methods and as a DB check constraint | The entity API can't construct an invalid instance, but the DB constraint guards against any future write path (e.g. a hub-sync script) that bypasses it |
| 2026-09-18 | DailyLog gets a source field (manual vs hub-sync, later schedule) | Keeps provenance visible; completions from different origins shouldn't be indistinguishable in the data |
| 2026-09-18 | Four Phase 1 tables: routine_items, learning_topics, daily_logs, goals | Matches the Phase 1 requirement exactly, no speculative tables for Phase 2 or 3 logic |
| 2026-09-18 | No user table, no auth | Single user, personal tool, see PROJECT.md; revisit only if this needs to be shared |
| 2026-09-18 | No edit history or versioning on daily_logs | Adds complexity with no current justification; revisit if incorrect logs become a real recurring problem |
| 2026-09-19 | `current_reading_log` is append-only with no status field | The current book is always just the latest row; a status field would duplicate what ordering already tells you |

## Open questions

- None currently open for Phase 3.