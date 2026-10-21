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

Phase 8 also built via `V17__hillside_opt_in.sql`: `users` gains `hillside_opt_in` (boolean,
not null, default `false`). The hillside now only counts a user's logs once they've opted in
(`PUT /api/users/me/hillside-opt-in`, see `backend.md`) — defaulting to `false` means no
existing user is silently enrolled by this migration, even if they already have non-private
routine items.

Phase 8 also built via `V16__goal_user_scoping.sql`: `goals` gains a nullable `user_id`
(indexed), referencing `users`. `NULL` still means the three seeded goals, shared and visible
to everyone, exactly as before. The old global `UNIQUE(name)` constraint
(`goals_name_key`) is replaced by `UNIQUE(user_id, name)` (`goals_user_id_name_unique`):
two different users, or a user and the seeded data, can now share a goal name, but one user
can't have two goals of the same name. This also makes `POST /api/goals` possible for the
first time (see `backend.md`); goals are no longer only created through migrations.

Phase 9 built via `V18__user_time_zone.sql`: `users` gains `time_zone` (varchar, not null,
default `'UTC'`) — an IANA zone id. Groundwork for the evidence-to-credit pipeline's local-date
rule (see `backend.md`), not the full Profile page (Phase 10).

Phase 9 also built via `V19__evidence_to_credit_pipeline.sql`, three new tables:

- `scoring_rules(id, version, goal_id references goals, min_minutes nullable, description,
  published_on, created_at)`, unique on `(version, goal_id)`. A versioned, published rule for
  what counts as qualifying evidence for one goal. Not every goal has a row — see
  `backend.md`'s decisions log for the presence-based fallback this enables.
- `evidence_events(id, user_id references users, goal_id references goals, source, source_event_id,
  occurred_at, local_date, minutes nullable, status, scoring_rule_version, reason,
  questioned_at nullable, created_at)`, unique on `(user_id, source, source_event_id)` (the
  dedupe key), a check constraint restricts `status` to `ACCEPTED`/`REJECTED`/`PENDING`/
  `SELF_REPORTED`. Every ingested piece of evidence, whatever the outcome — the credit ledger
  reads straight from this table.
- `daily_credits(id, user_id references users, goal_id references goals, local_date, credits,
  evidence_event_id references evidence_events, created_at)`, unique on
  `(user_id, goal_id, local_date)`. One row per (user, goal, day) that actually earned a
  credit; the unique constraint plus an `ON CONFLICT DO NOTHING` upsert (not application-level
  check-then-insert) is the entire mechanism behind "one credit per goal per day" and "two
  concurrent inserts produce one row" — see `backend.md`'s decisions log and
  `EvidenceCreditService`.

Phase 10 built via `V21__profile.sql`: `users` gains `interests`/`thinkers`/
`learning_preferences` (each a JSON array in a TEXT column via `StringListConverter`, same
pattern `reading_notes.ideas_connected`/`quoted_phrases` already use), `currency`
(`varchar(8)`), `day_end_hour` (`integer`, not null, default `0`, DB check constraint `BETWEEN
0 AND 23`), and `analogies_enabled` (boolean, not null, default `true`). `day_end_hour` feeds
`EvidenceCreditService`'s local-date rule (see `backend.md`); the rest is read and written
only through `GET`/`PUT /api/users/me/profile`, never by anything hillside- or cohort-facing.

Phase 11 built via `V22__granary.sql`: `users` gains `saving_target_amount` (integer,
nullable — null until the user sets one on the granary page). Two new tables:

- `savings(id, user_id references users, amount, local_date, created_at)`, unique on
  `(user_id, local_date)` — one saving per user per day, tapped, never more than once a day
  regardless of how many times the button is pressed.
- `pause_decisions(id, user_id references users, what, amount, why, heard_from, outcome
  nullable, decided_at nullable, follow_up_24h_at, follow_up_30d_at, reflection_30d nullable,
  created_at)`, a check constraint restricts `outcome` to `DECLINED`/`PROCEEDED`. `outcome`
  stays null until the user actually decides; `reflection_30d` is a separate, later field,
  filled independently of `outcome` at the 30-day look-back.

