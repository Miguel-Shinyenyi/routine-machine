# Routine Machine: design system

Source of truth for the frontend's look and language from 2026-10-16. Written in the
DESIGN.md format (mission, language, foundations, components, rules, quality gates) so a
coding agent or design tool can use it as-is. Same design family as amwayi's personal site
and the Invoice-Financing dashboard (Apple Human Interface paradigms), with the shepherd as
the app's voice.

## Mission

Guide one person toward their goals against hype culture's noise. The app reminds them
that the little work done daily counts, slows them down, and shows in the records that the
goal is closer than yesterday. It cares for the user the way a shepherd cares for a flock.

## Language: the shepherd's words

| Term | Means | Where |
|---|---|---|
| The Shepherd | The app's voice: notifications, suggestions, questions back | Everywhere, sparingly |
| Today's pasture | Today's tasks | Today (was Board) |
| The path | The fixed weekly schedule | Path (was Roadmap) |
| Your flock | All routine items, learning topics and how each is counted | Flock (was Backlog) |
| The tally | The records: one notch per day shown up | Tally (was Reports) |
| Reader's notes | Notes in your own words that place you in the book | Reading (new) |
| The hillside | The anonymous leaderboard; other flocks walking the same way | Hillside (new) |
| Watering holes | Data sources: WakaTime, ActivityWatch, health data, the UMWAYI hub | Watering holes (new) |
| Green pastures | The three goals | Tally, Today |
| The staff | The suggestion for today's learning slot, with its reason | Today |
| A sheep wandered | A missed routine. Never shamed, never resets anything | Today, Path |
| Still waters | Rest days (Saturday, Sunday) | Path |
| Tended | Done | Everywhere |

Navigation labels stay plain one-word nouns (Today, Path, Flock, Tally, Reading, Hillside,
Watering holes). The shepherd language lives in headlines and copy, never in a way that
makes a control harder to understand.

## Principles

1. **Deference.** Gray canvas #F5F5F7, white grouped cards, no card shadows, no gradients.
2. **Clarity.** System font (San Francisco on Apple devices). 40px semibold page titles
   ending in a period, 20px section titles, 15px body. Sentence case.
3. **Liquid Glass on floating chrome only:** the sidebar, the phone tab bar and the
   Shepherd's notification. `rgba(255,255,255,.72)`, blur 20px, saturate 180%. Solid under
   `prefers-reduced-transparency`.
4. **Familiar controls.** Segmented controls (fields, ranges, weeks), grouped inset lists
   with Settings-style colored icon squares, round check buttons, capsule buttons, one
   filled button per view.
5. **One tint: amber.** #E9A23B for fills and selection, #9A5208 for tint text (5.9:1 on
   white). Green #1B7332 means tended. Nothing else is a colour of its own.
6. **Slow by design.** Days shown up, never streaks. No red, no counters that reset, no
   confetti. A missed day is "wandered" and carries no penalty.

## Foundations

| Token | Light | Dark | Use |
|---|---|---|---|
| canvas | #F5F5F7 | #000000 | Page |
| surface | #FFFFFF | #1C1C1E | Cards, lists |
| surface-2 | #F5F5F7 | #2C2C2E | Tiles inside cards |
| ink | #1D1D1F | #F5F5F7 | Labels |
| muted | #6E6E73 | #A1A1A6 | Secondary text |
| hairline | #E5E5EA | #38383A | Row separators |
| fill | rgba(118,118,128,.12) | .24 | Gray buttons, segmented track |
| tint | #E9A23B | #F0AE4E | Filled buttons, progress, selected row |
| tint-text | #9A5208 | #F0AE4E | Links, eyebrows, tint chips |
| tint-fill | #FBF0DC | #2A2215 | Auto-counted chips, your row on the hillside |
| tended | #1B7332 / #E3F5E7 | #30D158 / #16301C | Done checks and chips |
| private | #2F5A72 / #EEF3F7 | #8EC5E8 / #12232E | Private chips, still-waters days |
| Goal lines | #C27A12, #0066CC, #1B7332 | lighter variants | Tally chart only |

- Radius: cards 18px, lists 14px, tiles 12px, check buttons round, buttons capsule.
- Content width 1100px. Desktop: glass sidebar (220px) + content; it stacks at phone width.
- Phone: 390px, 20px gutters, a floating glass tab bar (Today, Path, Reading, Tally,
  Hillside). Flock and Watering holes open from Tally on phone.
- Motion: 150ms hover and press (scale .97). A check fills in 200ms. Nothing moves on its
  own. Reduced motion: no transitions.
- Accessibility: WCAG 2.2 AA in both themes, 44px touch targets, real buttons and links,
  colour never the only signal (checks have a tick, chips have text).

## Pages

- **Today**: date eyebrow, "Today's pasture.", the Shepherd notification, a progress bar,
  the day's tasks as a grouped list in walking order (check, name, source chip, measured
  amount, time). Auto-counted rows fill themselves in. Right column: "Closer than
  yesterday." (hours per goal), the staff (today's learning topic and its reason), "One
  sheep wandered." and a link to the hillside band.
- **Path**: seven day columns, today outlined in amber, weekend in still-waters blue, each
  block with a status dot (tended, today, wandered, ahead). A card for durations not yet set.
- **Flock**: grouped lists by Body, Mind, Work, Home and people. Each row: name and icon
  square, "Counted by" rule, watering hole, hillside field or Private.
- **Tally**: tally stick (one notch per day shown up, dashed for rest), four stat tiles,
  cumulative hours per goal (lines only go up), per-routine meters with Steady / Settling
  in / Wandering, steep days and the learning correlation.
- **Reading**: book card with chapter progress, the note field, then "What the Shepherd
  read.": chapter placed, own-words percentage, ideas connected, quoted phrases marked, one
  question back. Earlier notes list. How a note is read. Notes never leave the user.
- **Hillside**: field segmented control (All fields, Reading, Movement, Deep work,
  Learning, Sleep, Writing), window (7 or 28 days), your band in words, a histogram of the
  flock by days shown up, the three people ahead and behind you, the rules that keep it calm.
- **Watering holes**: connected and not-connected source cards with what each reads and
  where the data lives, then the "how each activity is counted" table.

## Rules: don't

- No streaks, no "you broke your streak", no red for a missed day.
- No top-ten list, no volume ranking, no names, avatars, followers or messages.
- No em dashes in UI copy. No hype words.
- No glass on content, no second accent colour.

## Quality gates

- Build, lint, typecheck and tests clean.
- No horizontal page scroll at 390px. Wide tables and the week scroll inside their box.
- Light, dark, reduced motion and reduced transparency checked by eye.