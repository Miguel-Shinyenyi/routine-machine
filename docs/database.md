# Database

## Purpose

PostgreSQL schema for routine items, daily logs, learning topics, goals, and the fixed
schedule. Shared by both services.

## Current state

Built via two Flyway migrations. Four Phase 1 tables:

- `goals(id, name, description, user_id nullable, created_at)`, seeded with the three tracked
  goals (`cmu-masters-application`, `target-roles`, `ai-engineer-evidence`) in
  `V2__seed_goals.sql` (`user_id` added in Phase 8's `V16__goal_user_scoping.sql`, see below;
  unique on `(user_id, name)`, not `name` alone).
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

Phase 5 built via `V4__phase5_categorisation_and_hours.sql`:

- `routine_items` gains `category` (nullable, `BODY`/`MIND`/`WORK`/`HOME`, check-constrained,
  no default: existing items stay uncategorised until assigned through Flock), `count_rule`
  (nullable text, informational, no constraint), `source` (not null, defaults `MANUAL`,
  check-constrained to `MANUAL`/`HUB`/`WAKATIME`/`ACTIVITYWATCH`/`HEALTH`/`READER_NOTES`/
  `GITHUB`/`STRAVA`), `hillside_field` (nullable, check-constrained to
  `READING`/`MOVEMENT`/`DEEP_WORK`/`LEARNING`/`SLEEP`/`WRITING`), `private` (not null,
  defaults `true`), and `goal_id` (nullable, references `goals`, indexed).
- `daily_logs` gains `measured_minutes` (nullable int), rolled up by the intelligence
  service's `/tally` into cumulative hours per goal.

Phase 6 built via `V5__hub_sync_processed_files.sql`:

- `hub_sync_processed_files(id, repo_path, content_sha unique, kind ('JOURNAL' | 'ARTICLE'),
  file_date, created_at)`, one row per UMWAYI file hub-sync has already turned into a
  `daily_log`, keyed by git blob sha so a renamed file (same content, new path) isn't
  double-counted. See `hub-sync.md`.

Phase 6 also built via `V6__measurements.sql`:

- `measurements(id, source ('WAKATIME' | 'ACTIVITYWATCH' | 'GITHUB' | 'RESCUETIME' | 'HEALTH'),
  metric, measurement_date, value numeric, created_at, updated_at)`, unique on
  `(source, metric, measurement_date)`. One row per source-and-metric per day; `value` is
  overwritten in place on a repeat sync for the same day rather than a new row being inserted.
  Holds the raw numbers each sync job pulls from a watering hole (coding minutes so far,
  active minutes, commit counts, health readings) before any of it is turned into a
  `daily_log`. See `backend.md`.

Phase 6 also built via `V7__wakatime_daily_log_source.sql`: `daily_logs.source` gains a fourth
value, `wakatime`, for a log written by `WakaTimeSyncService` (see `backend.md`).

Phase 6 also built via `V8__activitywatch_daily_log_source.sql`: `daily_logs.source` gains a
fifth value, `activitywatch`, for a log written by `ActivityWatchSyncService` (see
`backend.md`).

Phase 6 also built via `V9__github_daily_log_source.sql`: `daily_logs.source` gains a sixth
value, `github`, for a log written by `GitHubSyncService` (see `backend.md`). This log never
carries `measured_minutes`, since a commit count isn't a duration.

Phase 6 also built via `V10__rescuetime.sql`: `routine_items.source` gains `RESCUETIME` (it
was missing from `MeasurementSource`'s equivalent until now, flagged as a gap when
`MeasurementSource` was introduced) and `daily_logs.source` gains a seventh value,
`rescuetime`, for a log written by `RescueTimeSyncService`. Unlike the other three syncs,
RescueTime is optional and off by default — see `backend.md`'s decisions log.

Phase 7 built via `V11__books.sql`:

