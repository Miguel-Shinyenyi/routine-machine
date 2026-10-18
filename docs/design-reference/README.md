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
    phone-today.html, phone-reading.html, phone-hillside.html   phone screens, 390 wide
    tokens.css       the design tokens as CSS variables, light and dark
    app.js           behaviour for Today (check a task) and Hillside (switch field, window)
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

On desktop the navigation is the glass sidebar. On phone it is a floating glass tab bar
with Today, Path, Reading, Tally and Hillside. The desktop pages reflow at 390 (the sidebar
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

## Not in the reference

- **Dark mode:** it is specified in `tokens.css` and `docs/design.md` but was not drawn.
- **Missing states:** empty, loading and error states are not drawn. Build them in the
  same style: a white card, a short sentence in muted text, and one action.
- **The mascot:** the shepherd in the notifications is a simple inline SVG placeholder.
  The full illustrated shepherd lives in the UMWAYI site illustrations.
