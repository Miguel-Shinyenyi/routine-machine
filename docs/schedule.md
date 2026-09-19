# Schedule

## Purpose

A deliberately fixed, non-adaptive weekly schedule, built to give real daily structure while
Phase 4's data-driven reprioritization waits on evidence it doesn't have yet. This is not a
smaller version of Phase 4, it doesn't get smarter over time, it just needs to be correct
once and followed.

## Learning versus deep-work

These are two different things, kept separate on purpose:

- **Learning** is understanding something, goal-tagged, using Phase 1's existing
  `learning_topics` mechanism. This covers studying settlement-engine (tagged toward
  `ai-engineer-evidence` and `target-roles`), CMU or PostHog prep material, and any other
  conceptual study. No new mechanism needed, settlement-engine study sessions are just
  `learning_topics` rows like any other.
- **Deep-work** is building, specifically Routine Machine's own implementation. It produces
  code, not understanding of existing code.

An earlier draft of this schedule put settlement-engine study under deep-work. That was
wrong, corrected below.

## The schedule

Order matters more than clock time. Paid work is roughly 3 hours daily, flexible, placed
wherever suits the day, everything else is sequenced around it.

**Daily block order:** wake and shower, exercise, a learning block (topic chosen by Phase 1's
`/suggestion` endpoint, or settlement-engine study when that's the day's emphasis), paid
work, a deep-work block on Routine Machine build days, cooking, journaling (captured
automatically once written, see `hub-sync.md`), personal time, sleep.

**Weekly layout:**

| Day | Exercise | Learning emphasis | Deep-work | Weekly extras |
|-----|----------|---------------------|-----------|----------------|
| Mon | Weight lifting | Goal-suggested topic | Routine Machine build | Mum check-in |
| Tue | Walk | Settlement-engine study | | |
| Wed | Weight lifting | Goal-suggested topic | Routine Machine build | Mum check-in |
| Thu | Walk | Settlement-engine study | | |
| Fri | Skating | Goal-suggested topic | Routine Machine build | |
| Sat | Rest or light walk | Goal-suggested topic or catch-up | Flexible catch-up | Cleaning, mum check-in, article writing |
| Sun | Rest or light walk | Goal-suggested topic or catch-up | Flexible catch-up | Finances review, batch meal-prep |

## Personal time and reading

The personal-time block includes reading, currently Sapiens. Which book is current isn't a
routine item, since it's a fact that changes occasionally, not something done or not done
daily. It's tracked as its own small log, `current_reading_log`, detailed in `database.md`,
updatable two ways: directly through the frontend, or detected from the journal itself, see
`hub-sync.md`.

## Current state

Not built. This is the design, not yet code.

## Data model

Detailed fully in `database.md`:

- `schedule_templates`: one row per recurring block (day of week, what it targets, a target
  duration, its order in the day). Targets either an existing `routine_item` or the daily
  learning-topic slot, it doesn't introduce a new concept for what gets done, only when.
- `tasks`: the day-specific, stateful instance of a template (or an ad-hoc one-off), carrying
  a status (`TODO`, `IN_PROGRESS`, `DONE`). Marking a task `DONE` writes a `daily_log` with
  `source = 'schedule'`, so Phase 2's pattern detection keeps working on the same underlying
  data without needing separate statistics logic for scheduled versus manual completions.
- `current_reading_log`: an append-only log of what's being read, latest row is current.

## Decisions log

| Date | Decision | Reason |
|------|----------|--------|
| 2026-09-19 | Settlement-engine study moved from deep-work to the learning block, as a goal-tagged learning_topic | Studying an existing system for understanding is a learning activity, not a building one; conflating them under deep-work blurred a distinction the project already draws elsewhere |
| 2026-09-19 | Reading tracked as an append-only `current_reading_log`, not a field on an existing table | What's currently being read changes occasionally and is worth a small history, not a single mutable value that overwrites its own past |
| 2026-09-18 | Added `deep_work` as a new routine item, not in the original daily inventory | The original inventory (sleep, showering, movement, cleaning, cooking, personal time, mum check-in, paid work, finances) had no entry for goal-directed project time, the actual purpose of Routine Machine and settlement-engine study. Without it, the schedule's most important block wouldn't be trackable at all. Now scoped specifically to Routine Machine build, since settlement-engine study moved to learning |
| 2026-09-18 | Completing a task writes a `daily_log` with a new `source = 'schedule'` value, rather than a separate completions table | Reuses Phase 2's existing pattern-detection logic unchanged; a scheduled completion and a manual one are the same fact with a different origin, not a different kind of fact |
| 2026-09-18 | The schedule is fixed, not adaptive, on purpose | Phase 4 is where adaptation belongs, and it's explicitly gated on real data. Building a semi-adaptive schedule now would blur that gate and duplicate work once Phase 4 actually starts |

## Open questions

- Exact target durations per block, left for whoever builds this to propose reasonable
  defaults and record them here, since no duration was specified when this was designed.
- Whether the weekly layout needs adjusting once it's actually being lived with, expected,
  not a sign anything was wrong in the design.