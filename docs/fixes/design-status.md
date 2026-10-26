# Design status (stage 1, 2026-10-26)

How the running app compares with `docs/design-reference/`. Report only. Nothing in the source changed.

## How this was checked

- App: the local stack on `http://127.0.0.1:3000`, built from branch `access-control-per-user-scope` (94 commits ahead of `origin/main`, not `main`).
- Account: one throwaway account, created for this check and signed in by the browser. It has no routines, goals or logs. So every page shows its empty state. A result of "matches" below means the structure, copy and tokens match in the empty state. Data states were not checked.
- Browser: Playwright Chromium, 1440 wide for desktop pages and 390 wide for phone pages.
- Method: full-page screenshot of each route, put beside the reference screenshot (reference on the left), then read by eye. Overflow was measured with `scrollWidth - innerWidth`: 0 on every page.

## `compare.mjs` does not run as documented

1. The README command, run from `frontend/` as `node ../docs/design-reference/compare.mjs http://localhost:3000`, fails:

```
Error [ERR_MODULE_NOT_FOUND]: Cannot find package 'playwright' imported from
.../docs/design-reference/compare.mjs
```

   Node resolves an ES module's imports from the script's own folder (`docs/design-reference/`), not from the folder you run it in. `playwright` and `pngjs` are already in `frontend/package.json`, so the README's `npm i -D` step is also stale.
2. A copy of the script, run from a folder with a link to `frontend/node_modules`, did run. It wrote 14 images and printed no warnings:

```
/today @1440: wrote compare/today-1440--vs-app.png
/path @1440: wrote compare/path-1440--vs-app.png
... (14 lines, no overflow warnings)
/granary @390: wrote compare/phone-granary--vs-app.png
```

3. Those 14 images are not useful. The script never signs in, so the right-hand side of every image is the sign-in page. I looked at `today-1440--vs-app.png` to confirm.
4. The script sends the cohort page to `/admin/cohorts/1`. The app serves it at `/cohort/[cohortId]`. The route map in the reference README has the same stale path.

A signed-in comparison needs a login step in the script. That is a stage 2 change.

## Result per page

| Page | Reference | Result | Evidence |
|---|---|---|---|
| `/today` | today.html, phone-today.html | differs | Same header, flock list, right-hand cards and phone tab bar. Added since the reference: a quote line, a "first step" card, the weekly failure-mode card (nine buttons) and Previous day / Next day links. The reference has a Shepherd notification at top right; the app has none there. The sidebar footer shows "Profile" with an initial, where the reference shows a name row. |
| `/path` | path.html | matches | Seven-day board, week switch, legend and "This week so far" card are in the same order. Empty state adds "Your path is empty." The week label now reads "26 October to 1 November". |
| `/flock` | flock.html | differs | The reference is a read-only table grouped Body, Mind, Work, Home and people, with Counted by, Watering hole and On the hillside columns. The app page is a set of forms (Routine items, Goals, Learning topics, Schedule templates, Ad-hoc tasks). With no data, the app shows no table, so a populated Flock was not compared. |
| `/tally` | tally.html | differs | The eyebrow reads "Since 2026-09-29" (ISO date); the reference reads "Since 31 August". Stat cards differ: the app shows "Reading progress" and "Learning notes" with the text "Not tracked yet (Phase 7)" (`frontend/app/tally/page.tsx:150`), where the reference shows words read and notes counted. That text looks stale, since Phase 7 is built. Not confirmed against data. |
| `/reading` | reading.html, phone-reading.html | matches | Header, copy and the book form match in the empty state. The note box, results and earlier notes need a book, so they were not compared. |
| `/granary` | granary.html, phone-granary.html | matches | Header copy, four stat cards, "One grain a day", the pause form with chips, and the right-hand cards match. Empty state adds a "Set a daily amount" card. The "Store KSh 50 today" button and the September card need data. |
| `/hillside` | hillside.html, phone-hillside.html | matches (hidden state only) | The account is not opted in, so only the "Personal progress only" state shows. The cohort/open switch, "Walking near you" and the credit ledger were not seen. |
| `/watering-holes` | watering-holes.html | differs | Reference: connected cards, then not-connected cards in four columns, with Health Connect, Apple Health, Strava and E-reader. App: three columns, with "Health data" in place of those four. The "Still to work out together" list in the app is stale: line 192 says "there's no page to turn it on from", but the Hillside page has a switch. |
| `/profile` | profile.html | differs | The reference shows read-only rows with an Edit link, a "Your programme" card and a "Your data" card with Export my data and Delete my account. The app shows editable inputs for time zone, failure-mode day, day end and currency, a comma-separated box for your own interests, and none of those two cards. It has a Sign out link. |
| `/cohort/[cohortId]` | cohort.html | differs | Seen live only as a non-member: "Not your cohort." with the cohort side navigation. The page code has the four tiles, the weekly bars and the by-group table. The reference also has Export CSV, Invite learners, "What this pilot is measuring", a "Data" card and a sidebar with the organisation name, a cohort list, Seats and invites, Reports and Privacy and data. The app sidebar has one cohort link and "My own Today". |
| `/login`, `/register` | none | no reference | Plain card on the canvas, no sidebar. Seen in the first run. |
| `/privacy` | none | no reference | Page loads with the sidebar. |
| `/watchtower` | none | no reference | Answers 404 for a non-owner, as designed. |
| `/` | none | no reference | Redirects to `/today`. |

## Not compared

- Dark mode (not in the reference).
- Populated pages: Flock, Tally, Reading, Hillside (opted in), Watering holes table, Granary history, and the cohort page for an admin or a member. Each needs seeded data.
- Phone pages other than Today and Hillside were captured and overflow-checked but not read by eye.

## Screenshot artefact, not a defect

On the 390 wide Today capture the floating tab bar sits in the middle of the page. Playwright draws a fixed element at its viewport position in a full-page capture.
