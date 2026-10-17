# Frontend

## Purpose

The shepherd app: a tracking interface for the fixed schedule, redesigned in Phase 5 from a
project-tracking-board metaphor (backlog, roadmap, board, reports) to the shepherd's own
language in `docs/design.md`. The mapping to real data is unchanged, only the names, visual
language, and page content changed.

## The views

- **Flock** (`/flock`, was Backlog): the `schedule_templates`, the recurring definitions
  themselves (what happens on which day), plus any ad-hoc one-off tasks not tied to a
  template, routine items grouped by category, learning topics, and current reading. The
  master list, not a daily view.
- **Path** (`/path`, was Roadmap): the weekly layout from `schedule.md` rendered as seven day
  columns, weekend days styled as still waters. Shows a banner while any template has no real
  duration set, rather than inventing one.
- **Today** (`/today`, was Board): the day's `tasks` as a single grouped list in walking
  order, each with a round check button, replacing the three-column To Do/In
  Progress/Done board. A progress bar, "Closer than yesterday" (latest cumulative hours per
  goal from `/tally`), "The staff" (`/suggestion`'s topic and reason), and "One sheep
  wandered" (the most recent skip-heavy day from `/patterns`).
- **Tally** (`/tally`, was Reports): the tally stick (one notch per day shown up, dashed for
  rest days), four stat tiles, a cumulative-hours-per-goal line chart from the intelligence
  service's `/tally`, per-routine meters (Steady/Settling in/Wandering), steep days
  (`/patterns`'s `skipHeavyDays`), and the topic/routine correlation. Streak numbers are still
  returned by `/patterns` but are not rendered, per `docs/design.md`'s "no streaks" rule.

- **Watering holes** (`/watering-holes`, new in Phase 6): status of each automatic-counting
  source (on/off, last activity, what it reads), plus a table of which routine items count
  automatically and by what rule. Not one of `docs/design.md`'s four original shepherd views;
  added once Phase 6 built something worth a status page for.

`/` redirects to `/today`. The four old paths (`/board`, `/roadmap`, `/backlog`, `/reports`)
redirect (307, in `next.config.ts`) to their renamed routes so existing links keep working.

## Current state

Built: Next.js (App Router), TypeScript, Tailwind. No auth (matches the rest of the
project — single user), so `lib/api.ts` is a plain server-side `fetch` wrapper against the
backend and the intelligence service, not the bearer-token version settlement-engine's
frontend needed.

**Design system** (`app/globals.css`): the tokens from `docs/design.md` as CSS custom
properties (`--canvas`, `--surface`, `--tint`, etc.), redefined under
`prefers-color-scheme: dark`. No manual theme toggle exists, the system setting decides,
matching the chart palette's existing precedent (`components/charts/viz-tokens.css`). The
system font stack replaces `next/font/google`'s Geist. A `.glass` utility (blur + saturate)
falls back to a solid `--surface` under `prefers-reduced-transparency: reduce`, and
`prefers-reduced-motion: reduce` disables all transitions globally.

**Shell** (`components/Shell.tsx`, replacing `NavBar`): a glass sidebar (220px) on desktop, a
floating glass tab bar fixed to the bottom on phone. Both list the five built views only;
Reading and Hillside (Phases 7-8) aren't linked yet since they don't exist.

- **Flock** (`app/flock`) also manages routine items and learning topics, and current
  reading, not just schedule templates and ad-hoc tasks — see the decisions log; without it,
  the schedule-template form would have nothing to point `routineItemId` at on first use.
  Routine items render grouped by `category` (Body, Mind, Work, Home, Uncategorised), each
  row showing its `countRule`, a source chip when `source` isn't `MANUAL`, and a Private chip.
- **Path** (`app/path`) reads all schedule templates and groups them by day client-side.
- **Today** (`app/today?date=`, defaults to today) materializes the day's tasks on load via
  `lib/materialize.ts` (see `schedule.md` for the frontend-orchestration decision), then
  renders them as a single list with round check buttons. Status changes are Server Actions
  (`lib/actions.ts::updateTaskStatus`) bound per-row via `TaskCard`, progressively enhanced
  `<form>`s per Next.js's own guidance, not client-side fetch calls.
- **Tally** (`app/tally`) renders the intelligence service's `/tally` output (tally stick,
  stat tiles, `GoalHoursChart`, per-routine meters) plus `/patterns`'s skip-heavy days and
  topic/routine correlation. `GoalHoursChart` (`components/charts/GoalHoursChart.tsx`) is a
  hand-rolled SVG line chart (three goal-line colors from `docs/design.md`, reserved for this
  one chart) with a table underneath, following the dataviz skill's "form before color" and
  "table view under every chart" conventions, same as the components it replaced.
- **Watering holes** (`app/watering-holes`) reads two endpoints: `GET /api/watering-holes`
  (backend's) for the per-source status cards (on/off, last activity, description), and the
  existing `GET /api/routine-items` for the "how each activity is counted" table — filtered to
  `source !== "MANUAL"` and rendered from each item's own `source`/`countRule` fields, not a
  table written into the page. A routine item newly pointed at an automatic source shows up
  here the next time the page loads, with no page-specific code change needed.

The one piece of real frontend logic (`lib/schedule.ts`: which templates still need a task for
a given day, and computing a date's weekday independent of server timezone) is unit-tested
with Vitest — 5 tests. Everything else is page rendering and forms, verified by actually
running the app (`npm run dev` against the other two services) and clicking through the
views and the full materialize → start → done flow in a browser, not by an automated test
suite; settlement-engine's own frontend has no test framework either, so this isn't a
departure from precedent.

## Decisions log

| Date | Decision | Reason |
|------|----------|--------|
| 2026-10-16 | Renamed the four views to Today/Path/Flock/Tally per `docs/design.md`, old paths redirect via `next.config.ts` | The shepherd language is the whole point of the Phase 5 redesign; a 307 redirect (not permanent, since the rename could still be revisited) keeps any existing link or bookmark working rather than 404ing |
| 2026-10-17 | Watering holes' "how each activity is counted" table is built from `GET /api/routine-items`, not a second purpose-built endpoint | The task explicitly asked for this to be generated from routine items, not hard-coded; the existing endpoint already exposes `source` and `countRule`, so adding another endpoint would duplicate the same data in a second shape for no reason |
| 2026-10-17 | Watering holes is a fifth nav link, not folded into an existing view | It's a status page about the sync machinery itself, a different kind of thing from the four daily-use shepherd views `docs/design.md` defines; forcing it into Tally or Flock would blur what each page is for |
| 2026-10-16 | The sidebar/tab bar link only to the built views, not to Reading or Hillside | `docs/design.md` names all six nav labels, but Reading (Phase 7) and Hillside (Phase 8) don't exist yet; a link to a page that 404s is worse than waiting to add it once built |
| 2026-10-16 | `GoalHoursChart` is hand-rolled SVG, not a charting library | The project has no charting dependency yet and a three-line cumulative chart doesn't need one; matches the existing charts' precedent of plain SVG/CSS built from the dataviz skill's guidance rather than a new dependency |
| 2026-10-16 | Removed `CompletionRateChart`, `StreakChart`, `SkipHeavyDaysList` | Superseded by Tally's per-routine meters and steep-days list, which read the same `/patterns` data in the page design's own shape; dead code once nothing referenced them, deleted rather than left in place |
| 2026-09-19 | Board materializes by calling the intelligence service's `/suggestion` directly from a plain server-side function, not a Server Action | It's only ever invoked from the page's own render, never bound to a client form/button, so it doesn't cross the client/server boundary a Server Action exists for; `"use server"` is reserved for `lib/actions.ts`'s mutations that client components actually call |
| 2026-09-19 | Backlog (now Flock) also manages routine items and learning topics | Not in the original four-views description, but the schedule-template form needs an existing routine item to point at and there was no other way to create one through the UI; Flock's "master list" framing covers this without stretching it |
| 2026-09-18 | Next.js, matching settlement-engine's frontend | Reuses a known-working stack rather than introducing a fourth technology into the project for a UI layer |
| 2026-09-18 | Reports (now Tally) render Phase 2's existing `/patterns` output directly, no new statistics computed in the frontend for that data; Phase 5 added `/tally` in the intelligence service for the data `/patterns` doesn't have | Keeps all pattern/statistics logic in one place (the intelligence service); the frontend's job is display, not calculation |

## Open questions

- Reading (Phase 7) and Hillside (Phase 8) have no pages yet; Tally's "reading progress" and
  "learning notes" stat tiles currently say "Not tracked yet" rather than a real number.
- Watering holes' 390px phone layout was not confirmed live this session: `resize_window`
  reported success but the screenshot it produced stayed at the desktop viewport, the same
  tool limitation noted for Phase 5's walkthrough. The page reuses the same `flex-col`/
  `sm:flex-row` pattern already used elsewhere, but that is a code-review observation, not a
  rendered check.
- `docs/schedule.md`'s open question about real target durations is still open; Path shows a
  banner rather than inventing one.