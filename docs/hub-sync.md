# Hub Sync

## Purpose

Reads the UMWAYI context hub repo directly (public, no auth required) and converts activity
there into routine completions, so journaling and writing don't have to be logged twice, once
by doing them and once by telling Routine Machine about it.

## Current state

Stage 1 built: `HubJournalReader` (`backend/src/main/java/com/routinemachine/core/hubsync/`).
Unlocks a short-lived shallow clone of UMWAYI's journal with git-crypt and extracts the
`Current book:` line. Not called by any scheduler yet. See the key-handling note below and
`backend.md`.

The rest of Phase 1 scope is not started: on each check, list files under `journal/entries/`
and `articles/` in the UMWAYI repo, compare against what's already been recorded, and mark the
`journaling` or `writing` routine item as done for the corresponding date on any new or
changed file.

**One narrow addition:** while checking a journal entry for its existence, also look for a
single specific line matching `Current book: <title>`, case-insensitive, anywhere in the
file. If found, write a new row to `current_reading_log` with that title, `source =
'hub-sync'`. This is a deliberate, narrow exception to the rule below, extracting one
explicit, self-declared line, not inferring or interpreting content. If the convention isn't
present in an entry, nothing happens, no error, no guess.

**Key handling:** `journal/entries/` and `journal/weekly-summaries/` in UMWAYI are encrypted
with git-crypt. File names and commit metadata stay readable through the public API; content
reads need the key. The backend reads it from `UMWAYI_JOURNAL_KEY_B64`, the base64 of an
exported git-crypt key file. Set in `infra/.env` (gitignored, copy from `infra/.env.example`)
or however the deployment target injects environment variables. The key never appears in a
committed file, an image layer, a log line, or a test fixture, only the variable name does.
Leave it unset to disable journal reading; the backend still starts. See the open questions
below for rotation and the key's blast radius.

## Decisions log

| Date | Decision | Reason |
|------|----------|--------|
| 2026-10-14 | `HubJournalReader` returns empty and logs one line, no content, when the key is missing, when clone or unlock fails, or when a file still has the git-crypt header after unlock | The app must still start without the key (most environments won't set it), and a failed or partial unlock must never be mistaken for a real "no match" |
| 2026-10-14 | Each `readCurrentBook()` call makes its own temp dir and shallow clone, unlocks, reads, and deletes both before returning | No unlocked clone or key file is kept between calls. Shortens the window the decrypted content and the key both exist on disk, and avoids a stale clone drifting from UMWAYI's actual latest entries |
| 2026-10-14 | Key delivered via `UMWAYI_JOURNAL_KEY_B64`, the base64 of an exported git-crypt key file, read fresh from the environment on every call | git-crypt needs the raw key file; base64 in an env var is the standard way to pass binary secrets through environment variables and container orchestration without writing them to disk ahead of time. Reading fresh (not caching) means rotating the variable takes effect on the next call, no restart-timing assumptions baked in |
| 2026-10-14 | Content reads use a git-crypt-unlocked shallow clone, not the GitHub API | File content isn't available through the API without decrypting it client-side first; git-crypt's own CLI is the documented, non-reimplemented way to do that, so cloning and unlocking locally was simpler and more correct than parsing the git-crypt format directly |
| 2026-09-19 | Added one narrow content check: a `Current book:` line, updating `current_reading_log` | The person asked to update which book they're reading as part of journaling itself, not as a separate action. A single, explicit, self-declared line is extraction, not inference, so it doesn't violate the no-content-parsing rule below in spirit, though it is content-parsing in the literal sense, worth naming plainly rather than quietly stretching the original rule |
| 2026-09-18 | Read UMWAYI directly rather than manual re-logging | Journaling and writing already happen in UMWAYI; requiring a second manual entry in Routine Machine duplicates effort and will get skipped under exactly the kind of overload this project exists to reduce |
| 2026-09-18 | Detects that an entry or article exists, does not parse its content or infer goal relevance, aside from the one narrow exception above | Content-based tagging is a pattern-detection problem, belongs in Phase 4. Phase 1 stays rule-based, not inferential, per PROJECT.md's phase rules |
| 2026-09-18 | File names come from the public GitHub API, no token stored; content reads use a git-crypt unlocked shallow clone with the key from `UMWAYI_JOURNAL_KEY_B64` | UMWAYI is a public repo, so listing file names needs no credential; the journal's content is encrypted, which the API read alone can't see past |

## Open questions

- Poll on a schedule, or trigger manually. Decide when Phase 1 implementation starts.
- Whether a moved or renamed file in UMWAYI could get double-counted. Needs a real check
  once this is implemented, not assumed away here.
- Whether the `Current book:` convention should be documented somewhere in UMWAYI itself
  (e.g. the journal template), so it's discoverable rather than only known here.
- git-crypt uses one key for the whole encrypted set, `journal/entries/` and
  `journal/weekly-summaries/`. It can't be scoped to one file. Anything holding
  `UMWAYI_JOURNAL_KEY_B64` can decrypt every journal entry and weekly summary, not just the
  `Current book:` line, so the backend now holds read access to the full private journal, not
  the narrow slice it actually uses.
- Least-privilege alternative to holding the full key: UMWAYI's `site/now.md` already carries
  a plaintext "currently reading" field, or the `Current book:` value could be mirrored to its
  own plaintext file on write. Either would let hub-sync read the current book with no key at
  all. Revisit before Stage 3 (writing `current_reading_log` rows); Stage 1 took the full-key
  path because no narrower read existed yet to build against.
- Key rotation: in UMWAYI, re-key git-crypt or add a replacement key (`git-crypt init` run
  again, or drop and re-add a collaborator key, depending on how UMWAYI manages git-crypt
  access). Export the new key file (`git-crypt export-key <path>`). Base64 it
  (`base64 -i <path>`, or `base64 <path>` on Linux). Replace the `UMWAYI_JOURNAL_KEY_B64`
  value in `infra/.env` or wherever the deployment injects it. Restart the backend. No code
  change: the reader reads the variable fresh on every call, nothing is cached.