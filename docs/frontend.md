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
- **Hillside** (`/hillside`, Phase 8, rebuilt for Phase 9's credit-based v2): scope (cohort/
  open — both the same opted-in population today, see `backend.md`'s decisions log) and goal
  segmented controls (the goal list is each signed-in user's own real goals, not the design
  reference's fictional category names — same "real data" precedent as everywhere else),
  a real toggle-switch "Show the hillside" control (`.switch`, copied from the reference's own
  CSS), a band card (score/days/goal count), a "Returns" card (a cohort-wide count of people
  who came back after a gap this season, never a per-person rank), a "walking near you" list
  with per-row credit dots, the "why this hillside is calm" rules card, and — for a signed-in
  user — the full credit ledger ("How your credits were counted": day, goal, evidence, status,
  credit, why, and a "Question this" link per row). Reads `GET /api/hillside/v2`,
  `GET /api/goals?userId=`, and `GET /api/evidence-events`; a signed-in session supplies the
  real `asUserId` automatically (the `?asUserId=` query param is ignored once a session
  exists, and only still works as a demo-mode preview when signed out).
- **Profile** (`/profile`, new in Phase 10, signed-in only — redirects to `/login` otherwise):
  "About you" (email, a time zone field, a "your day ends at" hour field feeding Phase 9's
  local-date rule, currency), "What you love" (interest pick-chips from a fixed reference set
  plus a free-text "add your own," and a free-text "thinkers you return to" field), "Explain it
  my way" (the analogies on/off switch), and "How you like to learn" (another pick-chip list).
  One form, one `updateProfile` Server Action, partial updates only (unset checkboxes send no
  value, so only what's actually ticked is sent — matching the backend's own
  only-apply-non-null-fields update). Reads/writes `GET`/`PUT /api/users/me/profile`.
- **Granary** (`/granary`, new in Phase 11, signed-in only — redirects to `/login`
  otherwise): a "set a daily amount" prompt until one exists, then a real "Store {amount}
  today" button; four stat tiles (stored this month against a target, days stored, pauses
  kept, stored since you started); the "one grain a day" jar (a filled or dashed mark per
  elapsed day this month); "the pause" — a form logging a tempting decision ("Wait 24 hours"
  or "I've already decided," both create the same entry; the pause list below always offers
  Decline/Proceed on an undecided entry, not only once the 24-hour mark has passed — see the
  decisions log) and a table of every pause logged, with inline decide/reflect controls; and a
  sidebar ("Where it went" — a per-category meter bar, only rendered once a statement has
  been imported — "Where the granary drinks from," and a static boundaries card). M-Pesa
  import (`components/MpesaImportForm.tsx`, behind an "Import M-Pesa statement" button that
  opens a native `<dialog>` modal, not an inline expanding card) is a real, working upload: a
  PDF file input plus an optional password
  field, submitted as multipart form data via `lib/actions.ts::importMpesaStatement` and a new
  `backendFetchMultipart` helper in `lib/api.ts` (the only call in this app that isn't JSON).
  A wrong/missing password or an unrecognised statement format shows its own inline error,
  distinguishing the two by the backend's `type` field on the error response. Reads/writes
  `GET /api/granary/summary`, `PUT /api/granary/saving-target-amount`,
  `POST /api/granary/savings`, `GET`/`POST /api/granary/pauses`,
  `PATCH /api/granary/pauses/{id}/decide`, `PATCH .../reflect`,
  `POST /api/granary/mpesa-statement`, `GET /api/granary/mpesa-categories`.
- **Cohort dashboard** (`/cohort/[cohortId]`, new in Phase 12, signed-in only — redirects to
  `/login` otherwise): the four pilot measures as stat tiles (each a count-of-denominator, per
  `docs/product-direction.md`'s "every figure with its denominator"), the weekly check-in bar
  chart (a dashed/blank bar for a week that hasn't started, matching the backend's `null` for
  an unstarted week — see `backend.md`), the "by study group" table (only rendered once a
  cohort has at least one grouped member; a group under 5 shows its row with the two rate
  columns collapsed into "Hidden: fewer than 5 people," never the real numbers), and a
  three-button survey form ("Supportive"/"Neutral"/"Pressuring"). `Shell.tsx` swaps in a
  completely separate sidebar for any `/cohort/*` route — matching the design reference's own
  cohort/admin nav (`cohort.html`'s `aria-label="Programme admin"`), which is a different nav
  from the personal shepherd app's, not an entry added to it — same branching precedent
  `Shell` already uses for `/login`/`/register`'s minimal chrome. That nav only has what's
  real: the current cohort (highlighted) and a "My own Today" cross-link back to the personal
  app; no "Seats and invites"/"Reports"/"Privacy and data" links, since those pages don't
  exist yet and a nav link to nothing would be worse than no nav link. Reached by direct URL
  only — there's no org/cohort-creation UI yet, nor a "my cohorts" list to link from anywhere
  (see the decisions log).
  Deliberately read-mostly: admin actions (creating an organisation/cohort, bulk CSV invites,
  setting a seat limit) have no UI yet, only the survey write does. A `403` from the backend
  (neither an org admin nor a cohort member) renders a plain "Not your cohort" message instead
  of the dashboard, inside the normal page chrome rather than crashing. Reads
  `GET /api/organisations/cohorts/{id}/dashboard`, `GET .../dashboard/by-group`,
  `GET .../dashboard/weekly-check-ins`; writes `POST .../survey-response`.

- **Watchtower** (`/watchtower`, observability part 5, owner only, slices 1 to 4 of 4): the owner's operations page. Built so far:
  the route, the 404 gate, the service health list (`GET /api/watchtower/services`, one card per service: Up, Down or
  Not configured) and the sync table (`GET /api/watchtower/sync`: per source a result chip, failures in a row, last
  success, newest data and the last 24 hours; a note says when Prometheus is not answering). To everyone but the owner, signed in or not, it is the plain Next 404 page: no redirect to `/login`
  and no sidebar link, so the page is not known to exist. `lib/watchtower.ts::fetchServices` turns the backend's 404 into
  `null` (the page then calls `notFound()`) and rethrows every other failure, so an outage is never shown as a 404. The
  owner reaches it by typing the address. The charts (`components/charts/WatchtowerChart.tsx`, a client component for the hover) sit under a range row of three links
  (`?range=1h|6h|24h`). The logs section (`lib/logs.ts`) is a plain `GET` form (level, request id) over the newest 100 lines; the range
  row sets the logs' range too, and a request id in a row filters to that request. The page has an "API documentation" section with a link to `/watchtower/docs` (staged Prompt 4, part 4): both APIs in one read-only Swagger UI. `app/watchtower/docs/route.ts` is the way in and `app/watchtower/docs/[...path]/route.ts` is a narrow proxy: GET only, a fixed list of three backend specs and the Swagger UI's own files (`lib/docsProxy.ts`, `proxyTarget`), the owner's session added as a bearer token, the body's type checked (`safeContentType`) and the UI's configuration tidied (`sanitizeSwaggerConfig`). To anyone but the owner, signed in or not, every `/watchtower/docs` address is the plain 404, the same as the page. Details and checks:
  `docs/observability.md`, "Part 5".

`/` redirects to `/today`. The four old paths (`/board`, `/roadmap`, `/backlog`, `/reports`)
redirect (307, in `next.config.ts`) to their renamed routes so existing links keep working.

## Per-user scoping, part 3: every personal page works for the signed-in user

The app began as one person's tool, so every page showed the same data to everyone, including
signed-out visitors and brand-new accounts. Part 2 made the backend answer for one user; this part
makes the frontend ask for one.

- **Signed out means no personal data.** Today, Path, Flock, Tally, Reading and Watering holes
  now redirect to `/login`, like Granary, Profile and the cohort page already did
  (`requireSessionUserId()` in `lib/auth/session.ts`). Before, a signed-out visitor landed on
  `/today` showing the founder's routine, goals and hours.
- **Every personal backend call carries the session user's id**, through `withUserId(path, userId)`
  (`lib/scope.ts`, unit-tested, replaces a `userId` already on the path instead of sending two):
  all the pages above, `lib/materialize.ts`, the root layout's sidebar counts, and eight Server
  Actions (`createScheduleTemplate`, `createRoutineItem`, `createAdHocTask`, `createLearningTopic`,
  `updateTaskStatus`, `createCurrentReading`, `createBook`, `submitReadingNote`). The layout makes
  no personal calls at all when signed out.
- **A new account starts empty**, with the empty states the pages already had: "Nothing
  scheduled yet" on Today, "None yet" lists on Flock, "What are you reading?" on Reading.
- **`materializeDay` skips a slot the backend rejects** (a `400` on creating the day's task)
  instead of failing the whole page. The intelligence service is not yet scoped per user, so it
  can suggest a learning topic that is not this user's; the backend now refuses that link.
- **Intelligence calls carry the user too (part 4, done)**: Today's suggestion, tally and patterns,
  the Tally page, the sidebar's "kept watch with you for N days" and `materializeDay`'s suggestion
  all send the session user's id (`withUserId`), so a new account's Today and Tally are empty:
  "No hours logged yet", "No learning topics to suggest yet", "0 notches in 28 days".
- **Watering holes are owner-only (part 5, done)**: the page and the sidebar badge send the user id
  to `/api/watering-holes`; the owner sees the real status of each source, any other account sees
  every source under "Not connected yet". The sync services now log into the owner's items for the
  owner, so after the owner claim new synced data shows on the owner's pages.

## Current state

Built: Next.js (App Router), TypeScript, Tailwind. Every personal page now works for the
signed-in user only (per-user scoping part 3, below). The backend calls themselves are still
unauthenticated and name the user with a `userId` query parameter or body field (the backend
trusts it; closing that is the access-control stage — see `backend.md`), so `lib/api.ts`'s `backendFetch` is a plain server-side `fetch` wrapper. Real
password login now exists (`/login`, `/register` — see the decisions log and
`lib/auth/jwt.ts`/`lib/auth/session.ts`), and `authenticatedBackendFetch` sends the session's
own bearer token to the one endpoint that needs it so far, `GET /api/users/me`.

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
  Routine items render grouped by `category` (Body, Mind, Work, Home, Uncategorised), each as
  a list row with a coloured initial square (colour by category, matching the design
  reference's palette; the reference's hand-picked per-routine icons aren't reproduced since
  there's no data to pick one per real routine item), its `countRule`, a source chip when
  `source` isn't `MANUAL`, a `hillsideField` chip when set, and a Private chip. Eyebrow shows
  the real routine/goal counts, not the reference's fixed sample numbers.
- **Path** (`app/path?monday=`, defaults to the current week) reads schedule templates once
  and the week's seven days of existing tasks (`GET /api/tasks?date=`, one call per day, never
  `materializeDay` — a week-wide read view must not create tasks for days nobody has opened
  yet). Each day shows a status dot per scheduled routine: tended (a matching task is `DONE`),
  wandered (past day, not done), today (still open), or ahead (future day, or today before
  it's tended) — `lib/weekDates.ts` (tested) computes the week's Monday and its seven dates
  from any date in it, used for both rendering and the Last/This/Next week links.
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
  session. Phase 10: "The staff" card also shows an analogy box when a session exists, the
  signed-in user's Profile has `analogiesEnabled` on and at least one interest set, and a topic
  exists to explain — `POST /analogy` (intelligence service) called with
  `{topic: suggestion.topic.name, interest: profile.interests[0]}`, the result rendered as the
  analogy sentence, the plain definition, and a line naming which interest it came from plus a
  link to Profile to turn it off. A failed or unconfigured call (`.catch(() => null)`, same
  pattern `submitReadingNote` already uses for the assessment call) just means no analogy box,
  never a page crash — verified live against the real Anthropic API, which failed on the same
  account-billing issue Phase 7's reading-note assessment already hit, caught cleanly. Phase
  11: Today also shows a Shepherd-style prompt card for each granary pause due for a
  follow-up (`GET /api/granary/pauses/due`) — "have you decided?" with inline Decline/Proceed
  for a 24-hour-due pause, or a short reflection field for a 30-day-due one.
- **Tally** (`app/tally`) renders the intelligence service's `/tally` output (tally stick,
  stat tiles, `GoalHoursChart`, per-routine meters) plus `/patterns`'s skip-heavy days and
  topic/routine correlation. The tally stick is grouped in fives (`<div>` per group of notch
  pills), matching the design reference's own grouping, with a real "N notches in M days."
  headline and a real "Since {date}" eyebrow from the window's first day, not the reference's
  fixed sample numbers. `GoalHoursChart` (`components/charts/GoalHoursChart.tsx`) is a
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
| 2026-10-25 | Session replay stays off on every page | The prompt prefers off to masking on the granary, reading notes, profile and cohort pages. Nothing in the product questions so far needs a recording of a screen, and a mask that fails shows a note or an amount. Held by the options test |
| 2026-10-25 | The privacy page is generated from a disclosure list that a test holds equal to the allowed events | A privacy page written by hand drifts from the code the day an event is added. Now an event cannot be allowed without being described, and "sent today" is a flag to flip when one is wired |
| 2026-10-25 | `identify` is called with the internal user id alone, and anything but a positive whole number is refused | The prompt: identify by internal id only, never email, never name. One function (`distinctIdFor`) decides what an identifier may be, so no caller can pass an email by mistake |
| 2026-10-25 | The analytics library is loaded by a dynamic `import()` only after a visitor accepts, rather than initialised opted out | `opt_out_capturing_by_default` still lets the library start and contact its host. Not importing it at all is the strongest guarantee of "nothing before consent": zero requests and not even the script |
| 2026-10-25 | Never `cookieless_mode`; Do Not Track and Global Privacy Control are checked by this app, not left to the library | The library's documentation says `on_reject` counts a visitor who rejects. `respect_dnt` covers Do Not Track only, so GPC is handled in `consent.ts`, and both win over an earlier accept |
| 2026-10-25 | Events pass a closed allow-list and URLs lose query strings and fragments in `before_send` | A real visit showed an unrequested `$pageleave` with scroll statistics, full URLs and the page title. Filtering at the last moment holds whatever else is added to the app |
| 2026-10-25 | `posthog-js` 1.438.7 pinned, installed with `npm install --legacy-peer-deps` | The newest release over two weeks old; npm 10.9.2 crashes on this tree without the flag (see the Vitest 4 row), and a normal `npm ci` from the lockfile was checked |
| 2026-10-25 | Vitest 3.2.7 to 4.1.12 (and with it Vite 7 to 8), plus a lockfile-only `npm audit fix`; `npm audit` now reports 0 vulnerabilities | The remaining audit findings were all in the test runner and the build tool's dev server (a prototype-pollution gadget leading to code execution in `tinypool`, path traversal in `@vitest/mocker`, and `launch-editor` and `zod` issues). They never ship in the app, but a critical one in the tool that runs on a developer's machine is still worth closing. I chose 4.1.12 over 5.x because 5.x needs Node 22 or newer and the image builds on Node 20. The 79 tests, lint and the production build pass, and the image builds and runs on Node 20. **npm 10.9.2 crashes on this upgrade** ("Cannot read properties of null (reading 'edgesOut')", in its peer-dependency resolver, with or without `node_modules`), so the lockfile was written with `npm install --legacy-peer-deps`; a normal `npm ci` from it works, which is the check that matters |
| 2026-10-25 | Each owner chart keeps a 640 px minimum width and its card scrolls sideways on a narrow screen, instead of scaling down | Found at 390 px: the chart is drawn on a 720 px canvas, and scaled into a 320 px card its axis labels were about 5 px tall. At 640 px they are 9.8 px. The two tables on the page already scroll sideways the same way. Checked by measuring the rendered label size in a 390 px frame |
| 2026-10-25 | Next.js 16.3.5 to 16.4.1 (and `eslint-config-next` with it), exact versions | `npm audit` listed critical and high advisories on 16.3.5 (remote code execution in image optimisation and `next/og`, an SSRF, a Server Actions denial of service, cache poisoning). 16.4.1 clears every `next` finding; the six that remain are in the dev-only test tooling. I chose 16.4.1 (released 14 Oct) over 16.4.3 (23 Oct) because `npm audit` was already clean and it is the older release. Checked by the 79 tests, lint, a production build and the running pages; no code changed |
| 2026-10-25 | The API documentation is served by a narrow route handler on the frontend that adds the owner's session, with an allow-list of backend addresses | A browser cannot send a bearer token when it loads a page, so the backend's Swagger UI could not be opened directly. The proxy reaches only the three specs and the UI's own files, GET only, so no address a visitor types can use the session on any other backend route. The backend still decides who the owner is; the frontend holds no owner logic |
| 2026-10-25 | The API documentation is read-only: no "Try it out" | "Try it out" would send requests from the browser. Through this proxy that would mean forwarding arbitrary requests with the owner's token, which is more surface to protect than reading the documentation is worth. Turned off in the backend's Swagger UI settings and checked by a test |
| 2026-10-25 | The documentation link is a plain `<a>`, not Next's `Link` | `Link` prefetches, and this address is a route handler, not a page |
| 2026-10-25 | The logs filter is a plain `GET` form with the level and id in the address, and the page range scopes the logs as well as the charts | Same server-rendered, bookmarkable pattern as Tally's and Path's controls, with no client state. One range row above everything keeps the charts, the sync table and the logs on the same slice (the dataviz skill's filter rule) |
| 2026-10-25 | `parseLogFilters` drops a bad level or id before the call, and the page says when an id was dropped | The backend refuses them with a 400, which would show as an error page for something the owner typed. Dropping it keeps the page up and the note says why the filter did not apply |
| 2026-10-25 | Trace links render only for `http` and `https` addresses, open in a new tab with `noopener noreferrer` | The address comes from the backend's configuration, but a link built from data is checked before it is shown |
| 2026-10-25 | The owner's charts are hand-rolled SVG in `WatchtowerChart`, with the geometry in `lib/chart.ts`, and use eight new `--series-1` to `--series-8` colours | Same reasoning as `GoalHoursChart` (no charting library for line charts). The eight colours are the dataviz skill's validated set for light and dark; the existing three `--goal-line` colours are reserved for the goal chart and are too few for up to eight routes |
| 2026-10-25 | Chart lines break where more than two steps of time have no point | Prometheus omits a time with no value. A line across the hole draws values that were never measured, which I saw in the first render |
| 2026-10-25 | A series' colour follows its name, not its rank | The 8 series kept are sorted by peak, so ranks change from one load to the next. A colour tied to rank would repaint a route each time |
| 2026-10-25 | The sync table shows "—" for failures in a row when the backend has no value, never 0, and "Never" for a missing time | A source that has not run since the backend started is not a source with no failures. Showing 0 would read as healthy. The page also prints a note that failures in a row restart with the backend while last success and the 24-hour counts do not |
| 2026-10-25 | The watering holes page's map of source labels moved to `lib/sourceLabels.ts` (`measurementSourceLabel`) and the sync table reads it too | The table needs the same six names ("UMWAYI hub", "Health data", and so on). A second copy would let the two pages drift. The page behaves as before |
| 2026-10-25 | `/watchtower` calls `notFound()` for a signed-out visitor too, with no redirect to `/login`, and has no sidebar link | Prompt 3: everyone but the owner gets a plain 404. A redirect to `/login` would tell a stranger the page exists. The backend already answers 404 for both cases, so the page passes that answer on and holds no owner logic of its own. The owner types the address; a link would show the page to everyone |
| 2026-10-25 | `fetchServices` returns `null` on a 404 and rethrows anything else | A backend outage or a 500 shown as a 404 would hide a real fault from the one person who needs to see it. Two tests pin both halves: the 404 gives `null`, and a 500 or a network error is thrown |
| 2026-10-21 | A signed-out visitor is redirected to `/login` from every personal page, instead of seeing the founder's data | Before per-user scoping, `/today` rendered the founder's own routine, goal names and hours for anyone. Reusing the redirect Granary and Profile already had (`requireSessionUserId`) keeps one rule for "this page is about a person" |
| 2026-10-21 | `withUserId` is a small pure function with its own tests, and every personal call goes through it | There are over twenty personal calls across pages, a helper module and eight Server Actions; hand-building `?userId=` in each is how one gets forgotten, and a forgotten one silently shows the unowned legacy rows. The helper also replaces an existing `userId` rather than appending a second |
| 2026-10-19 | The M-Pesa import form is a client-side `MpesaImportForm` component, not a server-rendered form always visible on the page | The real reason is `useActionState`'s need for a client component to show the import's own pending/error state (the password prompt, the "couldn't read this" message) — this is the one piece of Phase 11 that genuinely needs client-side state, unlike every other granary control, which stays server-rendered |
| 2026-10-19 | The M-Pesa import form opens in a native `<dialog>` modal (`showModal()`), not an inline expanding card | User feedback: the inline card was "hidden" in the sense of not reading as a real dialog and shifting the page layout around it when opened. A native `<dialog>` gets a true modal with backdrop, focus handling, and Esc-to-close for free, with no extra dependency. The form closes itself automatically on a successful submit (tracked via a `submittedRef` so the initial render's `error: null` state isn't mistaken for a just-completed success) but stays open to show the inline error on failure; clicking the backdrop also closes it |
| 2026-10-19 | `backendFetchMultipart` is a new, separate helper in `lib/api.ts`, not a flag on the existing `request()`/`backendFetch` | The existing helper always sets `Content-Type: application/json`; a multipart upload needs fetch to set its own `Content-Type` with the correct boundary from the `FormData` body, which means never setting that header manually at all — a flag that conditionally skips a header is more error-prone than a second, explicit function with a different contract |
| 2026-10-19 | The "Where it went" category-totals card only renders once at least one category total exists, rather than always showing with empty/zero bars | An import hasn't happened for most users most of the time; a permanently-visible chart of all zeros would look broken rather than simply "nothing imported yet," and the reference itself only shows this card because its sample data always has an import behind it |
| 2026-10-19 | `Shell`'s phone tab bar changed from Today/Path/Reading/Tally/Hillside to Today/Reading/Granary/Tally/Hillside, matching the updated design reference exactly | Granary now exists, removing the reason the old order was kept (the reference's own target order needed a page that didn't exist yet). Path's "opens from Today" cross-link still isn't built, same already-flagged gap this doc records for Flock/Watering holes |
| 2026-10-19 | The granary's "Decline"/"Proceed" buttons are shown on every undecided pause, not gated behind `dueForTwentyFourHourFollowUp` | The design reference's create-pause form has both "Wait 24 hours" and "I've already decided" as alternate submit buttons; both create the identical entry (there's no backend distinction for "already decided" — see `backend.md`'s decisions log), so "I've already decided" needs the decide controls to already be visible on the new row, not hidden until a 24-hour timer passes. The still-waiting case keeps a "Waiting · {date}" hint alongside the buttons so the timing isn't lost |
| 2026-10-19 | `Shell`'s sidebar no longer shows the signed-in email + "Sign out" row; the sidebar's only account-related control is the Profile link (avatar + "Profile"), and "Sign out" moved onto `/profile` itself | User flagged that the email/sign-out row no longer matched the design reference's sidebar, which only ever shows the avatar/name "Profile" link (`docs/design-reference/pages/profile.html`'s `.sidefoot`/nav). Signing out is now a real action on the one page that's about the account, not a second place doing the same job the sidebar used to |
| 2026-10-18 | Profile is its own page/route with a real `GET /api/users/me/profile` fetch, not folded into the existing `me` fetch `RootLayout` already does for the sidebar | The sidebar's existing `fetchShellContext()` call is on every single page load and only needs `email`/`hillsideOptIn`; adding a second backend round trip there for data only the Profile page itself uses would slow down every page for a fetch 99% of page loads never use |
| 2026-10-18 | Interest/learning-preference pick-chips are real `<input type="checkbox">` elements styled via a CSS `:has()` selector (`.pick label:has(input:checked)`), not client-side state | Matches this app's established server-rendered-everything pattern (no client component, no `useState`) — the same reasoning the hillside's `.switch` and every segmented control already follow. `:has()` is supported in every browser this app already targets (the design reference's own pages assume a similarly modern baseline) |
| 2026-10-18 | The "Explain it my way" switch uses a new `.toggle-track` CSS class, not the hillside's existing `.switch` | `.switch` toggles on a server-set `aria-checked` attribute (correct for a Link/form-submit toggle like the hillside's opt-in); Profile's switch is one field inside a larger form submitted all at once, so it needs to reflect the checkbox's own live `:checked` state via Tailwind's `peer` selector instead — a different mechanism for a different kind of toggle, not a style choice |
| 2026-10-18 | Today's analogy box uses the first interest in the user's list (`profile.interests[0]`), not a selector to choose among several like the design reference's own in-page "explain through: Football / Philosophy / Skating" switcher | The task's explicit wiring instruction was "the Today staff card shows the analogy line," not a full interactive switcher; the reference's own switcher lives on the Profile page's preview section, which this app didn't build since nothing in the task asked for a second, duplicate live-preview surface beyond Today's own card |
| 2026-10-18 | `intelligence-service/Dockerfile`'s explicit `COPY main.py suggestion.py ... assess_note.py` list needed `analogy.py` added by hand | Found via live Docker verification: the container crash-looped with `ModuleNotFoundError: No module named 'analogy'` even though the file existed in the repo and all tests passed locally, because the Dockerfile copies named files, not the whole directory. Fixed and re-verified the container actually starts, not just that the code imports locally |
| 2026-10-18 | Phase 9: `/hillside` rebuilt entirely for the credit-based hillside v2, replacing Phase 8's days-shown-up version in place (same route, same file) | The design reference's `hillside.html` fully replaces the old layout — season/band/returns/ledger are a different feature, not an addition alongside the old one. Phase 8's backend `GET /api/hillside`, `HillsideService`, and their tests are deliberately left in place, just no longer called from this page — see `backend.md`'s open questions for the cleanup this leaves open |
| 2026-10-18 | The goal segmented control lists each signed-in user's own real goals (`GET /api/goals?userId=`), not the reference's fixed "Coding/Reading/Learning/Writing/Movement" category buttons | Same precedent as every other page in this app showing real data instead of the reference's sample copy; this app's real goals are milestone-style (`cmu-masters-application`), not activity categories, so inventing fixed category buttons would misrepresent what a credit is actually for here |
| 2026-10-18 | The hide/show control is a real `.switch` (role="switch", `aria-checked`), not a text link | The design reference shows this as a prominent toggle, not a link; copied `.switch`'s CSS from the reference's own `phone-hillside.html` stylesheet almost verbatim — its on-state green already matched this app's existing `--tended-ink` token exactly, so no new colour was needed. Still a plain `<form>`-submitted `<button type="submit">`, no client JS, same server-rendered pattern every other toggle in this app uses |
| 2026-10-18 | "Season N" in the eyebrow is computed from a fixed arbitrary epoch Monday (whole weeks since 2026-01-05), not a stored season number | Purely cosmetic, matching the reference's own "Season 42" flavour text; a real stored season counter would need its own table and migration for a number nothing else in the product depends on |
| 2026-10-18 | The band card shows "On the hillside you are {pseudonym}" (reading it off the `neighbors` row where `isYou` is true), not a separate backend field for "your own pseudonym" | The pseudonym is already in the response, in the one row that's always yours when you have a score; adding a redundant top-level field to `HillsideV2Response` would duplicate data the client can already read off the list it's already fetching |
| 2026-10-18 | The ledger's "Question this" is a link per row, not the reference's single page-level "Question a credit ›" link | The backend's `question` endpoint operates on one specific evidence event id; a single generic link wouldn't know which credit to question. Matching the reference's exact single-link placement would mean building a picker UI for no real gain — a per-row link is simpler and unambiguous, logged here as a deliberate functional divergence per `design-reference/README.md`'s own instruction to record one |
| 2026-10-18 | `Shell` returns a bare, chrome-free `<main>` for `/login` and `/register`, instead of these two pages rendering inside the normal sidebar/phone-tab-bar layout | User flagged the nav as still visible on both pages. Every link in that nav leads to data scoped to an account that may not exist yet, and on phone the floating tab bar would sit over a two-field form for no reason; `Shell` already reads `usePathname()` for active-link state, so the same hook decides whether to render any chrome at all, with no new route-group or layout file needed |
| 2026-10-18 | Real password login built (`/login`, `/register`, `lib/auth/jwt.ts`, `lib/auth/session.ts`, `lib/auth/actions.ts`), GitHub/Google sign-in left pending | User asked for password login first, OAuth deferred explicitly. `jwt.ts` mints/verifies the same HS256 `{sub, exp}` shape the backend's `NimbusJwtDecoder.withSecretKey` already expects (proven in `UserControllerApiIntegrationTest`), using Node's built-in `crypto` rather than a new dependency (no next-auth) — `AUTH_JWT_SECRET` must stay identical between `backend` and `frontend` in `docker-compose.yml`. The session is an httpOnly cookie set via Server Actions (`loginAction`/`registerAction`/`logoutAction`), not client-side token storage |
| 2026-10-18 | `/login` and `/register` render nested inside the global `Shell` (sidebar still visible), not a separate unauthenticated layout | Every page in this app already works without login (demo mode); there's no logged-out-only section to carve out, and the reference has no login UI to match either, so these two pages are styled from the app's own `.page-title`/`.card`/`.field`/`.btn-fill` tokens for consistency rather than a new visual language |
| 2026-10-18 | Shell's sidebar footer shows the real signed-in email + a "Sign out" button when a session exists, or a "Sign in" link otherwise | `RootLayout`'s `fetchShellContext()` now also reads the session cookie and calls `GET /api/users/me` (caught, not hard-failed — an expired or tampered cookie degrades to "not signed in" exactly like no cookie at all) |
| 2026-10-18 | Live-verified register → login → logout round trip, plus the wrong-password error path, in a real browser against the rebuilt Docker stack | `resize_window`'s unreliability (noted below) doesn't affect desktop flows; this was a correctness check of the actual session cookie / redirect behaviour, not a visual one. Confirms `/register` creates a session and redirects to `/today`, "Sign out" clears it and redirects to `/login`, `/login` with correct credentials signs back in, and wrong credentials show "That email or password doesn't match an account." inline without creating a session |
| 2026-10-18 | Hillside's `asUserId` is now derived from the real session when one exists, completely replacing the `?asUserId=` query param for a signed-in user, rather than the session merely being an additional option alongside it | Letting a signed-in user's own URL-editable `?asUserId=` override their real session would mean a logged-in person could still browse to see any other user's band just by changing the query string — the thing the demo-mode param was always flagged as not safe for (see `backend.md`'s decisions log). The param still works exactly as before for a signed-out visitor, so the page stays checkable without an account |
| 2026-10-18 | Hillside's opt-in toggle (`setHillsideOptIn` in `lib/actions.ts`) is a single Server Action taking a hidden `optIn` field, not two separate join/leave actions | The button already knows the current state (`me.hillsideOptIn`) to decide its own label and the value of the hidden field; a single action handling both directions needs no branching on the caller's side, matching the backend's own single `PUT .../hillside-opt-in` endpoint shape |
| 2026-10-18 | The opt-in toggle and its band-card messaging only render when a session exists (`me !== null`) | Opting in with no real identity makes no sense — there would be no user id for the backend to flip the flag on. Signed out, the page keeps its older demo-mode copy pointing at `?asUserId=` and a "Sign in" link instead |
| 2026-10-18 | Pseudonym rotation needed no frontend change at all | The rotation is entirely a backend hashing detail (`HillsidePseudonym.forUserId` now folds in the current season) — the frontend already just renders whatever pseudonym string the API returns, so there was nothing here to update |
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
| 2026-10-17 | Path reads existing tasks per day (`GET /api/tasks?date=`), never `materializeDay`, for the week view | `materializeDay` creates tasks as a side effect; calling it for six days nobody has opened (especially future days) would prematurely materialize a week ahead of schedule, which the single-day Today page was never meant to trigger in bulk |
| 2026-10-17 | Path's status dots don't show real durations per task (the reference shows "48 min", "3 h 10") | That data lives on each day's `DailyLog.measuredMinutes`, not on `Task` itself; wiring a second fetch per day to join it in was judged not worth the complexity for this pass, logged as an open question instead of silently dropped |
| 2026-10-18 | Flock's routine-item rows use `lib/routineIcons.tsx`'s keyword-matched icons (copied from the reference's per-routine SVGs), not a coloured initial letter | A first pass used a plain initial square, reasoning real routine items are free-text names the app can't map to a meaningful icon automatically — but the user flagged this as a visible fidelity gap on review. Matching on common keywords in the name (sleep, lifting, walk, reading, journal, deep work, ...) with a per-category fallback icon covers this project's own seed data and most real flocks, without needing a name the reference never showed |
| 2026-10-17 | Tally's notches are grouped in fives via nested flex containers, not a single flat row | Matches the design reference's own grouping exactly, and reads better at a glance for a window that can be 28+ days |
| 2026-10-17 | `docs/design-reference/` copied into the repo and `compare.mjs` run against the live app, per the reference's own `README.md` | Needed to actually verify the fidelity passes above against real screenshots, not just by re-reading HTML source; found and fixed three real gaps: Flock and Watering holes were listing every Phase 8 demo user's own routine items (`GET /api/routine-items` with no `userId` returns everyone's, not just the unowned/legacy ones this single-user page is for), Path was missing its "This week so far." summary card entirely, and routine-item source chips (Today, Flock, Watering holes) printed the raw `RoutineItemSource` enum value instead of a human label |
| 2026-10-17 | A shared `lib/sourceLabels.ts` (`sourceLabel()`), not a label map duplicated per page | `TaskCard`, Flock, and Watering holes' "how each activity is counted" table all need the same `RoutineItemSource` → human label mapping; Watering holes' own `SOURCE_LABELS` for `WateringHoleStatus.source` (a different enum, `MeasurementSource`) stays separate on purpose — "Health data" and "Health Connect" are deliberately different labels for the same integration in two different contexts |
| 2026-10-17 | (Superseded 2026-10-21, see the per-user scoping rows below) Flock and Watering holes filtered `routine-items` to `userId == null` before rendering | At the time, Phase 8's hillside demo users' items came back in the same flat list, so the pages kept only the unowned ones. Since per-user scoping part 2 the list endpoint returns only the signed-in user's own items, so there is nothing left to filter and the filter would now hide a user's own routines |
| 2026-10-17 | Path's "This week so far." card (tended/wandered counts, days shown up) was built from the same per-day template/task data the grid already fetches, not a new endpoint | The reference shows exactly this card; the grid loop already computes each day's tended/wandered status to draw its dots, so the summary is a straightforward reduction over data already in hand, not new data |
| 2026-10-18 | Second design-fidelity pass, this time comparing the live app against the reference directly in a browser (not just screenshots), after the user reported "a lot" of remaining mismatches | Found real gaps the first screenshot-only pass missed: Flock had no table structure at all (just flat rows, no column headers, no chevron) and used initial-letter icons instead of per-routine ones; `TaskCard` still showed an extra "Start"/"Mark tended" button the reference never has, alongside the correct check circle; Tally had no "4 weeks"/"Since the start" toggle, its stat tiles used `.tile` (`--surface-2`) which is the exact same colour as the page's own canvas background, making them invisible, not just flat; `GoalHoursChart` had no axis labels or inline end-of-line goal labels, just bare lines; Reading's two sidebar cards (`How a note is read.`, `Your notes stay yours.`) used `className="card"` with no padding class, so their text touched the rounded corner; and `.chip` had no `white-space: nowrap`, so any chip with enough text (a long chapter label, for instance) wrapped mid-pill. All fixed in this pass; see the surrounding entries |
| 2026-10-18 | `.chip` gained `display: inline-flex; align-items: center; white-space: nowrap` | Matches the design reference's own `.chip` rule; without it, a chip's text could wrap onto a second line inside the pill, found on the Reading page's "Ch. 13"-style chapter chip once real (longer) `chapterPlaced` text was tried against it |
| 2026-10-18 | Reading's earlier-notes chapter chip shows `shortChapterLabel()` (extracts "Ch. N" from the start of `chapterPlaced` via regex, or falls back to the full text) | `chapterPlaced` is a free-text guess from the intelligence service's assessment, not a structured field, so it can be arbitrarily long ("Chapter 7, The Habit Stacking"); the reference's own chip is always a short "Ch. N" tag, and guessing a chapter number out of the free text is the closest honest match without inventing a new structured field |
| 2026-10-18 | `TaskCard`'s check button now toggles directly between `TODO`/`IN_PROGRESS` and `DONE`, with no separate "Start"/"Mark tended" button | The reference's check is a single binary toggle, nothing else, on every row; this app's `Task` still has a real three-state status (`Path` and task materialization rely on it), but Today's own UI only ever needs the two ends of that chain, matching the reference exactly instead of exposing a flow it never shows |
| 2026-10-18 | Tally's `windowDays`/`sinceStart` live in the URL (`?window=all`) as `Link`s, same pattern as the hillside's field/window controls and Path's week navigation | Keeps every toggle on this app server-rendered and bookmarkable rather than introducing client state for just this one page |
| 2026-10-18 | `/tally`'s "since the start" is a real `sinceStart` param computed from the earliest actual daily log (`tally.py`'s `since_start`), not a very large fixed `windowDays` | A huge fixed window (e.g. 10 years) would make `compute_tally` generate one `DayShownUp` entry per day back to a date nothing ever happened — thousands of meaningless notches on the tally stick. Windowing from the real earliest log date matches what "since the start" actually means and keeps the stick a sane length |
| 2026-10-18 | Tally's "Reading progress" stat reads `GET /api/books/current` (the `Book` entity), not the legacy `GET /api/current-reading` | Both existed and could show different books — the Reading page has shown `Book`-sourced data since Phase 7, so Tally quoting the older, separate `current_reading_log` source was a real, confusing inconsistency once Flock's "Currently reading" box (the only remaining UI that wrote to it) was removed, see below |
| 2026-10-18 | Removed Flock's "Currently reading" box and its `CurrentReadingForm` | Not in the design reference's Flock layout, and it edited a different data source (`current_reading_log`) than the Reading page's own current-book flow (`Book`), which is confusing, not just a cosmetic gap — a user editing "Currently reading" on Flock wouldn't see it reflected on the Reading page at all. The backend's `/api/current-reading` endpoint and `CurrentReadingForm` component are left in place, just unused by this page now |
| 2026-10-18 | `GoalHoursChart` rewritten with axis gridlines/labels and inline end-of-line dot + label per goal, matching `docs/design-reference/pages/tally.html`'s chart exactly, instead of a bare SVG polyline plus a separate legend table | The original chart had no way to read actual values off it at a glance; the reference's inline end-labels (`goal name · N h`) are also the legend, so the separate table was dropped as redundant once the chart carries that information itself |
| 2026-10-18 | `CorrelationStatTiles` restyled to this app's own `.card`/token system; `components/charts/viz-tokens.css` deleted | This component (Phase 2, pre-redesign) was still using a separate "dataviz skill" palette (`--sequential`, off-white `--surface: #fcfcfb`) that visually didn't match anything else in the shepherd-themed app, the one obviously "wrongly designed" section the user's report was pointing at. Not in the design reference at all (it predates Phase 5), so there is no reference layout to match — it now at least looks like it belongs in this app |
| 2026-10-18 | Watering holes gained a static "Reader's notes" card (always `Built in`, not read from `/api/watering-holes`) and a "Still to work out together." card with this project's own real open questions | Both are in the reference but were missing. Reader's notes isn't a toggleable external sync the way the other sources are, so it's hardcoded rather than faked as a `WateringHoleStatus` row. The "still to work out" list uses this project's actual open items (hillside opt-in has no UI yet, pseudonym rotation, Path's missing real durations) rather than the reference's own sample questions (M-Pesa, Strava), which don't apply here |
| 2026-10-18 | Sidebar's Flock link shows a routine-item count badge, same place/style as Today's "N of M" | Matches the design reference's sidebar exactly; added a `routineCount` field to `fetchShellContext()` in `layout.tsx`, filtered to unowned items the same way Flock's own page already is |
| 2026-10-18 | Third design-fidelity pass: user reported the fixes so far still didn't match, specifically "the notches height is not same as in the reference missing icons padding between text and border is not there" | Pulled exact pixel values from the reference's own CSS this time instead of eyeballing screenshots. Found the tally stick notches were rendered as 16×8px rounded pills in `--tint` orange (`h-4 w-2 rounded-full`), when the reference's `.n` is 5×38px with 3px corners, solid `--ink` (near-black) for every shown-up day, and orange (`--tint`) *only* for today's own notch (`.n.today`) — a real colour and proportion bug, not just a sizing nit. Also found a second `className="card"`-with-no-padding instance (`app/hillside/page.tsx`'s "Why this hillside is calm" aside), missed by the first pass's literal-string grep because this one had other classes alongside `card`; fixed by grepping for the `card` class generally, not the exact string, and auditing every match |
| 2026-10-18 | Tally stick notches: `--ink` for every shown-up day except today (`--tint`), 5×38px, 3px radius, dashed-border-only for a missed day (no fill), 5px gap within a group of five and 18px between groups | Matches `docs/design-reference/pages/tally.html`'s `.n`/`.n.today`/`.n.miss`/`.grp`/`.notches` rules exactly, pulled from the reference's own CSS rather than approximated from a screenshot |
| 2026-10-18 | Today's "Closer than yesterday." card gained a real progress bar per goal and an "Open the tally ›" link, replacing a plain two-column text list | Matches the reference's `.goalrow`/`.bar` structure. No real per-goal target exists to size a bar against (the reference's own widths are sample-data flourishes), so each bar is sized relative to whichever goal here has the most hours, not a fabricated cap — an honest relative comparison, not invented absolute progress |
| 2026-10-18 | Today's "The staff" card restructured: a `chip-tint` "The staff" badge above an `<h2>` naming the actual suggested topic, not "The staff" itself as the heading with the topic as plain body text | Matches the reference's `<span class="chip tint">The staff</span><h2>{topic name}</h2>` structure. The reference's own "Start learning"/"Another topic" buttons were deliberately not added: they have no real destination or behaviour in this app (no learning-session page, no alternate-suggestion endpoint), and the reference itself never wires them to anything either (`app.js` only handles Today's checks and Hillside's switches) — copying their visual presence without giving them real behaviour would be the "half-finished implementation" the project's own rules warn against |
| 2026-10-18 | Hillside's "Why this hillside is calm" rules each gained the reference's own icon (bar-chart, person, water-drop, plus) | These were plain text with no icon at all; copied the exact SVG paths from `docs/design-reference/pages/hillside.html`'s `.rule` rows |
| 2026-10-18 | Flock gained the reference's "Private stays private." card (static copy, placed after the routine-items table) | Was entirely missing. The reference's neighbouring "Learning topics, by green pasture." card (topics grouped under their goal, inline) was deliberately not replicated as a second, redundant display — Flock's existing "Learning topics" section already lists every topic with its goal name, and doubles as this app's real create-a-topic form, which the reference's static page doesn't need |
| 2026-10-18 | Fourth pass: audited every page against the reference's exact CSS values (colours, radii, box-shadows, font stack), not just structure | Found several real, pixel-level mismatches a structural comparison alone wouldn't catch: progress bars everywhere (Today's "Tended today", goal bars, Reading's chapter bar) were `rounded-full` in a translucent `--fill` grey instead of the reference's flat `4px`-radius `#E8E8ED` track; every segmented control's active tab (Path's week toggle, Tally's window toggle, Hillside's field/window toggles) was missing its `box-shadow`, making the active state read as just a colour change instead of a lifted tab; the body font stack and `h1`/`h2` letter-spacing/line-height didn't match the reference's own `-apple-system,...,"SF Pro Text",...` stack and type-scale numbers; Hillside's histogram bars used `--fill` and square corners instead of solid `#D2D2D7` with `6px 6px 2px 2px` radii; and the marked-quote `<mark>` in Reading used the orange `chip-tint` colours (tinted text) instead of the reference's dark-ink text with a dotted orange underline |
| 2026-10-18 | A global `a { color: var(--tint-text) }` rule was added, then reverted within the same pass | Matching the reference's own global `a{color:#9A5208}` seemed like the right fix for under-styled links, but Tailwind v4 emits utility classes (including `.text-ink`) inside a CSS layer, which loses to any unlayered plain-CSS rule regardless of selector specificity — the new rule silently overrode every `.text-ink` link (Path/Tally/Hillside's segmented-control tabs, Shell's brand link), turning text that should stay dark into orange. Reverted; every link that should be `--tint-text` already carried that class explicitly, so no global rule was needed in the first place |
| 2026-10-18 | Hillside's "walking near you" rows gained a real rank number and a data-derived caption ("3 days ahead of you" / "Same pace as you" / "N days behind you"), in a 4-column grid matching the reference's `rank \| who \| dots \| detail` layout | The reference shows a rank number and a hand-picked caption ("Ahead by a day", "Steady for 6 weeks") per row, neither of which this page had. The rank is now a real backend field (`NeighborRow.rank`, the row's 1-indexed position in the full ranked list — see `backend.md`). The caption is computed from each neighbour's real `daysShownUp` compared to yours, not invented flavour text like the reference's own "Steady for 6 weeks," which has no real tracked basis in this app |
| 2026-10-18 | Fifth pass: restored visible text and controls a structural/CSS comparison still missed, after the user pointed at a specific example ("your flock today 3 of 9 tended... Start learning and Another topic button missing") and asked for the same check on every page | Today was missing its "Your flock today" heading + "N of M tended · in the order you walk them" line entirely, the "Rows with a source chip fill themselves in..." footnote, and the "Start learning"/"Another topic" buttons on the staff card. Path was missing weekend columns' "Still waters" label. Watering holes' sidebar link was missing its "N on" connected-count badge, matching the one Flock already had. Reading was missing its "Save draft" button |
| 2026-10-18 | Tally's "Cumulative hours per goal" and "Per-routine meters" sections were rebuilt to match the reference's real content, not just relabelled | The reference's chart section is headlined "The green pastures are closer." with an explanatory paragraph, not a bare "Cumulative hours per goal" label. Its per-routine list is headlined "How each sheep is doing." with an actual progress meter bar per row (`.mrow`/`.meter`, a dark-ink fill against a light track) — this page had a plain list with only a coloured status word, no bar at all. Its "Steep days" and "Learning and the rest" sections turned out not to be raw data lists in the reference at all, but two interpretive insight cards (chip badge + a generated headline + a sentence), which this page had never built — it showed a bare list of skip-heavy dates and two disconnected stat tiles instead |
| 2026-10-18 | "Tuesdays are the hardest climb." and "On learning days you tend more." are computed from real data (`steepDayInsight`/`learningInsight` in `app/tally/page.tsx`), not copied as static reference text | The reference's own headline and numbers are sample-data flavour, specific to its fictional week. `steepDayInsight` finds which real weekday `patterns.skipHeavyDays` cluster on and states the real count; `learningInsight` compares the real `topicRoutineCorrelation` averages and picks "tend more"/"tend less"/"look like any other day" accordingly. `CorrelationStatTiles` (the old two-bare-tiles component) was deleted, now folded into this computation |
| 2026-10-18 | Reading's "Save draft" is a real feature (per-book `localStorage`, loaded via `useSyncExternalStore` the same way `ThemeToggle` reads `localStorage` — see the entry above on why not `useEffect` + `setState`), not a decorative copy of the reference's button | A dead button that looks like it saves something but doesn't would be worse than no button; the draft needs no backend change, survives a reload, and clears itself once the note is actually submitted |
| 2026-10-18 | Today's "Start learning"/"Another topic" buttons were added back as plain, unwired `<button type="button">` elements | Reversed an earlier decision to omit them as a "half-finished implementation." The user explicitly asked for them back for visual parity, and — same as before — the reference itself never wires them to anything either (`app.js` only handles Today's checks and Hillside's switches), so matching that exact level of (non-)interactivity isn't adding a fake feature, it's matching the mockup precisely |
| 2026-10-19 | `/cohort/[cohortId]` gets its own sidebar, swapped in by `Shell.tsx` itself (not just the page content living outside the main nav) | First shipped with the cohort page simply using the normal personal-app sidebar, which the user caught live ("the menu does not change to match the cohort menu from design reference") — the design reference's `cohort.html` has a completely separate `<nav aria-label="Programme admin">`, and the first version's own stated reasoning for keeping the page "outside Shell's nav" never actually followed through on building that separate nav. Fixed using the same pathname-branching pattern `Shell` already had for `/login`/`/register`'s different chrome, rather than restructuring the route into its own layout |
| 2026-10-19 | The cohort dashboard page is read-mostly this slice: no UI for creating an organisation/cohort, bulk CSV invites, or setting a seat limit, only the survey form writes anything | Those admin actions are a separate, later part — this slice's job was the dashboard view itself (all five backend pieces already built in prior parts). Reachable today only via the API directly; a real admin needs the backend's existing endpoints until that UI exists |
| 2026-10-19 | A real bug found live-testing before reporting: an org admin who isn't also a cohort member crashed the page when submitting the survey | The backend correctly 403s a non-member's survey answer (it's a learner's question, not an admin's), but `submitCohortSurveyResponse` didn't catch it, so `ApiError` reached the Server Action unhandled. Fixed by catching and swallowing a `403` specifically (any other error still surfaces) — the admin's click has no visible effect instead of crashing, which is correct since the backend already decided they're not eligible to answer |
| 2026-10-19 | A second real bug found the same session: the survey/seat-limit `POST`/`PUT` calls threw `"Unexpected end of JSON input"` even for an eligible learner | Both backend endpoints returned a plain `200` with an empty body (`void`-returning `@PostMapping`/`@PutMapping`), but `lib/api.ts`'s `request()` only skips `response.json()` on a `204`. Fixed on the backend (`@ResponseStatus(HttpStatus.NO_CONTENT)` on both `OrganisationController` methods) rather than loosening the frontend's parsing, since a write with no response body should honestly be a `204`, matching how the rest of this app already treats empty responses |

## First step and sign-up time zone (staged Prompt 6, part 3)

- **First-step card on Today.** A new account with no routines sees "Add your first routine" with a link to Flock. With routines but no schedule it sees "Put it on the path". Once a routine is scheduled the card goes. The rule is `lib/firstStep.ts` (4 tests); the card is on `app/today/page.tsx`. Other pages still say only "None yet" (Backlog L).
- **Time zone at sign-up.** The register form sends the browser's zone (`AuthForm` with `sendTimeZone`, a hidden field filled after load). `registerAction` keeps it only if `registrationTimeZone` (`lib/timeZone.ts`, 5 tests) says it is a real zone, then sets it on the profile after the session starts. A failure there never blocks sign-up. **The backend does not validate a zone's name**, so a bad one typed on Profile still breaks the Granary (Backlog L).
- **Favicon.** `app/icon.svg` is the shepherd's crook on the amber tile, the same mark as the sidebar logo. The default `favicon.ico` was removed so browsers do not prefer it.
- **Checked** (25 Oct 2026, Chrome, a throwaway account on `127.0.0.1`): the card shows for a new account; a fresh sign-up shows `Africa/Nairobi` on Profile (browser zone), an account made before the change shows `UTC`; the page's icon link points to `/icon.svg`. **Not checked**: the icon's look in the browser tab itself (I read the link, not the tab strip), the card at phone width, the card in light theme.

## Goals on Flock (staged Prompt 6, part 4)

- **A goal can now be created in the browser.** Flock has a "Goals" section above Learning topics: the list, a first-step line when empty ("A goal is what your hours add up to"), and a form (name, optional "Why it matters"). The action is `createGoal` in `lib/actions.ts`: it trims the name, does nothing for a blank one, sends `POST /api/goals`, then queues `goal_created` (no properties) **only after the backend accepted it**. The backend already scoped goals to the signed-in user.
- **The eighth event is wired**, so every named analytics event is now sent. `EVENT_DISCLOSURE.goal_created.sentToday` is true, and the privacy page hides its "not sent yet" list when it is empty.
- **Known rough edge**: a goal name the person already has makes the backend answer 400, and the action does not catch it, so they see the app's error page. There is no friendlier message yet.
- **Checked** (25 Oct 2026, Chrome, throwaway account on `127.0.0.1`, analytics declined): adding "Learn Rust" listed it, and the `rm_events` cookie was cleared with nothing sent. 4 new unit tests (request body, trimming, blank name, backend refusal) and a deliberate break (removing the queue call fails one). **Not checked**: the accepted path sending `goal_created` to PostHog in a real browser (unit-tested only), the form at phone width, a duplicate name.

## Empty states, duplicate goal, bad time zone (staged Prompt 6, part 5)

- **Empty states name the next step.** Flock: routines ("Add one below: a small thing you can do most days"), learning topics (says a goal comes first when there is none), schedule templates ("Pick a routine, the day and how long it takes"). Path with no schedule shows a banner linking to Flock's schedule section. Tally's per-routine list says to add one in the Flock. Today already has the first-step card (part 3).
- **A duplicate goal name is caught before the click.** `lib/goalName.ts` (4 tests) returns the problem; `NewGoalForm` shows it and disables Add. The backend stays the authority (it still answers 400). The box clears after a goal is added.
- **A bad time zone is refused by the backend** (`User.validTimeZone`, used by `updateProfile` and `changeTimeZone`; HTTP 400 and the saved zone stays; test `aTimeZoneThatIsNotARealZoneIsRejectedAndTheSavedZoneStays`). Before this a name like `Mars/Base` was saved and then broke the Granary and the credit ledger for that person.
- **Known rough edge**: typing a bad zone on Profile now shows the app's error page ("This page couldn't load") instead of a message. Nothing is saved and the Granary still loads. A friendly message is not built (Backlog L).
- **Checked** (25 Oct 2026, Chrome, throwaway account): Path empty banner; typing an existing goal name shows the message and disables Add; adding a new goal lists it and clears the box; a bad zone on Profile gave the error page and the Granary still loaded. **Not checked**: Flock's other empty texts and Tally's in a browser (strings only, built and linted), phone width.

## Demo profile, frontend (staged Prompt 6, part 10)

The backend half is in `docs/backend.md`, "Demo profile". **Off by default**: the frontend reads `DEMO_PROFILE_ENABLED` (exactly `true` turns it on, `lib/demo.ts`) and needs `AUTH_INTERNAL_TOKEN` to ask the backend.

- **The button.** With the flag on, `/login` shows a "Try the demo" box ("Open a demo with made-up sample data. Nothing in it is real"). It submits `demoLoginAction` (`lib/auth/actions.ts`), which asks the backend to open the account with the internal token, then starts an ordinary session. `openDemo` (`lib/demo.ts`, 6 tests) returns null, and the person goes back to `/login?demo=unavailable` with a note, when the demo is off, the token is missing, the backend fails, **or the account is not flagged `demo`**. That last check means this route can never sign a real person in.
- **The label.** While signed in as the demo account, every page shows "Sample data. This is a demo account with made-up information. Nothing here is a real person's." (`DemoBanner` in `components/Shell.tsx`, fed by `demo` from `/api/users/me`).
- **Analytics skip it.** The demo account is never identified (`app/layout.tsx` passes no user id), so a demonstration is not counted as a person. A demo visitor still gets the same consent banner as anyone.
- **Checked** (26 Oct 2026, Chrome on `127.0.0.1`, flag on for one run then restored): with the flag off `/login` had no demo button and `demo-login` answered 404; with it on the button showed, clicking it landed on `/today` with the sample routines and the banner text above, and signing out worked. 209 frontend tests pass, tsc and lint clean. **Not checked**: phone width, the light theme, the banner on pages other than Today, the "unavailable" note (unit-tested path, not clicked), the demo with analytics accepted.
- **Not built**: a way to delete the demo account, and to reset its data to a clean state.

## The walk-through, before and after (staged Prompt 6, part 11)

Prompt 6 acceptance check 3: the Prompt 1 walk-through repeated, with screenshots before and after. **How the "before" was made**: I exported the commit just before per-user scoping began (`a75a1c5^`, the code the walk-through ran on), built it as a separate Docker project on other ports, and gave it three sample routines on today's schedule through its open API. **The sample rows are mine, not the founder's real data.** The old stack and its volume were removed afterwards.

| Item | Before (`docs/screenshots/`) | After |
|---|---|---|
| a. Signed-out visitor | `before-signed-out-today.jpg`: `/today` answers 200 and shows the schedule ("item 1, 2, 3") to anyone; the old API also returned the routines to a request with no token | `after-signed-out-today.jpg`: `/today` redirects to `/login`; the API answers 401 |
| b. A brand-new account | `before-new-account-today.jpg` and `before-new-account-flock.jpg`: a just-registered account sees the three routines, "3 routines, 3 goals", and no first step | `today-first-step.jpg`: an empty Today with "Add your first routine" and a button; `today.jpg` is the same account after a few routines |
| c. No goal creation in the UI | not shown (nothing to screenshot) | Flock has a Goals section (part 4); checked live |
| d. Tapping a task changes it for everyone | not re-reproduced | not re-tapped; covered by the access-control tests |
| e. Time zone defaults to UTC | not shown | a new account starts on the browser's zone (part 3); checked live |

**What this does not prove**: the "before" ran on made-up rows, not the real data; items c, d and e have no before image; the "after" screenshots are from this session's code (`4d24402` and later).

## The daily reminder on Today (staged Prompt 7, part 2)

- **One reminder a day, rotated by date.** `lib/reminders.ts` (11 tests): `reminderFor(list, "YYYY-MM-DD")` returns the same reminder all day, the next one tomorrow, and wraps after the last. The day number comes from UTC, so a time zone or daylight saving can never skip or repeat a day; a date that is not real gives no reminder. The reminder follows the date Today is showing, so "Previous day" and "Next day" show that day's reminder.
- **The text is a file you edit.** `frontend/content/reminders.txt`: one reminder per line, blank lines and lines starting with `#` ignored. It ships with the ten reminders from the staged prompt, in order. A test pins those ten and refuses an empty, over-long (more than 200 characters) or em-dash reminder. To change it: edit the file, then `docker compose build frontend` and `up -d frontend`. No code changes. The image copies the file beside the server (`frontend/Dockerfile`) and `lib/remindersFile.ts` reads it on each request; a missing file means no reminder, never a broken page.
- **Where it shows.** An italic line under the page title on `/today` (`data-testid="daily-reminder"`).
- **Checked** (26 Oct 2026, Chrome, throwaway account on `127.0.0.1`): Today showed the third reminder; `?date=2026-10-27` showed the fourth and `?date=2026-10-25` the second, so the order moves one a day. Screenshot: `docs/screenshots/today-reminder.jpg`. A deliberate break (always the first reminder) failed 3 tests. 220 frontend tests, tsc and lint pass. **Not checked**: phone width, the light theme, a missing or empty file in the running image, editing the file in a live container.
- **A stronger reminder after missed days (Miguel's answer to "necessity").** `missedRecently` (`lib/reminders.ts`): true when the person has shown up on some earlier day **and** none of the three days just before the date shown counted as shown up (a day counts at 3 or more tended, as on the Tally). **Never true for a new account** (no earlier day shown up), and days missing from the record are not guessed at. When true, Today shows one from the file's second list instead, rotated by date the same way. The file's `[after missed days]` heading starts that list (case and spacing do not matter); with no such list the daily reminder is used. The three shipped lines are the prompt's "smallest next piece" reminder and two I wrote ("A missed day takes nothing away. Do one small thing today." and "You do not have to catch up. Start with the smallest piece."), kept kind on purpose; **edit them freely**, a test only refuses shame and pressure words (lazy, failure, behind, guilt and a few more) and em dashes. 26 tests in `lib/reminders.test.ts`; two deliberate breaks (no history guard, a one-day window) failed 1 and 2 tests.
- **Checked live** (26 Oct 2026, throwaway account): before it had any full day on record, Today showed an ordinary daily reminder; after one full day (18 Oct) and two unfinished days since, today (26 Oct) showed "A missed day takes nothing away. Do one small thing today." (`docs/screenshots/today-reminder-after-missed-days.jpg`). **Not checked**: the viewed-date edge for future dates (they are not in the tally, so they always get the daily reminder), phone width.

## The weekly failure-mode card on Today (staged Prompt 7, part 5)

- **What it asks.** "Which failure mode did you meet this week?" with nine choices: the eight modes of `docs/understanding-first.md` in that document's order, each with its short sign, then **"None this week", last**, as a choice of last resort (Miguel's decision; the backend value is `NONE`). `lib/failureModes.ts` holds the list. Tests hold it equal to the backend's `FailureMode` enum and the names equal to the document.
- **When it shows.** The question is due from a weekday **the person chooses** on their Profile ("Ask me 'which failure mode did you meet this week?' from" a day of the week, Monday until they choose; Miguel's decision, 26 Oct 2026). From that day through Sunday it is on Today until this week has an answer; the next Monday it closes and waits again. After an answer it shrinks to "This week's failure mode: Progression. Change it" (a `<details>` that opens the same nine choices), whatever the day. `questionOpen` and `weeklyCard` in `lib/failureModes.ts` decide it; the date it uses is the date Today is showing. If the answer cannot be read, or the day name or date cannot be understood, the card errs on the side of showing, never hiding a question by accident.
- **The save.** `answerFailureMode` in `lib/actions.ts` sends `PUT /api/failure-modes/current` only for one of the nine values (anything else is ignored, nothing is sent), then refreshes Today. A backend refusal is not swallowed. **No analytics note**: the answer is personal and is not one of the eight named events.
- **Tests**: 26 in `lib/failureModes.test.ts` and `lib/actions.failureMode.test.ts` (the list, the document and enum matching, the value check, the card states, the day rule across a week and its boundaries, the action's request, refusals, and `updateProfile` sending only a real day). Four deliberate breaks (no value check; trusting any answer; `>` for `>=`; unknown day means closed) failed 1, 1, 6 and 1 tests.
- **Checked** (26 Oct 2026, Chrome, throwaway account): the card showed with all nine choices (`docs/screenshots/today-weekly-failure-mode.jpg`); clicking Progression saved it and the card shrank to "This week's failure mode: Progression"; it survived a reload; "Change it" opened the list, and "None this week" replaced the answer (one row in `weekly_failure_modes`, `NONE`). I then deleted the test row. **Not checked**: phone width, the light theme, keyboard use, a screen reader on the `<details>` control.
- **Checked, the day setting** (26 Oct 2026, Monday, throwaway account): with the default the card showed; after choosing Thursday on Profile and saving, the choice survived a reload and **Today (Monday) had no card**; `?date=2026-10-28` (Wednesday) had none; `?date=2026-10-29` (Thursday) had it. I then reset the account to Monday (by the database, because a browser step failed with a transient error). **Not checked**: that an already answered week still shows its answer on an early day (unit-tested only).

## "Today" is the person's day (Backlog P, part 1)

- **The problem.** The frontend server runs on UTC, so `todayIsoDate()` (`lib/schedule.ts`) gave the server's date. For someone in Nairobi (UTC+3), from midnight to 3 a.m. the Today, Path and Tally pages and the sidebar's count showed the previous day.
- **The fix.** `todayInZone(zone, now)` in `lib/userToday.ts` returns the date in an IANA zone with `Intl`, so daylight saving and the date line are the browser engine's job, not ours; a missing or unreal zone gives UTC. `getUserToday()` in `lib/auth/userToday.ts` reads the signed-in person's saved zone from their profile and returns that date, once per request (React `cache`); signed out, or if the profile cannot be read, it is the UTC date and the page still renders.
- **Where it is used**: Today's default date, the Path's week and its "today" mark, the Tally's "today" notch, and the sidebar's "N of M tended" count (`app/layout.tsx`).
- **Left alone on purpose**: the hillside's season start still uses the server date (`app/hillside/page.tsx`), so everyone shares one weekly board and a person in another zone does not see an empty board for a few hours on a Monday; and `NewAdHocTaskForm` runs in the browser, where `todayIsoDate()` is already the person's own date.
- **Not fixed yet** (Backlog P): the intelligence service's tally window and patterns use the server's date (`intelligence-service/main.py:213, 257`), so the Tally's days still end at the UTC date and its "today" notch can lag by a few hours for the same person; reading notes log the day on the server's date (`ReadingNoteService.java:78`).
- **Tests**: 8 in `lib/userToday.test.ts`. A deliberate break (ignore the zone) failed 3.
- **Checked** (26 Oct 2026, 05:36 UTC, throwaway account): on `America/Los_Angeles`, Today showed Sunday 25 October and the Path 19 to 25 October; on `Africa/Nairobi`, Monday 26 October. **Not checked**: the Tally's notch and the sidebar count by eye, phone width.

## Analytics (staged Prompt 5, parts 1 to 3 of 6)

Product analytics with PostHog, to learn how people move through the app **without collecting what the app promises to keep
private**. Built in parts. **What exists**: a consent banner, a gate that keeps the library unloaded until a visitor accepts, a
filter on everything that leaves the browser, identification by internal user id only, the session-replay decision (off), and a public
privacy page where a visitor can change their choice, and (part 3) the eight named events wired to the app, with a test that fails the build on a private field name. **Seen in PostHog's own Live events**: not by
me (I cannot open your PostHog); PostHog answered 200 to the two events my real browser sent, see "Checked against the real
project" below.

- **Off unless configured.** Set `POSTHOG_KEY` and `POSTHOG_HOST` in `infra/.env` (names only in `.env.example`; passed through
  `docker-compose.yml`; the key is **never** in a file in the repo). With either missing, or a host that is not an `http(s)` address,
  `analyticsConfigured()` is false: no banner, no library, no request. Read at runtime by the root layout, not baked into the build.
- **Consent first.** `components/AnalyticsConsent.tsx` asks once (Accept and Decline, equally prominent) and remembers the answer in
  `localStorage` (`rm_analytics_consent`). The rules are in `lib/analytics/consent.ts`, pure and tested: **Do Not Track and Global
  Privacy Control win** over an earlier "accept" and the visitor is not asked; anything else in storage counts as no choice.
- **Nothing before "Accept".** The library is imported dynamically (`lib/analytics/client.ts`) only after consent, so before it the
  code is not even downloaded. **A visitor who declines is not tracked at all.** I did **not** use PostHog's `cookieless_mode:
  'on_reject'`: the library's own documentation says it counts a visitor who rejects, in cookieless form, which contradicts the
  prompt, and a test fails if `cookieless_mode` is ever added. This is the check the prompt asked for ("do not trust this note").
- **What leaves the browser** (`lib/analytics/options.ts`, `sanitize.ts`, `events.ts`): no automatic click or form capture, no session
  replay (a later decision), no surveys, no feature-flag calls, **no page-leave event and no scroll statistics**, no cookies (the
  identifier is in `localStorage`), and **no person profile** until someone is identified. Every event goes through PostHog's
  `before_send`: **only these names pass: `$pageview`, `$identify`, `goal_created`, `day_logged`, `credit_questioned`,
  `reading_note_submitted`, `pause_logged`, `hillside_joined`, `hillside_left`, `cohort_dashboard_viewed`**; anything else is dropped
  (PostHog's automatic events, anything invented later); **every URL loses its query string and fragment** (here they can hold ids);
  **the page title is dropped**. A pageview still carries PostHog's standard device properties: browser and version, operating system,
  language, time zone, screen and window size, the user agent, a random device id and a random session id. No email, no name.
- **Identified by internal user id only (part 2).** When someone is signed in, and has accepted, the root layout passes their **internal
  user id** (a number) to the consent component, which tells the library "this is user 34" and nothing else: `identify` is called
  with the id alone, no email, no name, no person properties (`lib/analytics/identity.ts`, `client.ts`, 14 tests). `distinctIdFor`
  refuses anything that is not a positive whole number, so an email or a name can never become an identifier. A signed-out visitor
  (or someone who signs out) is forgotten with `reset()`; nothing is identified before consent or after a visitor withdraws. The
  filter also drops the person-property fields PostHog attaches to events (`$set`, `$set_once`, `$unset`), which carry full URLs. **The
  internal id is still personal data** under Kenya's Data Protection Act (it points to one person), so the privacy page says so.
- **Session replay: off everywhere, decided (part 2).** The prompt prefers turning replay off on the granary, reading notes, profile and
  cohort pages over masking them, and prefers masking to nothing. Replay is off on **every** page (`disable_session_recording: true`,
  held by a test). Revisit only if a product question needs it, and then per page, never on those four.
- **The privacy page (part 2), `/privacy`.** Public, signed in or not. It says what is counted and when, what is never sent, why, who
  receives it (the deployment's own PostHog host, and the region for PostHog's two hosted ones, outside Kenya), and the rights under Kenya's
  Data Protection Act, 2019. It is built from `lib/analytics/disclosure.ts`, and **a test holds that list equal to the list of events the
  app may send**, so an event cannot be allowed without being described to the people it is collected from; `sentToday` marks the events
  sent now (all of them; the page's "not sent yet" list shows only when an event is not connected). A **"Your choice" box** shows the
  current choice in plain words and lets the visitor change it at any time (`components/AnalyticsChoice.tsx`, sharing one store with the
  banner, `lib/analytics/consent-store.ts`); when the browser itself sends Do Not Track or Global Privacy Control the buttons are off
  and the page says why. With analytics off the page says only that. Linked from the banner, the sidebar footer and the sign-in and
  register pages. **Not on the phone tab bar** (it has five slots), so on a phone it is reached from the banner or the sign-in pages.
  Who to contact comes from `PRIVACY_CONTACT` (optional; without it the page says to contact "the person who runs this app").
  **Left out on purpose: how long PostHog keeps the data**, because it is a project setting nobody has decided; add it to the page
  when it is. **Not legal advice**: have the Kenya wording checked before relying on it.
- **Withdrawing.** If a visitor's choice or browser signal changes to "no" during a visit, `stopAnalytics()` resets the library and then
  opts it out. **The order matters and I had it wrong at first**: the library's `reset()` also clears its consent setting back to
  "opted in", so opting out first and resetting after left a visitor who had just said no capturing again. Found by simulating GPC
  mid-visit; `lib/analytics/client.test.ts` now holds the order, and accepting again opts back in on the same instance.
- **The eight named events (part 3).** The list is closed in `lib/analytics/events.ts` (`CUSTOM_EVENTS`, `EVENT_PROPERTIES`). Each event carries only what the table says. Anything else is refused by `buildEvent` and again by the `before_send` filter.

  | Event | Sent when | Carries |
  |---|---|---|
  | `day_logged` | a task is marked done (`updateTaskStatus`) | `source: "manual"` |
  | `credit_questioned` | a credit is questioned (`questionCredit`) | nothing |
  | `reading_note_submitted` | a reading note is saved (`submitReadingNote`) | nothing: no text, no score |
  | `pause_logged` | a pause is created (`createPause`) | nothing: no amount, no item |
  | `hillside_joined` / `hillside_left` | the Hillside switch changes (`setHillsideOptIn`) | nothing |
  | `cohort_dashboard_viewed` | the browser opens `/cohort/<id>` (`paths.ts`) | nothing |
  | `goal_created` | a goal is created on Flock (`createGoal`) | nothing: not its name, not its description |

- **How a server action's event reaches PostHog.** The browser holds the library, the server action does not. The action calls `queueAnalyticsEvent` (`lib/analytics/queue.ts`), which writes a short-lived cookie `rm_events` (120 seconds, at most 10 entries, name only). The consent component reads it, sends the events **only if the visitor has accepted**, and clears it. Declined visitors get the cookie cleared and nothing sent. The cookie is revalidated on both sides with `buildEvent`, so a forged or expired entry is dropped (`pending.ts`, `plan.ts`). It holds event names, never values. The privacy page names it.
- **The build-failing check.** `lib/analytics/named-events.test.ts` runs every event through `buildEvent` with the worst-case inputs and fails if any property key is a whole word `amount`, `text`, `note`, `email` or `name` (plural too, camelCase and snake_case split, `fields.ts`). `sanitize.ts` also deletes such keys at send time. `npm test` runs in the build, so the build fails.
- **Cohort ids stay out of URLs.** Found live: a pageview for `/cohort/1` carried the id. `normalizeRoute` now sends `/cohort/:id` for `$current_url`, `$pathname` and the related fields.
- **Known over-count.** `cohort_dashboard_viewed` is path-based, so it also fires when the page shows "Not your cohort" (403). Treat it as "opened a cohort page".
- **Checked in part 3 (25 Oct 2026, Chrome, signed in as the owner, real project)**: declined, the four actions I triggered sent 0 requests to PostHog and the actions still worked, with the `rm_events` cookie cleared; accepted, four requests (`$pageview`, `$identify`, `hillside_left`, `hillside_joined`), none with a non-standard property; a pushed `/cohort/12?probe=secret` sent `cohort_dashboard_viewed` and a `$pageview` at `/cohort/:id`, with no cohort number and no "secret" anywhere. **Unit tests only** for `day_logged`, `credit_questioned`, `reading_note_submitted` and `pause_logged`: I did not run those actions on your real data. **I did not look at your Live events.**
- **A setting you must change in PostHog**: the library's `ip` option is documented as having no effect. Discarding visitors' IP
  addresses is the project setting **"Discard client IP data"** in PostHog's project settings. Switch it on; I cannot.
- **Checked** (25 Oct 2026, Chrome, against a stand-in PostHog on `localhost:9099` and a placeholder key, never the real service):
  a fresh visitor sees the banner and, before choosing, the page loaded 11 resources, none to the analytics host or named PostHog,
  with no stored identifier and no cookie; after **Decline** a second page and a reload sent **0 requests** (the browser's network
  log, the page's resource list and the stand-in's own record all agree); after **Accept** exactly one `POST /e/` (the pageview)
  arrived, with no flags or config call; between two pages on the final code **two `$pageview` events and nothing else**, with the text
  of a deliberately secret-looking query string appearing nowhere in what was sent; simulated GPC mid-visit set the library's opt-out
  flag and **two further page changes sent nothing**. 31 unit tests (consent rules, options, the event filter, the library call
  order), 17 deliberate breaks, each failing the matching test.
- **Checked against the real project (part 2, 25 Oct 2026, Chrome, signed in as the owner, key and host from `infra/.env`)**: a fresh
  visitor on `/privacy` loaded 27 resources, **none to PostHog**, with no stored identifier and no cookie; declining through the page's own
  control removed the banner and sent nothing, also while moving through the signed-in Today page; choosing "Count my visits" made
  **two requests to `https://us.i.posthog.com/e/`, both answered 200** (the pageview and the identify), with the identifier in
  `localStorage` and no cookie; withdrawing through the page set the library's opt-out flag and **two further page changes sent
  nothing**; simulating Global Privacy Control turned the page's buttons off and said why; **at 390 px** the banner sits above the phone tab
  bar with no overlap, the page does not overflow sideways, and its buttons are 38 px tall (a little under the usual 44 px touch size).
  **What it sent to your project is test data**: a `$pageview` and a `$identify` for your internal user number. **I did not look at your
  Live events; please check that both appear and that the person is only that number.**
- **Not checked**: real Do Not Track and GPC from a real browser (the rules are unit-tested and GPC was simulated), signing out (the
  reset is unit-tested; I did not sign you out), the dark theme of the banner and the page, and a screen reader.
- **Setting it up, for Miguel**: in PostHog create a project, copy its project key and region host (for example
  `https://eu.i.posthog.com` or `https://us.i.posthog.com`), put them in `infra/.env` as `POSTHOG_KEY` and `POSTHOG_HOST`, switch on
  "Discard client IP data", and restart the frontend. `posthog-js` is pinned at 1.438.7 (the newest release over two weeks old on 25 Oct 2026).

## Open questions

- The M-Pesa import form hasn't been checked at 390px width — it's a `max-w-[420px]` card,
  likely fine, but not confirmed live this phase.
- Granary isn't reachable from the phone tab bar's own page content — it's in `PHONE_LINKS`
  now, but nothing within `/granary` itself has been checked at 390px width this phase.
- Profile's "Export my data"/"Delete my account" buttons from the design reference weren't
  built — no backend endpoint exists for either, explicitly out of this phase's scope (see
  `backend.md`'s open questions).
- Profile isn't reachable from the phone tab bar at all (it's sidebar/desktop-only in the
  reference too — "Profile under the main items" per the design reference's own nav notes —
  but there's no phone entry point either, same kind of gap as Flock/Watering holes below).
- The design reference's Profile page shows a live in-page analogy preview with its own
  interest switcher ("Today's topic: X, explained through Football/Philosophy/Skating"); this
  app only wires the analogy into Today's own staff card, not a second duplicate preview on
  Profile — see the decisions log on why.
- The phone tab bar still lists Today/Path/Reading/Tally/Hillside, not the updated design
  reference's Today/Reading/Granary/Tally/Hillside (Path moved off, opening from Today
  instead). Not changed this phase: Granary doesn't exist yet (Phase 11), and the "Path opens
  from Today" cross-link isn't built either — same kind of gap already open for Flock/Watering
  holes below. Revisit once Granary exists.
- The hillside's ledger only ever shows events ingested through `POST /api/evidence-events`
  directly (e.g. via `curl` for this phase's own live verification) — nothing in the frontend
  calls that endpoint yet from a real user action (Today's checks, Reading's notes, Watering
  holes' syncs all still only write to `daily_logs`, the Phase 1-8 pipeline). Wiring real
  app actions to also emit evidence events is follow-on work, not built this phase.
- No admin UI exists for authoring a `scoring_rules` row for a goal (see `backend.md`'s open
  questions); every real evidence event in this app currently qualifies by presence alone.
- Tally's "learning notes" stat tile still says "Not tracked yet" rather than a real number.
- Path's day blocks don't show a tended task's actual duration, only its target from the
  schedule template; joining in each day's `DailyLog.measuredMinutes` would need a second
  per-day fetch (or a backend endpoint that returns both together), not built this pass.
- Flock and Watering holes aren't reachable from the phone tab bar, matching the design
  reference's own nav list, but the reference's "they open from Tally on phone" cross-link
  isn't built — there's currently no way to reach them at all on a 390px phone.
- GitHub/Google sign-in is deferred (user's explicit instruction when password login was
  built); `AUTH_INTERNAL_TOKEN` exists in `backend.md`/`.env.example` for a future
  `POST /api/auth/github-login` but nothing in this frontend calls it yet.
- There's still no settings page as such — the hillside opt-in toggle lives directly on
  `/hillside` itself (a one-line link, not a dedicated settings view), which is fine for the
  one toggle that exists today but won't scale if more account-level preferences show up.
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

## Access control, part 1: the session token rides on every backend call

`lib/api.ts` reads the session cookie (`getSessionToken`) and sends it as `Authorization: Bearer`
on every call to the backend, including multipart. With no session it sends nothing, and an
`Authorization` header a caller sets itself takes precedence (the login, GitHub and health calls).
The Granary page, the Today page's due-pause call and the Granary Server Actions no longer send
a user id: the backend learns who is asking from the token. Other routes still carry `userId` until
their part of the stage is done (see `docs/backend.md`).

## Access control, part 2

The session token now also goes to the intelligence service (`lib/api.ts` sends it on both
backends). Calls for goals, routine items, learning topics, daily logs, tasks and schedule
templates no longer carry `userId` (pages, `lib/materialize.ts` and the create and status
Server Actions). Books, notes, Watering holes and credits still do, and so do the intelligence
calls (`/suggestion`, `/tally`, `/patterns`).

## Access control, part 3

Reading (books, notes, current book), Watering holes and credits calls (the Hillside ledger and
"question a credit") no longer carry `userId`: the backend reads the user from the session token. The
only `userId` left is on the intelligence calls (`/suggestion`, `/tally`, `/patterns`) and the
cohort pages' `requestingUserId` (part 4).

## Access control, part 4

The cohort page and the pilot survey action no longer send `requestingUserId`: the backend reads the
user from the session token and applies the same admin and member rights as before.

## Access control, part 5

No call carries a user id any more. `lib/scope.ts` (`withUserId`) is deleted; `materializeDay(date)` and the
intelligence calls take none. Hillside no longer reads `?asUserId=`; "you" appears when you are signed in.
`lib/api.ts` sends the session token on every call to either backend.

