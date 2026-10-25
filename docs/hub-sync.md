# Hub Sync

## Purpose

Reads the UMWAYI context hub repo directly (public, no auth required) and converts activity
there into routine completions, so journaling and writing don't have to be logged twice, once
by doing them and once by telling Routine Machine about it.

## Current state

Phase 1 built: `HubSyncService` (`backend/src/main/java/com/routinemachine/core/hubsync/`),
polling on a schedule (`@Scheduled`, default every 15 minutes, `hub-sync.poll-interval-ms`;
disabled entirely with `hub-sync.enabled=false`, set `false` in every test context so the
suite never makes a real outbound call — see `testing.md`).

Each run:

- Lists `journal/entries/` over the public GitHub API (`GitHubContentClient`, no credential:
  UMWAYI is public). A file named `YYYY-MM-DD.md` dates a `journaling` daily_log for that date.
  A file whose name doesn't match that pattern is skipped and logged, not guessed at.
- Lists `site/philosophy/` and `site/tech/` the same way, UMWAYI's real essay directories (see
  the decisions log for why, not the `articles/` path this doc originally assumed). Since
  these filenames carry no date, each file's date comes from its earliest commit
  (`GitHubContentClient.firstCommitDate`, a `GET .../commits?path=` call), dating a `writing`
  daily_log.
- Dedupes by each file's git blob sha (`hub_sync_processed_files`, migration V5), not its
  path, so a file moved or renamed in UMWAYI — same content, new path — is recognised as
  already counted instead of logged a second time. This is the real check the open question
  below used to defer.
- If no routine item is named `journaling` or `writing` (case-insensitive), the file is still
  recorded as seen but nothing is logged, so adding the routine item later doesn't retroactively
  flood daily_logs with every file hub-sync has ever looked at.
- Also calls `HubJournalReader.readCurrentBook()` (Stage 1, below) and writes a new `books`
  row (Phase 7's `Book`, which superseded `current_reading_log` as hub-sync's write target —
  see the decisions log) only when the title actually changed from the current book, keeping
  it append-only without writing a duplicate row on every poll.

