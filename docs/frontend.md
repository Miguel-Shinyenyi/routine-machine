# Frontend

## Purpose

The shepherd app: a tracking interface for the fixed schedule, redesigned in Phase 5 from a
project-tracking-board metaphor (backlog, roadmap, board, reports) to the shepherd's own
language in `docs/design.md`. The mapping to real data is unchanged, only the names, visual
language, and page content changed.

**Design reference.** The user supplied a static HTML/CSS canvas export (nav icons, exact
copy, page layouts, a `tokens.css`) as the visual source of truth, compared against this app
page by page on 2026-10-17. Real, structural gaps were found and most were closed in the same
session — see the decisions log below for what changed and what's still a documented,
deliberate deviation rather than an oversight.

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

- **Watering holes** (`/watering-holes`, new in Phase 6): source cards grouped into
  "Connected and waiting" / "Not connected yet" (coloured mark per source, matching the
  design reference's own palette), plus a table of which routine items count automatically
  and by what rule, generated from live data rather than the reference's hand-authored copy
  (see the decisions log). Not one of `docs/design.md`'s four original shepherd views; added
  once Phase 6 built something worth a status page for.
- **Reading** (`/reading`, new in Phase 7): the current book (cover placeholder, title,
  author, chapter progress bar), a form to start a book when none is set yet, the note field
  with a live word count, "What the Shepherd read." (a "Counts for today" chip, a 3-stat score
  grid, the note's own text with quoted phrases marked inline via `<mark>`, ideas connected,
  one question back — or a plain "wasn't assessed" message), an earlier-notes list, and the
  "How a note is read" / "Your notes stay yours" explanatory cards, per `docs/design.md`'s
  layout for this page.
- **Hillside** (`/hillside`, new in Phase 8): field and window segmented controls (Link-based,
  server-rendered, not client state — see the decisions log), a band card, a histogram of
  participants by days shown up, a "walking near you" list of pseudonymous neighbours, and the
  "why this hillside is calm" rules card. Reads `GET /api/hillside`; `asUserId` is a demo-mode
  query param until the Next.js session exists (see `backend.md`'s open questions) — omitted,
  the page still shows the real distribution, just no personal band.

`/` redirects to `/today`. The four old paths (`/board`, `/roadmap`, `/backlog`, `/reports`)
redirect (307, in `next.config.ts`) to their renamed routes so existing links keep working.

## Current state

Built: Next.js (App Router), TypeScript, Tailwind. No auth (matches the rest of the
project — single user), so `lib/api.ts` is a plain server-side `fetch` wrapper against the
backend and the intelligence service, not the bearer-token version settlement-engine's
frontend needed.

**Design system** (`app/globals.css`): the tokens from `docs/design.md` as CSS custom
properties (`--canvas`, `--surface`, `--tint`, etc.), redefined under
`prefers-color-scheme: dark`. The system font stack replaces `next/font/google`'s Geist. A
`.glass` utility (blur + saturate) falls back to a solid `--surface` under
`prefers-reduced-transparency: reduce`, and `prefers-reduced-motion: reduce` disables all
transitions globally.

**Theme toggle** (`components/ThemeToggle.tsx`, in the desktop sidebar only): cycles
Auto → Light → Dark → Auto, overriding the system setting by setting `data-theme="light"`/
`"dark"` on `<html>` and persisting the choice to `localStorage`. `app/globals.css` gained a
matching `:root[data-theme="dark"]` block and the existing `@media (prefers-color-scheme:
dark)` block is guarded with `:not([data-theme="light"])`, so an explicit "Light" choice
overrides a dark system setting too, not just the reverse. A small inline script in
`layout.tsx`'s `<head>` applies the saved theme before first paint (reading `localStorage`
directly, not through React), avoiding a flash of the wrong theme on load. The toggle itself
reads via `useSyncExternalStore` with a tiny in-module pub-sub (`localStorage`'s own
`storage` event only fires in *other* tabs, never the one that wrote it), not `useState` in
a `useEffect` — the project's lint config flags synchronous `setState` inside effects, and
this reads cleanly from the external store instead of working around that rule.

**Shell** (`components/Shell.tsx`, replacing `NavBar`): a glass sidebar (220px) on desktop, a
floating glass tab bar fixed to the bottom on phone, both client components (needs
`usePathname()` for active-link state). Nav icons are inline SVGs copied from the design
reference's own markup, not invented. Desktop lists all seven views, including `/hillside`;
the phone tab bar matches the reference's stated 5-item set (Today, Path, Reading, Tally,
Hillside) — Flock and Watering holes are desktop/sidebar-only, same as the reference's own
"Flock and Watering holes open from Tally on phone" note, though this app doesn't yet add
that cross-link from Tally itself (open question). The sidebar footer line
("The Shepherd has kept watch with you for N days.") and Today's "N of M" badge both come
from real data (`RootLayout` fetches `/api/tasks?date=today` and `/tally` once, read-only,
never `materializeDay`, which can create tasks as a side effect unsuitable for every page
load) — not the reference's fixed sample numbers.

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
  `<form>`s per Next.js's own guidance, not client-side fetch calls. `TaskCard`'s done check
  button is solid-filled (`--tended-ink`) with a white check icon, matching the reference; an
  auto-counted task's source chip turns amber→green on completion rather than staying amber,
  matching the design reference README's own stated state. The eyebrow date
  (`lib/formatDate.ts`, tested) and the Shepherd notification card use real data
  (`suggestion.reason`, already fetched for "The staff") rather than the reference's specific
  sample insight copy, which this app has no Phase-4-level pattern logic to honestly produce
  yet. The hillside teaser card links to `/hillside` with a generic invite, not a personalized
  claim, since there's no logged-in "you" to compute a real one for without the Next.js
  session.
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
- **Reading** (`app/reading`) fetches `GET /api/books/current` (404 handled as "no book yet,
  show `NewBookForm`," the same `ApiError`-status-check pattern `fetchCurrentReading` already
  used in Tally/Flock) and, once a book exists, `GET /api/reading-notes?bookId=`. Submitting a
  note (`ReadingNoteForm`, a Server Action: `submitReadingNote` in `lib/actions.ts`) calls the
  intelligence service's `POST /reading-notes/assess` first, then `POST /api/reading-notes`
  with whatever the assessment returned (or nulls, if it failed or wasn't configured — the
  action catches that and never blocks the save, matching the backend's own "assessment is
  optional" design). The newest note in the list doubles as "What the Shepherd read.": no
  separate client state is kept for the just-submitted note, the page simply re-renders with
  it as the first item after `revalidatePath`. `NewBookForm` and `ReadingNoteForm` pass book
  context (title, current/total chapters) to the assessment call via hidden form fields set
  from server-rendered data, not typed by the user.

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
| 2026-10-17 | Added `--field-edge`/`--glass-edge` tokens and a shared `.field` input style to `globals.css` | The design reference's `tokens.css` defines both; this app's tokens were missing them, so every form input was using `--hairline` for its border instead of the dedicated, slightly darker field-edge colour the reference specifies |
| 2026-10-17 | `TaskCard`'s done check button is now solid-filled (`--tended-ink`) with a white check icon, not a pale `--tended-fill` background | Matches the design reference exactly; found during the comparison, not an arbitrary restyle |
| 2026-10-17 | The hillside's field/window controls are `Link`s with `aria-pressed`, not client state + fetch | Matches this app's existing all-server-rendering pattern (Today's day navigation works the same way); no new client-side data-fetching pattern needed for one page, and the URL stays shareable/bookmarkable per state |
| 2026-10-17 | `/api/hillside`'s `asUserId` is read from a `?asUserId=` URL param on the page too, not hidden anywhere | Honest about being a demo-mode stand-in for a real logged-in user (see `backend.md`'s decisions log); hiding it would make the page look more finished than it is |
| 2026-10-17 | `markQuotes` (`lib/markQuotes.ts`) is a pure, tested function the Reading page calls to render `<mark>` spans, not inline string manipulation in the page | The matching logic (find each quoted phrase's exact position, split into segments, handle a phrase not found verbatim) has real edge cases worth testing in isolation, same reasoning `lib/schedule.ts`'s pure functions already follow |
| 2026-10-17 | Quoted phrases are marked inline in the note's own text (`<mark>`), not rendered as a separate chip list | Matches the design reference exactly; also reads better — the quote appears where it actually occurs in what the user wrote, not detached from it |
| 2026-10-17 | The theme toggle lives in the desktop sidebar only, not the phone tab bar | The phone tab bar is already near capacity (six links); the toggle isn't core navigation, and desktop is where it's most discoverable as a settings-style control |
| 2026-10-17 | `ThemeToggle` reads via `useSyncExternalStore` with a tiny pub-sub, not `useState` set inside a `useEffect` | The project's lint config (`react-hooks/set-state-in-effect`) flags synchronous `setState` in effects; `useSyncExternalStore` is the pattern React's own docs recommend for syncing with an external system like `localStorage`, and it also gives a hydration-safe server snapshot for free |
| 2026-10-17 | `submitReadingNote` is one Server Action doing two sequential calls (assess, then save), not two actions the page coordinates | The assessment's result needs to reach the save call before anything is persisted, and the user only takes one action (clicking "Save note"); splitting it into two actions would need client-side state to pass the assessment between them for no benefit |
| 2026-10-17 | A failed or unconfigured assessment never blocks saving the note | Matches the backend's own decision that assessment fields are optional; the note is the user's real action, and the intelligence service being down, rate-limited, or simply not configured (no `ANTHROPIC_API_KEY`) is exactly the kind of failure that shouldn't cost the user their note |
| 2026-10-17 | "What the Shepherd read." is just the newest note in the already-fetched list, not separate client state held after submit | The page already re-renders with the freshly saved note as the list's first item once `revalidatePath` runs; keeping a second copy in client state would duplicate the same data for a view that server rendering already produces correctly |
| 2026-10-16 | Renamed the four views to Today/Path/Flock/Tally per `docs/design.md`, old paths redirect via `next.config.ts` | The shepherd language is the whole point of the Phase 5 redesign; a 307 redirect (not permanent, since the rename could still be revisited) keeps any existing link or bookmark working rather than 404ing |
| 2026-10-17 | Watering holes' "how each activity is counted" table is built from `GET /api/routine-items`, not a second purpose-built endpoint | The task explicitly asked for this to be generated from routine items, not hard-coded; the existing endpoint already exposes `source` and `countRule`, so adding another endpoint would duplicate the same data in a second shape for no reason |
| 2026-10-17 | Watering holes is a fifth nav link, not folded into an existing view | It's a status page about the sync machinery itself, a different kind of thing from the four daily-use shepherd views `docs/design.md` defines; forcing it into Tally or Flock would blur what each page is for |
| 2026-10-16 | The sidebar/tab bar link only to the built views, not to Reading or Hillside | `docs/design.md` names all six nav labels, but Reading (Phase 7) and Hillside (Phase 8) don't exist yet; a link to a page that 404s is worse than waiting to add it once built. Reading was added to this list once the page was actually built |
| 2026-10-16 | `GoalHoursChart` is hand-rolled SVG, not a charting library | The project has no charting dependency yet and a three-line cumulative chart doesn't need one; matches the existing charts' precedent of plain SVG/CSS built from the dataviz skill's guidance rather than a new dependency |
| 2026-10-16 | Removed `CompletionRateChart`, `StreakChart`, `SkipHeavyDaysList` | Superseded by Tally's per-routine meters and steep-days list, which read the same `/patterns` data in the page design's own shape; dead code once nothing referenced them, deleted rather than left in place |
| 2026-09-19 | Board materializes by calling the intelligence service's `/suggestion` directly from a plain server-side function, not a Server Action | It's only ever invoked from the page's own render, never bound to a client form/button, so it doesn't cross the client/server boundary a Server Action exists for; `"use server"` is reserved for `lib/actions.ts`'s mutations that client components actually call |
| 2026-09-19 | Backlog (now Flock) also manages routine items and learning topics | Not in the original four-views description, but the schedule-template form needs an existing routine item to point at and there was no other way to create one through the UI; Flock's "master list" framing covers this without stretching it |
| 2026-09-18 | Next.js, matching settlement-engine's frontend | Reuses a known-working stack rather than introducing a fourth technology into the project for a UI layer |
| 2026-09-18 | Reports (now Tally) render Phase 2's existing `/patterns` output directly, no new statistics computed in the frontend for that data; Phase 5 added `/tally` in the intelligence service for the data `/patterns` doesn't have | Keeps all pattern/statistics logic in one place (the intelligence service); the frontend's job is display, not calculation |

## Open questions

- Tally's "learning notes" stat tile still says "Not tracked yet" rather than a real number.
- Path, Flock, and Tally were not given the same design-reference fidelity pass Today,
  Reading, and Watering holes got on 2026-10-17 (icons in page content beyond the nav, the
  reference's exact copy/structure in each page body). The shared pieces (tokens, nav icons,
  check button, chip states) apply everywhere already; a dedicated pass on these three pages'
  own content is still open.
- Flock and Watering holes aren't reachable from the phone tab bar, matching the design
  reference's own nav list, but the reference's "they open from Tally on phone" cross-link
  isn't built — there's currently no way to reach them at all on a 390px phone.
- The hillside has no real opt-in concept, no pseudonym rotation, and `asUserId` is a plain,
  unauthenticated query param — see `backend.md`'s open questions for the full list; this
  page is only as trustworthy as that backend groundwork, not more.
- There's no way yet to edit a book's `currentChapter`/`totalChapters` after creating it
  (`POST /api/books` only creates), matching the open question already recorded in
  `backend.md`. The Reading page shows chapter progress if set at creation, nothing more.
- Reading's 390px phone layout was not confirmed live this session either, same
  `resize_window` tool limitation as Watering holes.
- Watering holes' 390px phone layout was not confirmed live this session: `resize_window`
  reported success but the screenshot it produced stayed at the desktop viewport, the same
  tool limitation noted for Phase 5's walkthrough. The page reuses the same `flex-col`/
  `sm:flex-row` pattern already used elsewhere, but that is a code-review observation, not a
  rendered check.
- `docs/schedule.md`'s open question about real target durations is still open; Path shows a
  banner rather than inventing one.