- `books(id, title, author, current_chapter, total_chapters, source ('manual' | 'hub-sync'),
  created_at)`. Supersedes `current_reading_log` as the write target for "what am I reading";
  `current_reading_log` keeps its existing rows but gets no new ones from hub-sync as of this
  phase — see `backend.md`'s and `hub-sync.md`'s decisions logs. No `chapters` table: a book's
  progress is two plain integers, not a maintained catalog of chapter rows (see `backend.md`).

Phase 7 also built via `V12__reading_notes.sql`:

- `reading_notes(id, book_id references books, note_text, chapter_placed, own_words_percentage,
  ideas_connected, quoted_phrases, question_back, created_at)`, indexed on `book_id`. A check
  constraint keeps `own_words_percentage` within 0-100 when present. `chapter_placed`,
  `own_words_percentage`, `ideas_connected`, `quoted_phrases`, and `question_back` are all
  nullable: the intelligence service's assessment (see `intelligence-service.md`) is optional,
  not a precondition for saving a note. `ideas_connected`/`quoted_phrases` hold JSON-encoded
  string arrays in a TEXT column rather than their own tables — see `backend.md`'s decisions
  log.

Phase 7 also built via `V13__reader_notes_daily_log_source.sql`: `daily_logs.source` gains an
eighth value, `reader-notes`, for a log `ReadingNoteService` writes when a note's
`own_words_percentage` clears the named threshold (70, see `backend.md`). This log never
carries `measured_minutes` — a written reflection has no duration to report, same reasoning
`GitHubSyncService`'s commit-count logs already apply.

Phase 8 built via `V14__users.sql`: `users(id, email unique, password_hash, github_id unique,
display_name, created_at)`, a check constraint requires at least one of `password_hash`/
`github_id`. See `backend.md`'s decisions log for the three accounts/auth decisions this
table is built on.

Phase 8 also built via `V15__user_scoped_data.sql`: `routine_items` and `daily_logs` each
gain a nullable `user_id` (indexed), referencing `users`. `NULL` means legacy/unowned — every
row written before this phase, and everything every Phase 6 sync service writes, stays
exactly that way. `books`, `reading_notes`, `measurements`, and the rest still have no
`user_id` at all. See `backend.md`'s decisions log for why these tables first, and why
nullable/optional rather than enforced.

Phase 8's hillside (`GET /api/hillside`, see `backend.md`) added no new table: it's computed
fresh from `daily_logs`/`routine_items`/`users` on every call, nothing is stored or cached.

Phase 8 also built via `V16__goal_user_scoping.sql`: `goals` gains a nullable `user_id`
(indexed), referencing `users`. `NULL` still means the three seeded goals, shared and visible
to everyone, exactly as before. The old global `UNIQUE(name)` constraint
(`goals_name_key`) is replaced by `UNIQUE(user_id, name)` (`goals_user_id_name_unique`):
two different users, or a user and the seeded data, can now share a goal name, but one user
can't have two goals of the same name. This also makes `POST /api/goals` possible for the
first time (see `backend.md`); goals are no longer only created through migrations.

## Decisions log

