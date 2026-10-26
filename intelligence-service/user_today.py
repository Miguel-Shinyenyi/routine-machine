from datetime import date, datetime, timezone
from zoneinfo import ZoneInfo

import httpx

# "Today" as the person lives it (Backlog P). The server runs on UTC, so for someone in Nairobi (UTC+3) its date is still yesterday from
# midnight to 3 a.m. The tally and the patterns use the date in the person's saved time zone instead; the frontend does the same
# (frontend/lib/userToday.ts). A missing, unreal or unreadable zone means UTC, never a failed request.


def current_time() -> datetime:
    return datetime.now(timezone.utc)


def today_in_zone(zone, now: datetime | None = None) -> date:
    now = now or current_time()
    if isinstance(zone, str) and zone:
        try:
            return now.astimezone(ZoneInfo(zone)).date()
        except Exception:  # an unknown zone name, or no zone database: fall back below
            pass
    return now.astimezone(timezone.utc).date()


def user_today(client: httpx.Client, authorization: str | None) -> date:
    # Without a token there is no person to ask about. With one, the backend's own profile route gives their saved zone.
    if not authorization:
        return today_in_zone(None)
    try:
        profile = client.get("/api/users/me/profile").raise_for_status().json()
        return today_in_zone(profile.get("timeZone"))
    except Exception:
        return today_in_zone(None)