Neither table is referenced by anything hillside- or cohort-facing — see `backend.md`'s
decisions log and the privacy test that proves it.

Phase 11 also built via `V23__mpesa_category_totals.sql`: `mpesa_category_totals(id, user_id
references users, statement_month, category, amount, created_at)`, unique on
`(user_id, statement_month, category)`. Only category totals are kept — no transaction-level
detail at all, and the uploaded statement PDF is never written to disk, let alone to a table.
Re-importing the same month's statement upserts (replaces) that month's totals rather than
adding a second set of rows. Also not referenced by anything hillside- or cohort-facing.

Phase 12 part 1 built via `V24__organisations_and_cohorts.sql`:
- `organisations(id, name, created_at)`.
- `organisation_admins(id, organisation_id references organisations, user_id references
  users, created_at)`, unique on `(organisation_id, user_id)`. Who administers an
  organisation, and therefore every cohort under it — see `backend.md` for why this is
  organisation-scoped, not cohort-scoped.
- `cohorts(id, organisation_id references organisations, name, starts_on, weeks,
  created_at)`.
- `cohort_memberships(id, cohort_id references cohorts, user_id references users,
  joined_at)`, unique on `(cohort_id, user_id)`. A learner's seat in one cohort.

Phase 12 part 2 built via `V25__cohort_invites_and_seats.sql` (flagged to the user and
confirmed before building — see `backend.md`'s decisions log):
- `organisations.seat_limit` added, nullable integer, null meaning unlimited.
- `cohort_invites(id, cohort_id references cohorts, email, created_at)`, unique on
  `(cohort_id, email)`. A reserved seat for an email with no account yet — fulfilled (turned
  into a `cohort_memberships` row, then deleted) the moment that email registers. There's no
  outbound email infrastructure in this app, so this table represents a reservation, not a
  record that mail was sent.

Phase 12 part 3 built via `V26__cohort_survey_responses.sql` (flagged to the user and
confirmed before building — see `backend.md`'s decisions log):
- `cohort_survey_responses(id, cohort_id references cohorts, user_id references users,
  answer, created_at)`, unique on `(cohort_id, user_id)`. One learner's answer to the pilot's
  "did this feel supportive, neutral, or pressuring?" question — resubmitting replaces it.

Phase 12 part 4 built via `V27__cohort_study_groups.sql`:
- `cohort_memberships.study_group` and `cohort_invites.study_group` added, both nullable
  `VARCHAR(255)`. Set via the CSV's optional second column (`email,group`); a member with no
  group just doesn't appear in the "by study group" breakdown.

None of these six tables (plus the two new columns) are read by `HillsideV2Service`/
`GranaryService` or vice versa — the three modules are simply uncoupled so far, nothing yet
requires the marker-string privacy test Phase 10/11 needed. `CohortDashboardService` does
read `Goal` and `DailyLog` directly (what counts as "checked in," by the user's own explicit
choice — see `backend.md`), but those two tables aren't hillside- or granary-private, so this
doesn't cross the boundary either. That test becomes necessary once a later Phase 12 part
surfaces anything from `savings`/`pause_decisions`/`mpesa_category_totals`/`daily_credits` on
a cohort response.

Per-user scoping, part 1, built via `V28__personal_data_ownership.sql` (ownership data only, no
query uses the new columns yet): a nullable `user_id` (references `users`) and an index on
`schedule_templates`, `tasks`, `books` and `current_reading_log`. `goals`, `routine_items` and
`daily_logs` already had one (V15, V16). `learning_topics` is scoped through its goal and
`reading_notes` through its book, so neither gets a column. `measurements` and
`hub_sync_processed_files` deliberately get none: they hold what the owner's own integrations
pull in (WakaTime, ActivityWatch, GitHub, RescueTime, the UMWAYI hub), which belongs to the
deployment's owner, not to each user. Columns stay nullable: every row that existed before
accounts has `user_id IS NULL` until `OwnerClaimService` gives it to the owner (see `backend.md`).

## Decisions log

| Date | Decision | Reason |
|------|----------|--------|
| 2026-10-21 | The four new `user_id` columns (V28) are nullable, not `NOT NULL` | Existing rows have no owner until `OwnerClaimService` runs, and the create endpoints do not set one yet (later parts). Making them `NOT NULL` now would fail the migration on real data and break every create path. Tightening to `NOT NULL` is a later migration, once nothing writes a NULL |
| 2026-10-19 | `savings` is unique on `(user_id, local_date)`, no concurrency-safe upsert like `daily_credits` | Unlike a credit, a saving is only ever written by one action (the user's own tap, from a session only they control) — there's no multi-source race the way evidence events have (sync jobs, manual taps, different sources, all landing the same day). A plain find-then-insert is enough; `GranaryService.storeSaving` is idempotent by checking first |
| 2026-10-19 | `pause_decisions.amount` is a plain string (`VARCHAR(100)`), not a structured integer | The design reference shows free text ("KSh 5,000 / week", "KSh 500 / week") that doesn't reduce to one currency-amount pair; the granary's own real financial fields (`savings.amount`, `users.saving_target_amount`) are integers precisely because those are single, well-defined numbers — a pause's "how much" is closer to a label the user writes than a number this app computes with |
| 2026-10-18 | `day_end_hour` is `INTEGER`, not `SMALLINT`, even though it only ever holds 0-23 | Hibernate maps a Java `int` field to `INTEGER` by default and validates the schema matches at startup; `SMALLINT` (the first version of this migration) failed that check immediately (`Schema-validation: wrong column type encountered`). Caught by `./mvnw test` before this ever reached a report — a real case of a plausible-looking micro-optimisation (`SMALLINT` saves 2 bytes a row nobody will ever notice) breaking the actual running system |
| 2026-10-17 | `users.hillside_opt_in` defaults `false`, not `true` | The hillside previously treated every user with a non-private daily log as a participant with no consent step at all; defaulting the new flag to off is the only choice consistent with calling this a real opt-in, not a relabeled version of the same behaviour |
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
| 2026-10-18 | `daily_credits.goal_id` is a FK to the existing `goals` table, not a new taxonomy | `docs/product-direction.md`'s pipeline spec says "goal" throughout without introducing a new entity name, and this app already has a `goals` concept (user milestones like "cmu-masters-application"); reusing it is the minimal, consistent-with-existing-schema reading, even though the design reference's own sample ledger uses category-style names ("Coding", "Reading") that don't match this app's real goal names — those are the reference's own sample-data flavour, same as every other number on those pages per `design-reference/README.md`'s own "never the numbers" rule |
| 2026-10-18 | `scoring_rules` has no row for this app's three real seeded goals | They're milestone-style goals (a CMU application, target roles), not minute-trackable daily categories, so a `min_minutes` threshold doesn't mean anything for them; `EvidenceCreditService` falls back to presence-based acceptance when no rule exists, so every real evidence event in this app currently qualifies by presence alone. A goal that should have a real minute threshold needs a `scoring_rules` row written directly for now — no admin UI for authoring one yet |
| 2026-10-18 | `daily_credits` is upserted with raw `ON CONFLICT DO NOTHING`, not a `@Modifying` JPQL query or check-then-insert in Java | The database's own `UNIQUE(user_id, goal_id, local_date)` constraint is the only thing that can actually guarantee "two concurrent inserts produce one row" under real concurrency; JPQL has no portable upsert syntax, and a Java-level check-then-insert has an inherent race window between the check and the write |
| 2026-10-18 | `evidence_events.questioned_at` is a single nullable timestamp, not a separate `credit_questions` table | "Question a credit" is explicitly "a simple form that records the request," not a review workflow with its own state machine (see `docs/product-direction.md`); one column records that it happened and when, which is all a transparency signal needs to be right now |

## Open questions

- None currently open for Phase 6.
- Phase 8: `routine_items`, `daily_logs`, and now `goals` have an optional `user_id`; how
  existing dev data backfills to a seed user is still undecided — see `backend.md`'s open
  questions for the full list.

## V29: user role

`users.role` (`VARCHAR(20) NOT NULL DEFAULT 'USER'`). Values `USER` and `OWNER`. Forward-only; existing
accounts become `USER`. The owner is set at startup from `OWNER_EMAIL` (see `docs/backend.md`), never in
SQL.

