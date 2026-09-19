# Frontend

## Purpose

A tracking interface for the fixed schedule, shaped like a project-tracking board (backlog,
roadmap, board, reports) rather than a generic habit-tracker UI, since that's the working
metaphor already established for how this project itself gets built and documented.

## The four views

- **Backlog**: the `schedule_templates`, the recurring definitions themselves (what
  happens on which day), plus any ad-hoc one-off tasks not tied to a template. This is the
  master list, not a daily view.
- **Roadmap**: the weekly layout from `schedule.md` rendered as a calendar-style read of the
  backlog, one column or row per day, so the week's shape is visible at a glance.
- **Board**: today's (or a chosen date's) `tasks`, in three columns, To Do, In Progress,
  Done. Materializes from the matching day's `schedule_templates` if today's tasks don't
  exist yet. Moving a card to Done writes the underlying `daily_log`.
- **Reports**: Phase 2's `/patterns` data (completion rates, streaks, skip-heavy days, the
  topic/routine correlation) rendered as charts, not raw JSON.

## Current state

Not built.

## Decisions log

| Date | Decision | Reason |
|------|----------|--------|
| 2026-09-18 | Next.js, matching settlement-engine's frontend | Reuses a known-working stack rather than introducing a fourth technology into the project for a UI layer |
| 2026-09-18 | Four views named after project-tracking concepts (backlog, roadmap, board, reports), not generic tracker terms | Matches the working metaphor already used across this project and settlement-engine; also makes the mapping to real data unambiguous, backlog is templates, board is tasks, reports is Phase 2's statistics, nothing invented beyond what those already are |
| 2026-09-18 | Reports render Phase 2's existing `/patterns` output directly, no new statistics computed in the frontend | Keeps all pattern logic in one place (the intelligence service); the frontend's job is display, not calculation |

## Open questions

- Whether Board needs to talk to the backend directly, or through the intelligence service,
  for materializing today's tasks. Decide once implementation starts.
- Exact chart types for Reports. Decide once real data exists to look at, choosing a chart
  for data that doesn't exist yet risks picking the wrong shape.