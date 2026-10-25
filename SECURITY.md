# Security

## Report a problem

Report a security problem privately. Do not open a public issue.

Use GitHub's private vulnerability reporting: the repository's **Security** tab, then **Report a vulnerability**.

Say what you found, where, and how to reproduce it. Do not include real people's data. If you reached any, say so and stop there.

You will get a reply from the person who runs the project. There is no fixed response time, because one person maintains this.

## What counts

- A way to read or change another user's data.
- A way to reach the owner-only pages or APIs (`/watchtower`, the API documentation) as someone else.
- A secret, key or token in the repository or its history.
- A way to make the analytics send a value the privacy page says it never sends.

## What is already known

- The development database password and the development JWT secret in the Docker Compose file are for local use. Replace them before any real deployment (`docs/backend.md`).
- `hub-sync` reads encrypted journal content only through the unlock flow in `docs/hub-sync.md`. The key comes from `UMWAYI_JOURNAL_KEY_B64` and is never stored here.

## Secrets

No file in this repository holds a secret. Configuration is by environment variable, and `infra/.env.example` lists the names only. If you find a value that looks like a secret, report it as above.
