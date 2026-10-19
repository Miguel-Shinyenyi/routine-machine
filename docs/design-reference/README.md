# Routine Machine design reference

The design canvas, exported as plain HTML, CSS and a little JavaScript. No framework and
no build step. Use it as the target when building or checking the frontend. It goes with
`docs/design.md`; where they disagree, `docs/design.md` wins.

Everything on these pages is sample data. Compare layout, type, spacing, colour, copy and
states, never the numbers.

## Where it goes

This folder is new. Put it in the routine-machine repo as `docs/design-reference/`:

```
docs/design-reference/
  README.md          this file
  pages/             the screens as static HTML (open pages/index.html)
    index.html       list of screens
    today.html ... watering-holes.html     desktop pages, fluid, drawn at 1440
    granary.html, profile.html                  new on 2026-10-18, learner pages
    cohort.html                                 new on 2026-10-18, programme admin view
    phone-today.html, phone-reading.html, phone-hillside.html, phone-granary.html   phone, 390 wide
    tokens.css       the design tokens as CSS variables, light and dark
    base.css         shared layout rules used by the pages added on 2026-10-18
    app.js           behaviour: Today checks, switches, chip pickers, segmented controls,
                     Profile analogies, Hillside hide and scope, Granary store
  screenshots/       PNGs of each page, the visual target
  compare.mjs        screenshots the running app and puts it beside the reference
```

Add `docs/design-reference/compare/` to `.gitignore`; it is generated.

## How to view it

Open `pages/index.html` in a browser, or serve the folder:
`python3 -m http.server -d docs/design-reference/pages 4555`.

## Screen to route map

| Reference page | App route | Was | Width |
|---|---|---|---|
| today.html | /today | /board | 1440 |
| path.html | /path | /roadmap | 1440 |
| flock.html | /flock | /backlog | 1440 |
| tally.html | /tally | /reports | 1440 |
| reading.html | /reading | new | 1440 |
| hillside.html | /hillside | new | 1440 |
| watering-holes.html | /watering-holes | new | 1440 |
| phone-today.html | /today | | 390 |
| phone-reading.html | /reading | | 390 |
| phone-hillside.html | /hillside | | 390 |
| granary.html | /granary | new | 1440 |
| profile.html | /profile | new | 1440 |
| cohort.html | /admin/cohorts/[id] | new | 1440 |
| phone-granary.html | /granary | new | 390 |

On desktop the navigation is the glass sidebar, with Granary between Reading and Hillside and
Profile under the main items. On phone it is a floating glass tab bar with Today, Reading,
Granary, Tally and Hillside (Path moved off the tab bar; it opens from Today). The admin view
has its own sidebar for programme staff. The desktop pages reflow at 390 (the sidebar
stacks), but the phone screens are the intended phone design; match those.

## How to compare

With the app running on port 3000, from `frontend/`:

```
npm i -D playwright pngjs
npx playwright install chromium
node ../docs/design-reference/compare.mjs http://localhost:3000
```

It writes `docs/design-reference/compare/<screen>--vs-app.png`: the reference on the left,
the app on the right. It also warns about any horizontal overflow. Open each image and
check:

1. **Structure:**
   - same sections in the same order;
   - same headline and sub-headline copy;
   - same controls (segmented controls, check buttons, capsule buttons).
2. **Tokens:** colours, radii and type sizes match `pages/tokens.css` and
   `docs/design.md`. The pages write hex values inline; the app should use variables.
3. **States:**
   - **Today:** checking a task turns it green and updates both counts and the bar.
     Auto-counted chips turn from amber to green.
   - **Hillside:** the field and window switches change the band, histogram and rows.
4. **Phone:** no horizontal page scroll. The week, the Flock tables and the Hillside rows
   scroll inside their own boxes.
5. **Accessibility:**
   - every control is a real `button` or `a`;
   - the selected item carries `aria-pressed` or `aria-current`;
   - the charts have text labels.

Record any deliberate difference in the decisions log of `docs/frontend.md`, so the next
comparison doesn't flag it again.

## What changed on 2026-10-18

- **Hillside v2**, from the leaderboard design notes:
  - Opt-in, and limited to an invited cohort by default. The open hillside is a second
    choice.
  - Weekly seasons: the board starts fresh every Monday.
  - One credit per goal per local day, capped at 3.
  - "Came back after a break" is counted separately and never ranked.
  - A credit ledger shows the status of each piece of evidence (accepted, self-reported,
    pending, not counted) and the rule version.
  - A switch hides the board for a personal-only view.
  - No "discipline score".
- **Granary**, the financial-discipline page: small daily savings, "the pause" (a 24-hour
  wait and a 30-day look back on tempting decisions), and category totals imported from an
  M-Pesa statement. Private, never on the hillside.
- **Profile:** interests that become the language of explanations, with an analogy switch
  (football, philosophy, skating) and a plain-version fallback. It also states exactly what
  a programme can and cannot see.
- **Cohort**, the programme admin view: aggregates only, with every rate shown with its
  denominator. Groups under 5 are hidden. It matches the pilot measures in the operating
  plan.
- **Smaller changes:**
  - Today gains an analogy line in the staff card and a granary card.
  - Flock gains a "Small saving" routine.
  - Watering holes names the M-Pesa statement as the finance source.

## Not in the reference

- **Dark mode:** it is specified in `tokens.css` and `docs/design.md` but was not drawn.
- **Missing states:** empty, loading and error states are not drawn. Build them in the
  same style: a white card, a short sentence in muted text, and one action.
- **The mascot:** the shepherd in the notifications is a simple inline SVG placeholder.
  The full illustrated shepherd lives in the UMWAYI site illustrations.