Stage 1 (title extraction) built earlier: `HubJournalReader`. Unlocks a short-lived shallow
clone of UMWAYI's journal with git-crypt and extracts the `Current book:` line. See the
key-handling note below and `backend.md`.

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
| 2026-10-21 | Hub-sync logs only into the owner's (or an unowned) "journaling"/"writing" item, and writes the current book as the owner's | The hub is the owner's own journal. Looking the item up by name across every account let a new user's "journaling" item receive the owner's journal entries, and writing an unowned book let any account's book change the sync's idea of the current one. See `backend.md`'s per-user scoping part 5 |
| 2026-10-17 | `syncCurrentBook()` now writes to Phase 7's `books` table, not `current_reading_log` | Agreed with the user before building Phase 7: `books` is a richer entity (chapter progress, author) that supersedes `current_reading_log` as the source of truth for "what am I reading." `current_reading_log`'s existing rows are kept for history, but hub-sync no longer writes new ones. Flagged and confirmed rather than decided silently, since it changes an existing integration point |
| 2026-10-17 | Articles come from UMWAYI's real `site/philosophy/` and `site/tech/` directories, not a top-level `articles/` path | This doc originally assumed `articles/`, written before UMWAYI's actual structure was checked. The real repo has no such directory; `site/philosophy/` and `site/tech/` are the closest real equivalent (essay-style personal writing), confirmed by listing the repo directly. Flagged and confirmed rather than guessed silently, per the project's ambiguity rule |
| 2026-10-17 | Poll on a schedule (`@Scheduled`, default 15 minutes), not a manual trigger | Phase 6 frames hub-sync as "automatic counting"; a personal single-user tool has no reason to make the person remember to ask for a sync. `hub-sync.enabled=false` (set in every test) stops it from firing outside a real deployment |
| 2026-10-17 | Dedupe by each file's git blob sha, not its path | Answers the open question below: a file renamed in UMWAYI keeps its content sha, so `hub_sync_processed_files` (unique on `content_sha`) still recognises it as already counted. A path-based check would have logged it again under the new name |
| 2026-10-17 | A file with no matching routine item is still recorded as processed, nothing logged | Without this, adding a `journaling` or `writing` routine item later would cause every file hub-sync has ever seen to get logged all at once on the next poll, backdating weeks of completions that were never actually tracked that way |
| 2026-10-14 | `HubJournalReader` returns empty and logs one line, no content, when the key is missing, when clone or unlock fails, or when a file still has the git-crypt header after unlock | The app must still start without the key (most environments won't set it), and a failed or partial unlock must never be mistaken for a real "no match" |
| 2026-10-14 | Each `readCurrentBook()` call makes its own temp dir and shallow clone, unlocks, reads, and deletes both before returning | No unlocked clone or key file is kept between calls. Shortens the window the decrypted content and the key both exist on disk, and avoids a stale clone drifting from UMWAYI's actual latest entries |
| 2026-10-14 | Key delivered via `UMWAYI_JOURNAL_KEY_B64`, the base64 of an exported git-crypt key file, read fresh from the environment on every call | git-crypt needs the raw key file; base64 in an env var is the standard way to pass binary secrets through environment variables and container orchestration without writing them to disk ahead of time. Reading fresh (not caching) means rotating the variable takes effect on the next call, no restart-timing assumptions baked in |
| 2026-10-14 | Content reads use a git-crypt-unlocked shallow clone, not the GitHub API | File content isn't available through the API without decrypting it client-side first; git-crypt's own CLI is the documented, non-reimplemented way to do that, so cloning and unlocking locally was simpler and more correct than parsing the git-crypt format directly |
| 2026-09-19 | Added one narrow content check: a `Current book:` line, updating `current_reading_log` | The person asked to update which book they're reading as part of journaling itself, not as a separate action. A single, explicit, self-declared line is extraction, not inference, so it doesn't violate the no-content-parsing rule below in spirit, though it is content-parsing in the literal sense, worth naming plainly rather than quietly stretching the original rule |
| 2026-09-18 | Read UMWAYI directly rather than manual re-logging | Journaling and writing already happen in UMWAYI; requiring a second manual entry in Routine Machine duplicates effort and will get skipped under exactly the kind of overload this project exists to reduce |
| 2026-09-18 | Detects that an entry or article exists, does not parse its content or infer goal relevance, aside from the one narrow exception above | Content-based tagging is a pattern-detection problem, belongs in Phase 4. Phase 1 stays rule-based, not inferential, per PROJECT.md's phase rules |
| 2026-09-18 | File names come from the public GitHub API, no token stored; content reads use a git-crypt unlocked shallow clone with the key from `UMWAYI_JOURNAL_KEY_B64` | UMWAYI is a public repo, so listing file names needs no credential; the journal's content is encrypted, which the API read alone can't see past |

## Open questions

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

## GitHub's rate limit (found 25 Oct 2026)

Hub sync and the GitHub watering hole both call GitHub's public API **without a token**. GitHub allows that **60 requests an
hour per IP address**, shared by everything on that address, and answers `403` when it is used up. One sync run costs **4
requests** (one GitHub events call and three hub directory listings), the sync runs **every 15 minutes** and **once at every
backend start**. Steady state is therefore 16 requests an hour, a quarter of the quota; **every restart adds 4 more**, and
anything else on the same address (a browser, a script, a test) draws on the same 60.
- **How it showed up**: the owner page's sync table read `failing` for HUB and GITHUB for hours. On the day I looked the quota
  was at 0 of 60 and resetting at 21:03 local. I had restarted the backend about 20 times and run about 25 requests of my own.
- **What the log says now**: `hub-sync: listing journal/entries returned HTTP 403 (rate limited until 2026-10-25T18:03:23Z)`,
  read from GitHub's `X-RateLimit-Remaining` and `X-RateLimit-Reset` headers (`GitHubRateLimit`, 3 tests). Before, it said only
  `returned HTTP 403` or `ConnectException`. Failed fetches also log their whole cause now (`FailureDescription`).
- **Not solved**: an optional token would lift the limit to 5,000 an hour, and neither client can send one. That is a design
  change that needs a GitHub token from Miguel (an environment variable, never a file), so I did not build it. Until then,
  expect HUB and GITHUB to read `failing` for up to an hour after a burst of restarts, and for any machine whose address is
  shared with other GitHub callers.
- **Still unexplained**: earlier failures logged as `ConnectException` (for example at 16:53 UTC, five seconds after a start)
  were not 403s, and I never saw their cause. Read the next one.

