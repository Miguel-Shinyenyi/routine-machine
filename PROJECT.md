# Routine Machine

## What this is

A system that logs daily routine completion and daily learning, and over phases moves from
recording, to detecting patterns, to actively suggesting what to do next. Built for one user.

## Why it exists

Directly from the person building it: "sometimes I feel like it's a lot so I end up doing
nothing." This system counters that by leading with a little a day, tracked honestly, rather
than a plan that looks complete on paper and never gets touched.

It also exists as portfolio evidence of AI engineering, not just backend engineering, and it
feeds three concrete goals: a CMU masters application, target roles including PostHog, and
becoming a working AI engineer rather than claiming the title without evidence.

## The underlying problem

Most personal habit tools either do nothing but record, or try to be intelligent from day
one with no data to be intelligent about. This project treats that as a sequencing problem,
not a scope problem: build the data layer correctly first, only add intelligence once there
is real data to learn from, and gate the most ambitious feature (active reprioritization) on
evidence, not on a target date.

## Tech stack

- Backend: Spring Boot (Java), same core stack as settlement-engine
- Intelligence service: Python, FastAPI
- Database: PostgreSQL
- Containers: Docker Compose (local only, no Kubernetes yet, single user, nothing to scale)

## Project structure

```
routine-machine/
  PROJECT.md              <- this file, master guide
  docs/
    DOCS_MAINTENANCE.md    <- rules for keeping these docs current
    backend.md              <- Spring Boot service structure and APIs
    intelligence-service.md  <- Python service, suggestion logic, pattern detection
    database.md               <- schema, migrations
    testing.md                 <- test strategy, coverage rules
  backend/                      <- Spring Boot source (not yet created)
  intelligence-service/          <- Python source (not yet created)
  infra/                          <- Docker Compose (not yet created)
```

## Running the application

Not yet runnable. Nothing has been built. This section gets filled in as soon as
`docker compose up` and a first endpoint exist, following the same pattern as
settlement-engine's `PROJECT.md`, exact commands, exact ports, exact env vars.

## Build phases

1. Logging and rule-based suggestion: record daily routine completion and learning topics,
   suggest a topic weighted toward whichever of the three goals has had the least recent
   attention. No real intelligence yet, correct data modeling only.
2. Pattern detection: completion rates, streaks, skip-heavy days, and a first descriptive
   correlation between logging a learning topic and completing more or fewer routine items
   that day. Statistics on structured data, not a model.
3. Active reprioritization: suggest a next action for the current day, with a stated reason
   tied to a Phase 2 finding. Does not start until Phase 2 has produced real data, weeks of
   it, not a synthetic sample. This is a gate, not a target date.

Current phase: **Documentation only. No code written yet.**

## Repo structure decision

Monorepo, same reasoning as settlement-engine: one contributor, tightly coupled services,
no reason yet to pay the coordination cost of separate repos. Revisit if that changes.

## Status log

Update this section every time a phase starts or finishes, or a real decision gets made.
Keep entries short. Record fixes and gaps found, not just what was completed.

| Date | Phase | Status | Notes |
|------|-------|--------|-------|
| 2026-09-18 | Setup | Done | Reconciled documentation structure against settlement-engine's actual PROJECT.md and docs/ pattern, replacing an earlier, less disciplined draft |
| 2026-09-18 | Setup | Done | Stack decided: Spring Boot core plus Python FastAPI intelligence service, reusing settlement-engine's polyglot pattern rather than inventing a new shape |

## Rules for working on this project

- TDD is not optional. Write the failing test before the implementation for every new piece
  of logic.
- Every component doc in `docs/` gets updated in the same session as the code change it
  describes. Do not batch doc updates for later.
- No secrets, API keys, or credentials in any file. Use environment variables and reference
  them by name only.
- Build only the current phase. Do not implement Phase 2 or 3 logic while working on Phase 1,
  even if it looks easy to add in passing.
- Do not start Phase 3 until Phase 2 has real logged data behind it, not synthetic data.
- See `docs/DOCS_MAINTENANCE.md` for the full doc update process.
- If anything in these docs is ambiguous or wrong, flag it back rather than silently deciding
  and proceeding. This project is tracked alongside a separate journaling process; decisions
  made silently here don't get recorded there.
