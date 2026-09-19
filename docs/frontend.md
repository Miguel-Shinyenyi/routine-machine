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

Built: Next.js (App Router), TypeScript, Tailwind. No auth (matches the rest of the
project — single user), so `lib/api.ts` is a plain server-side `fetch` wrapper against the
backend and the intelligence service, not the bearer-token version settlement-engine's
frontend needed.

- **Backlog** (`app/backlog`) also manages routine items and learning topics, and current
  reading, not just schedule templates and ad-hoc tasks — see the decisions log; without it,
  the schedule-template form would have nothing to point `routineItemId` at on first use.
- **Roadmap** (`app/roadmap`) reads all schedule templates and groups them by day client-side.
- **Board** (`app/board?date=`, defaults to today) materializes the day's tasks on load via
  `lib/materialize.ts` (see `schedule.md` for the frontend-orchestration decision), then
  renders the three status columns. Status changes are Server Actions
  (`lib/actions.ts::updateTaskStatus`) bound per-button via `TaskCard`, progressively
  enhanced `<form>`s per Next.js's own guidance, not client-side fetch calls.
- **Reports** (`app/reports`) renders the intelligence service's `/patterns` output as: a
  meter per routine item (completion rate), a current-vs-longest dumbbell chart (streaks), a
  status-badged list, not a chart, for skip-heavy days (a handful of flagged days isn't a
  magnitude/trend/identity job), and a stat-tile pair for the topic/routine correlation. Built
  following the dataviz skill's procedure (form before color, the validated reference
  palette, direct labels, a table view under every chart) rather than picking chart types or
  colors by eye.

The one piece of real frontend logic (`lib/schedule.ts`: which templates still need a task for
a given day, and computing a date's weekday independent of server timezone) is unit-tested
with Vitest — 5 tests. Everything else is page rendering and forms, verified by actually
running the app (`npm run dev` against the other two services) and clicking through all four
views and the full materialize → start → done flow in a browser, not by an automated test
suite; settlement-engine's own frontend has no test framework either, so this isn't a
departure from precedent.

## Decisions log

| Date | Decision | Reason |
|------|----------|--------|
| 2026-09-19 | Board materializes by calling the intelligence service's `/suggestion` directly from a plain server-side function, not a Server Action | It's only ever invoked from the Board Server Component's own render, never bound to a client form/button, so it doesn't cross the client/server boundary a Server Action exists for; `"use server"` is reserved for `lib/actions.ts`'s mutations that client components actually call |
| 2026-09-19 | Backlog also manages routine items and learning topics | Not in the original four-views description, but the schedule-template form needs an existing routine item to point at and there was no other way to create one through the UI; Backlog's "master list" framing covers this without stretching it |
| 2026-09-19 | Reports charts follow the dataviz skill: meter (completion rate), dumbbell (streaks), status-badged list not a chart (skip-heavy days), stat tiles (correlation) | Each form was picked by the data's job per the skill's `choosing-a-form` reference, not by habit; answers the "exact chart types" open question below concretely rather than deferring further |
| 2026-09-18 | Next.js, matching settlement-engine's frontend | Reuses a known-working stack rather than introducing a fourth technology into the project for a UI layer |
| 2026-09-18 | Four views named after project-tracking concepts (backlog, roadmap, board, reports), not generic tracker terms | Matches the working metaphor already used across this project and settlement-engine; also makes the mapping to real data unambiguous, backlog is templates, board is tasks, reports is Phase 2's statistics, nothing invented beyond what those already are |
| 2026-09-18 | Reports render Phase 2's existing `/patterns` output directly, no new statistics computed in the frontend | Keeps all pattern logic in one place (the intelligence service); the frontend's job is display, not calculation |

## Open questions

- None currently open for Phase 3.