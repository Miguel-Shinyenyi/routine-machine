# Interaction defects (stage 1, 2026-10-26)

Buttons and other clickable parts do not show a pointer cursor. Report only. No source changed.

## Cause (checked, not assumed)

**Verdict: the likely cause in the prompt is half right.** The cause is that no author rule sets a pointer on buttons. The default arrow comes from the browser's own stylesheet. `.pick label` is the only custom pointer rule on a form control, so it is the only one that works.

Evidence:

1. **Tailwind documentation.** The v4 upgrade guide says buttons "now use `cursor: default`" to match the browser, and it gives a base rule to restore the pointer (fetched 2026-10-26, tailwindcss.com/docs/upgrade-guide). Tailwind 3 had set the pointer. Tailwind 4 removed it.
2. **Installed version.** `frontend/node_modules/tailwindcss` is 4.3.3. Its `preflight.css` contains no `cursor` rule for `button`. The only `cursor` text in it is a comment about spin buttons in Safari.
3. **Computed style.** A plain `<button>` has `cursor: default` in two places: on a page with no stylesheet at all, and inside the running app (`/login`, after Tailwind and `globals.css` load). So the app does not override the browser. It also does not fix it.
4. **App source.** `frontend/app/globals.css` has one pointer rule: `.pick label` (line 307). Three components add `cursor-pointer` by class: a `<summary>` in `components/charts/WatchtowerChart.tsx`, a `<summary>` in `components/WeeklyFailureModeCard.tsx` and one label in `app/profile/page.tsx`. Nothing sets it on a button.
5. **Design reference.** `docs/design-reference/pages/base.css` sets `cursor:pointer` on `.btn`, `.seg button`, `.switch` and `.pick button`. The app copied the look of these but not the cursor, except for `.pick`.

### (1) Computed cursor of the plain elements

| Element | No stylesheet | Inside the app |
|---|---|---|
| plain `<button>` | `default` | `default` |
| `<button disabled>` | `default` | `default` |
| `<a href>` | `pointer` | `pointer` |
| `<select>` | `default` | `default` |
| `<summary>` | `auto` | `auto` |
| `<label>` with a checkbox | `default` | `default` |
| checkbox | `default` | `default` |
| `<div role="button">` | `auto` | `auto` |
| `.pick label` | not in the plain page | `pointer` |

### (2) Disabled elements

- A disabled button already shows `default`, which is right. The fix must keep that.
- No control in the app source has a `disabled` attribute (searched `app/` and `components/`), and none was disabled on any page reached. So there are no disabled rows in the table below. The behaviour is shown by the injected test in the next section.

## Proposed fix R1 (not applied)

One base rule in `frontend/app/globals.css`. It is the rule from the Tailwind guide, extended to the other clickable types found below:

```css
@layer base {
  button:not(:disabled),
  [role="button"]:not(:disabled),
  summary,
  select:not(:disabled),
  input[type="checkbox"]:not(:disabled),
  input[type="radio"]:not(:disabled),
  input[type="submit"]:not(:disabled),
  label:has(input[type="checkbox"]:not(:disabled)),
  label:has(input[type="radio"]:not(:disabled)) {
    cursor: pointer;
  }
}
```

Tested without touching the source: the rule was injected into the running `/login` page, and plain elements were read before and after.

| Element | Before | After |
|---|---|---|
| enabled `<button>` | `default` | `pointer` |
| `<button disabled>` | `default` | `default` |
| `<button role="switch">` | `default` | `pointer` |
| `<div role="button">` | `auto` | `pointer` |
| `<summary>` | `auto` | `pointer` |
| enabled `<select>` | `default` | `pointer` |
| disabled `<select>` | `default` | `default` |
| label with an enabled checkbox | `default` | `pointer` |
| label with a disabled checkbox | `default` | `default` |
| label for a text box | `default` | `default` |

Open choice for you: `select` is a design call. Browsers show an arrow on it. R1 gives it a pointer to match the buttons. Drop the `select` line if you prefer the arrow. The 8 select rows below depend on it.

## Counts

18 page loads (2 signed out, 16 signed in at 1440 or 390 wide), 315 visible clickable elements recorded. 65 of them show the wrong cursor: 55 buttons, 8 selects and 2 switches. All 65 are fixed by R1. Links already show a pointer. Labels and checkboxes in `.pick` already work.

## Rows

