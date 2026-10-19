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

`/` redirects to `/today`. The four old paths (`/board`, `/roadmap`, `/backlog`, `/reports`)
redirect (307, in `next.config.ts`) to their renamed routes so existing links keep working.

## Current state

Built: Next.js (App Router), TypeScript, Tailwind. Most of the app still talks to the backend
unauthenticated (single-user demo mode, or the `asUserId`/`userId` query params — see
`backend.md`), so `lib/api.ts`'s `backendFetch` is a plain server-side `fetch` wrapper. Real
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
| 2026-10-17 | Flock and Watering holes filter `routine-items` to `userId == null` before rendering | Phase 8's hillside demo users each have their own routine items now; these two pages are this single-user app's own management/status views, not multi-user views, so they only ever show the unowned (legacy, pre-Phase-8) items, same as before Phase 8 existed. Today's own fetch of the same endpoint is untouched since it's only used for an id-keyed lookup map, never listed |
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