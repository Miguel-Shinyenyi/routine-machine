

## The person's own "today" (Backlog P, part 2)

`/patterns` and `/tally` used the server's date (`date.today()`, UTC), so for someone in Nairobi (UTC+3) the tally's days ended on the previous day from midnight to 3 a.m. They now use the date in **the person's saved time zone**, as the frontend does (`docs/frontend.md`).

- **How.** `user_today.py`: `today_in_zone(zone, now)` gives the date in an IANA zone; `user_today(client, authorization)` asks the backend for the person's profile (`GET /api/users/me/profile`, with the token the request already carried) and reads `timeZone`. It is called inside the same client block as the other backend calls, so it adds one short call per request.
- **Falls back to UTC, never fails the request**: no token (there is no person to ask about; the backend refuses those requests anyway), a profile that cannot be read, a missing zone, or a zone name that is not real or not in the zone database. The container's own zone database is used (`zoneinfo` found `Africa/Nairobi` in `python:3.13-slim`, checked 26 Oct 2026).
- **Dockerfile**: the service's files are copied by name, so `user_today.py` is in that list (`intelligence-service/Dockerfile`); without it the container would fail to start.
- **Tests**: 11 in `test_user_today.py` (Nairobi, Los Angeles, the date line both ways, London daylight saving on 25 Oct 2026, bad zones, and the tally and patterns endpoints using the person's day, a UTC person getting the UTC date, no token, an unreadable profile, an unreal saved zone). Two deliberate breaks (ignore the saved zone; use the server's clock for the tally) failed 2 and 3. 98 tests in all.
- **Checked live** (26 Oct 2026, 05:41 UTC, throwaway account, a token minted in the shell and not printed): `/tally`'s last day was **2026-10-25** for `America/Los_Angeles` and **2026-10-26** for `Africa/Nairobi` and `Pacific/Kiritimati`. The zone was reset afterwards.
- **Not done**: `/suggestion` does not use a date, so it is unchanged; reading notes still log the day on the server's date (Backlog P (b)).