| Date | Decision | Reason |
|------|----------|--------|
| 2026-10-17 | `goals.user_id` is nullable, `NULL` keeps meaning the three shared seeded goals | Same reasoning as `routine_items`/`daily_logs`'s `user_id`: adding a creation API shouldn't change what every existing reader of `GET /api/goals` already sees |
| 2026-10-17 | `goals`'s unique constraint moves from `name` alone to `(user_id, name)` | Once goals can be created per user, two different users (or a user and the seed data) needing the same goal name (e.g. "learn X") is normal, not a collision; uniqueness should scope to the owner, not the whole table |
| 2026-10-17 | `users` is a new table, groundwork only — no existing table gets a `user_id` yet | Phase 8's accounts/auth architecture is now agreed with the user (see `backend.md`'s decisions log), but scoping every existing table to a user is separate, much larger work than proving accounts can exist; this slice is deliberately narrow |
| 2026-10-17 | `measurements` is unique on `(source, metric, measurement_date)`, not on an external id from the source | Different sources don't all provide a stable external id for a day's total (WakaTime's daily summary isn't a discrete record the way a GitHub commit sha is); keying on the day itself and upserting works the same way for every source, and matches that a day's total can legitimately change between two syncs of the same day |
| 2026-10-17 | `hub_sync_processed_files` is keyed by `content_sha`, not `repo_path` | A file's git blob sha stays the same across a rename; a path-keyed table would treat the renamed file as new and log it again. See `hub-sync.md`'s decisions log |
| 2026-10-16 | `routine_items.category`, `hillside_field` have no default, `source` defaults `MANUAL`, `private` defaults `true` | A category or hillside field can't be guessed for existing rows, so they stay nullable until someone assigns one through Flock; `source` and `private` have an honest single-user default (manually tended, not yet shared) that every existing row already matches |
| 2026-10-16 | `routine_items.goal_id` is nullable, unlike `learning_topics.goal_id` | Not every routine item rolls up to a goal (plenty are just upkeep: sleep, cleaning); forcing one would mean inventing a goal for rows that don't have one |
| 2026-09-19 | `tasks` also carries its own `label`, `routine_item_id`, and `learning_topic_id`, not just `schedule_template_id` | A `LEARNING_SLOT` template doesn't fix which topic applies — that's resolved per day (see `schedule.md`'s frontend-orchestration decision) — so it has to live on the task, not the template. Once `learning_topic_id` was needed there anyway, giving ad-hoc tasks (no template) the same optional target columns plus their own `label` was the direct extension, not a special case |
| 2026-09-18 | `daily_logs.source` gains a third value, `schedule` | A task completed through the Phase 3 board is a distinct origin from a manually logged entry or a hub-sync-detected one, and provenance should stay visible per the original source-field decision below |
| 2026-09-18 | Two new tables, `schedule_templates` and `tasks`, rather than extending `daily_logs` | `daily_logs` is an append-only record of completed facts; a task has mutable state (`TODO` to `DONE`) before it becomes one, which doesn't fit an append-only table |
| 2026-09-18 | Goals are seeded via migration, not created through the API | The three goals are fixed by the project's own structure (see PROJECT.md), not user-editable data; a migration is the current, reasonable place for fixed reference data |
| 2026-09-18 | Exactly-one-target enforced both in the DailyLog entity's factory methods and as a DB check constraint | The entity API can't construct an invalid instance, but the DB constraint guards against any future write path (e.g. a hub-sync script) that bypasses it |
| 2026-09-18 | DailyLog gets a source field (manual vs hub-sync, later schedule) | Keeps provenance visible; completions from different origins shouldn't be indistinguishable in the data |
| 2026-09-18 | Four Phase 1 tables: routine_items, learning_topics, daily_logs, goals | Matches the Phase 1 requirement exactly, no speculative tables for Phase 2 or 3 logic |
| 2026-09-18 | No user table, no auth | Single user, personal tool, see PROJECT.md; revisit only if this needs to be shared. **Revisited 2026-10-17**: Phase 8's hillside needs this; `users` now exists, but nothing else references it yet — see the 2026-10-17 entry above |
| 2026-09-18 | No edit history or versioning on daily_logs | Adds complexity with no current justification; revisit if incorrect logs become a real recurring problem |
| 2026-10-17 | `books` has no `chapters` table, just `current_chapter`/`total_chapters` integers | Matches `docs/design.md`'s Reading page, which needs chapter progress as a number out of a number, not a catalog; a reader's note's "chapter placed" field (Phase 7, not built yet) is an LLM's free-text guess, not a foreign key |
| 2026-09-19 | `current_reading_log` is append-only with no status field | The current book is always just the latest row; a status field would duplicate what ordering already tells you |

## Open questions

- None currently open for Phase 6.
- Phase 8: `routine_items`, `daily_logs`, and now `goals` have an optional `user_id`; how
  existing dev data backfills to a seed user is still undecided — see `backend.md`'s open
  questions for the full list.