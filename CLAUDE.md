# Working in Routine Machine

Claude Code reads this file at the start of every task. Practices that apply to every project are in `~/.claude/CLAUDE.md`. This file adds what is specific here.

## Read in this order

1. `PROJECT.md`. Stack, phases, status log, and the rules for working here.
2. `docs/DOCS_MAINTENANCE.md`. How every doc gets written and updated.
3. Only the doc for the component you are touching.

| You are changing | Read |
|---|---|
| Spring Boot API (`backend/`) | `docs/backend.md`, `docs/database.md` |
| Fixed weekly schedule | `docs/schedule.md` |
| Pattern detection, suggestions (`intelligence-service/`) | `docs/intelligence-service.md` |
| Reading the UMWAYI hub | `docs/hub-sync.md` |
| Dashboard (`frontend/`) | `docs/frontend.md`, then `frontend/AGENTS.md` |
| Metrics, logs, traces, alerts, the owner-only operations page | `docs/observability.md` |
| API documentation (the owner-only viewer for both APIs) | `docs/backend.md` ("API documentation"), `docs/intelligence-service.md`, `docs/frontend.md` |
| Tests | `docs/testing.md` |
| The concerns record (the 111 concepts), or an audit | `docs/architecture.md`, then `docs/engineering-concerns.md` |

## Rules that already apply

- TDD is not optional. Write the failing test before the implementation for every new piece of logic.
- Build only the current phase. Do not write later-phase logic, even if it looks easy. Phase 4 does not start until Phase 2 has real logged data behind it.
- Update the component doc in the same change as the code.
- No secrets, keys or credentials in any file. Use environment variables and name them only.
- The `Current book:` line in UMWAYI's `journal/TEMPLATE.md` and the matching string in `docs/hub-sync.md` change together, in one change. If only one changes, hub-sync stops matching with no error.
- If anything in the docs is ambiguous or wrong, flag it. Do not decide silently. Decisions made here are not recorded in the journal process unless you say so.
- Do not push or open a pull request unless the task says so.

## Known risk

hub-sync reads encrypted journal content only through the unlock flow documented in `docs/hub-sync.md`. The key comes from the `UMWAYI_JOURNAL_KEY_B64` environment variable. Never store the key in this repo.

## Run and check

`./mvnw test` in `backend/` needs Docker for Testcontainers. `pytest` in `intelligence-service/`. `npm run lint`, `npm test` and `npm run build` in `frontend/`. Commands and ports are in `PROJECT.md`.

## Understanding first, and the 111 concerns

These rules come from the UMWAYI hub (`docs/understanding-first.md` and `docs/engineering-concerns.md`). They apply to every task here.

- The cycle of every step: understand things as they are, not as you want them to be. Then let AI write what you understood is needed. Check the output against the goal. Weigh the consequences and know how to undo the change before you ship it.
- State the goal in one sentence before any work, in the other person's words. After the work, check the output against that sentence.
- Stay close behind AI output. Do not accept code you cannot explain. Review each change before the next one starts.
- Solve the problem as stated. Do not draft, follow up or audit before you are asked.
- Name the failure mode when you notice it: Forming, Dislodging, Assumption, Location, Achievement, Progression, Interruption, Mislead. Apply its remedy.
- When there is so much to do that nothing moves, pick the smallest next piece and finish it.
- Keep a concerns record in the design doc: one row per concept (1 to 111), answered applies, not applicable or deferred, with a reason and evidence. Add it when asked for an audit, and at the start of any new project.