One row per page and selector. The number in brackets is how many elements match. Pages marked `@390` are the phone width. `/watchtower` is the 404 page that a non-owner sees. `/cohort/1` is the "Not your cohort" page, because the throwaway account is not a member.

| Page | Selector | Computed cursor | Expected cursor | Proposed fix |
|---|---|---|---|---|
| `/login (signed out)` | `label` (2) | `default` | `default` | none (label of a text box; default is right) |
| `/login (signed out)` | `button[type=submit].btn-fill` (1) | `default` | `pointer` | Rule R1 |
| `/login (signed out)` | `a` (3) | `pointer` | `pointer` | none |
| `/login (signed out)` | `button[type=button]` (2) | `default` | `pointer` | Rule R1 |
| `/register (signed out)` | `label` (2) | `default` | `default` | none (label of a text box; default is right) |
| `/register (signed out)` | `button[type=submit].btn-fill` (1) | `default` | `pointer` | Rule R1 |
| `/register (signed out)` | `a` (3) | `pointer` | `pointer` | none |
| `/register (signed out)` | `button[type=button]` (2) | `default` | `pointer` | Rule R1 |
| `/today` | `a (in nav)` (11) | `pointer` | `pointer` | none |
| `/today` | `button[type=button] (in nav)` (1) | `default` | `pointer` | Rule R1 |
| `/today` | `a.btn-fill` (1) | `pointer` | `pointer` | none |
| `/today` | `button[type=submit]` (9) | `default` | `pointer` | Rule R1 |
| `/today` | `a` (2) | `pointer` | `pointer` | none |
| `/today` | `a.card` (1) | `pointer` | `pointer` | none |
| `/path` | `a (in nav)` (11) | `pointer` | `pointer` | none |
| `/path` | `button[type=button] (in nav)` (1) | `default` | `pointer` | Rule R1 |
| `/path` | `a` (4) | `pointer` | `pointer` | none |
| `/flock` | `a (in nav)` (11) | `pointer` | `pointer` | none |
| `/flock` | `button[type=button] (in nav)` (1) | `default` | `pointer` | Rule R1 |
| `/flock` | `a` (1) | `pointer` | `pointer` | none |
| `/flock` | `a.btn-fill` (1) | `pointer` | `pointer` | none |
| `/flock` | `label` (16) | `default` | `default` | none (label of a text box; default is right) |
| `/flock` | `select` (7) | `default` | `pointer` | Rule R1 |
| `/flock` | `button[type=submit].btn-fill` (5) | `default` | `pointer` | Rule R1 |
| `/tally` | `a (in nav)` (11) | `pointer` | `pointer` | none |
| `/tally` | `button[type=button] (in nav)` (1) | `default` | `pointer` | Rule R1 |
| `/tally` | `a` (2) | `pointer` | `pointer` | none |
| `/reading` | `a (in nav)` (11) | `pointer` | `pointer` | none |
| `/reading` | `button[type=button] (in nav)` (1) | `default` | `pointer` | Rule R1 |
| `/reading` | `label` (3) | `default` | `default` | none (label of a text box; default is right) |
| `/reading` | `button[type=submit].btn-fill` (1) | `default` | `pointer` | Rule R1 |
| `/hillside` | `a (in nav)` (11) | `pointer` | `pointer` | none |
| `/hillside` | `button[type=button] (in nav)` (1) | `default` | `pointer` | Rule R1 |
| `/hillside` | `button[type=submit][role=switch].switch` (1) | `default` | `pointer` | Rule R1 |
| `/hillside` | `a` (1) | `pointer` | `pointer` | none |
| `/watering-holes` | `a (in nav)` (11) | `pointer` | `pointer` | none |
| `/watering-holes` | `button[type=button] (in nav)` (1) | `default` | `pointer` | Rule R1 |
| `/granary` | `a (in nav)` (11) | `pointer` | `pointer` | none |
| `/granary` | `button[type=button] (in nav)` (1) | `default` | `pointer` | Rule R1 |
| `/granary` | `button[type=button]` (1) | `default` | `pointer` | Rule R1 |
| `/granary` | `button[type=submit].btn-fill` (2) | `default` | `pointer` | Rule R1 |
| `/granary` | `label` (3) | `default` | `default` | none (label of a text box; default is right) |
| `/granary` | `label (in .pick)` (4) | `pointer` | `pointer` | none (`.pick label` rule, or `cursor-pointer` class) |
| `/granary` | `input[type=radio] (in .pick)` (4) | `default` | `n/a` | none: the input is visually hidden; its label is the click target |
| `/granary` | `button[type=submit]` (1) | `default` | `pointer` | Rule R1 |
| `/profile` | `a (in nav)` (11) | `pointer` | `pointer` | none |
| `/profile` | `button[type=button] (in nav)` (1) | `default` | `pointer` | Rule R1 |
| `/profile` | `button[type=submit]` (1) | `default` | `pointer` | Rule R1 |
| `/profile` | `label` (6) | `default` | `default` | none (label of a text box; default is right) |
| `/profile` | `select.field` (1) | `default` | `pointer` | Rule R1 |
| `/profile` | `label (in .pick)` (13) | `pointer` | `pointer` | none (`.pick label` rule, or `cursor-pointer` class) |
| `/profile` | `input[type=checkbox] (in .pick)` (13) | `default` | `n/a` | none: the input is visually hidden; its label is the click target |
| `/profile` | `label` (1) | `pointer` | `pointer` | none (`.pick label` rule, or `cursor-pointer` class) |
| `/profile` | `input[type=checkbox]` (1) | `default` | `n/a` | none: the input is visually hidden; its label is the click target |
| `/profile` | `button[type=submit].btn-fill` (1) | `default` | `pointer` | Rule R1 |
| `/cohort/1` | `a (in nav)` (3) | `pointer` | `pointer` | none |
| `/cohort/1` | `button[type=button] (in nav)` (1) | `default` | `pointer` | Rule R1 |
| `/privacy` | `a (in nav)` (11) | `pointer` | `pointer` | none |
| `/privacy` | `button[type=button] (in nav)` (1) | `default` | `pointer` | Rule R1 |
| `/privacy` | `button[type=button]` (2) | `default` | `pointer` | Rule R1 |
| `/watchtower` | `a (in nav)` (11) | `pointer` | `pointer` | none |
| `/watchtower` | `button[type=button] (in nav)` (1) | `default` | `pointer` | Rule R1 |
| `/today @390` | `a.btn-fill` (1) | `pointer` | `pointer` | none |
| `/today @390` | `button[type=submit]` (9) | `default` | `pointer` | Rule R1 |
| `/today @390` | `a` (2) | `pointer` | `pointer` | none |
| `/today @390` | `a.card` (1) | `pointer` | `pointer` | none |
| `/today @390` | `a (in nav)` (5) | `pointer` | `pointer` | none |
| `/reading @390` | `label` (3) | `default` | `default` | none (label of a text box; default is right) |
| `/reading @390` | `button[type=submit].btn-fill` (1) | `default` | `pointer` | Rule R1 |
| `/reading @390` | `a (in nav)` (5) | `pointer` | `pointer` | none |
| `/hillside @390` | `button[type=submit][role=switch].switch` (1) | `default` | `pointer` | Rule R1 |
| `/hillside @390` | `a` (1) | `pointer` | `pointer` | none |
| `/hillside @390` | `a (in nav)` (5) | `pointer` | `pointer` | none |
| `/granary @390` | `button[type=button]` (1) | `default` | `pointer` | Rule R1 |
| `/granary @390` | `button[type=submit].btn-fill` (2) | `default` | `pointer` | Rule R1 |
| `/granary @390` | `label` (3) | `default` | `default` | none (label of a text box; default is right) |
| `/granary @390` | `label (in .pick)` (4) | `pointer` | `pointer` | none (`.pick label` rule, or `cursor-pointer` class) |
| `/granary @390` | `input[type=radio] (in .pick)` (4) | `default` | `n/a` | none: the input is visually hidden; its label is the click target |
| `/granary @390` | `button[type=submit]` (1) | `default` | `pointer` | Rule R1 |
| `/granary @390` | `a (in nav)` (5) | `pointer` | `pointer` | none |

## Not reached

These clickables exist in the source but did not render for an empty account, so their cursor was not measured: the Today check buttons with data (the nine small buttons seen are the failure-mode choices), the answered state of the weekly failure-mode card (`<summary>`), the owner-only operations page, and the three survey buttons on the cohort page (a member sees them; `page.tsx` lines 176 to 196). R1 covers each element type, but a stage 2 check should look at them with seeded data.
