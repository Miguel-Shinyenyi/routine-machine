# Contributing

Read `PROJECT.md` first (stack, phases, rules), then `docs/DOCS_MAINTENANCE.md`, then the one doc for the part you change. `CLAUDE.md` has the routing table.

## Rules

- Write the failing test first. Every new piece of logic gets one.
- Build only the current phase. Later-phase logic waits.
- Update the component doc in the same change as the code.
- No secrets, keys or credentials in any file. Use environment variables and name them only.
- If a doc is wrong or unclear, say so in the pull request. Do not decide silently.

## Run the checks

CI runs the same three on every pull request (`.github/workflows/ci.yml`).

| Part | Folder | Command |
|---|---|---|
| Backend | `backend/` | `./mvnw test` (needs Docker, for Testcontainers) |
| Intelligence service | `intelligence-service/` | `pytest` |
| Frontend | `frontend/` | `npm run lint`, `npm test`, `npm run build` |

Setup and ports are in `PROJECT.md`, "Running the application".

On `frontend/`, install with `npm ci`. If npm 10.9.2 crashes while changing dependencies, use `npm install --legacy-peer-deps` and check that a normal `npm ci` still works from the new lockfile.

## Pull requests

- Keep one purpose per pull request.
- Fill in the template. Say which checks you ran and show their output.
- Say how to undo the change.

## Security problems

Do not open an issue. See `SECURITY.md`.
