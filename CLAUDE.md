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
| Tests | `docs/testing.md` |

## Rules that already apply

- TDD is not optional. Write the failing test before the implementation for every new piece of logic.
- Build only the current phase. Do not write later-phase logic, even if it looks easy. Phase 4 does not start until Phase 2 has real logged data behind it.
- Update the component doc in the same change as the code.
- No secrets, keys or credentials in any file. Use environment variables and name them only.
- The `Current book:` line in UMWAYI's `journal/TEMPLATE.md` and the matching string in `docs/hub-sync.md` change together, in one change. If only one changes, hub-sync stops matching with no error.
- If anything in the docs is ambiguous or wrong, flag it. Do not decide silently. Decisions made here are not recorded in the journal process unless you say so.
- Do not push or open a pull request unless the task says so.

## Known risk

`docs/hub-sync.md` plans to read UMWAYI's `journal/entries/` through the public GitHub API. Those files are now encrypted with git-crypt, so a plain read returns ciphertext. Check this before building hub-sync. Do not store a decryption key in this repo.

## Run and check

`./mvnw test` in `backend/` needs Docker for Testcontainers. `pytest` in `intelligence-service/`. `npm run lint`, `npm test` and `npm run build` in `frontend/`. Commands and ports are in `PROJECT.md`.