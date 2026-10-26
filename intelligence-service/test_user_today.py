from datetime import date, datetime, timezone

import httpx
import respx
from fastapi.testclient import TestClient

import main
import user_today
from main import app, BACKEND_BASE_URL
from user_today import today_in_zone

# Backlog P, the intelligence service's half: "today" for the tally and the patterns is the person's day, in the person's saved time
# zone, not the server's UTC date. The frontend already does the same (frontend/lib/userToday.ts).
LATE_SUNDAY_UTC = datetime(2026, 10, 25, 22, 30, tzinfo=timezone.utc)

client = TestClient(app)


def test_it_is_already_monday_in_nairobi_when_it_is_late_sunday_in_utc():
    assert today_in_zone("Africa/Nairobi", LATE_SUNDAY_UTC) == date(2026, 10, 26)


def test_it_is_still_sunday_in_utc_and_in_los_angeles_at_that_moment():
    assert today_in_zone("UTC", LATE_SUNDAY_UTC) == date(2026, 10, 25)
    assert today_in_zone("America/Los_Angeles", LATE_SUNDAY_UTC) == date(2026, 10, 25)


def test_the_date_line_is_respected_in_both_directions():
    assert today_in_zone("Pacific/Kiritimati", datetime(2026, 10, 25, 23, 59, 59, tzinfo=timezone.utc)) == date(2026, 10, 26)
    assert today_in_zone("America/Los_Angeles", datetime(2026, 10, 26, 0, 0, 1, tzinfo=timezone.utc)) == date(2026, 10, 25)


def test_daylight_saving_ends_in_london_at_01_utc_on_25_october_2026():
    assert today_in_zone("Europe/London", datetime(2026, 10, 25, 0, 30, tzinfo=timezone.utc)) == date(2026, 10, 25)
    assert today_in_zone("Europe/London", datetime(2026, 10, 25, 23, 30, tzinfo=timezone.utc)) == date(2026, 10, 25)
    assert today_in_zone("Europe/London", datetime(2026, 10, 26, 0, 0, tzinfo=timezone.utc)) == date(2026, 10, 26)


def test_no_zone_or_an_unreal_zone_means_utc_not_a_failed_request():
    for bad in (None, "", "Mars/Base", "not a zone", 42):
        assert today_in_zone(bad, LATE_SUNDAY_UTC) == date(2026, 10, 25), bad


def _backend(profile_response):
    respx.get(f"{BACKEND_BASE_URL}/api/routine-items").mock(return_value=httpx.Response(200, json=[]))
    respx.get(f"{BACKEND_BASE_URL}/api/learning-topics").mock(return_value=httpx.Response(200, json=[]))
    respx.get(f"{BACKEND_BASE_URL}/api/daily-logs").mock(return_value=httpx.Response(200, json=[]))
    respx.get(f"{BACKEND_BASE_URL}/api/users/me/profile").mock(return_value=profile_response)


def _capture(monkeypatch):
    seen = {}
    real_patterns, real_tally = main.compute_patterns, main.compute_tally

    def patterns(*args, **kwargs):
        seen["patterns"] = kwargs["today"]
        return real_patterns(*args, **kwargs)

    def tally(*args, **kwargs):
        seen["tally"] = kwargs["today"]
        return real_tally(*args, **kwargs)

    monkeypatch.setattr(main, "compute_patterns", patterns)
    monkeypatch.setattr(main, "compute_tally", tally)
    monkeypatch.setattr(user_today, "current_time", lambda: LATE_SUNDAY_UTC)
    return seen


AUTH = {"Authorization": "Bearer a-signed-token"}


@respx.mock
def test_tally_and_patterns_use_the_persons_own_day(monkeypatch):
    seen = _capture(monkeypatch)
    _backend(httpx.Response(200, json={"timeZone": "Africa/Nairobi"}))

    assert client.get("/tally", headers=AUTH).status_code == 200
    assert client.get("/patterns", headers=AUTH).status_code == 200

    assert seen == {"tally": date(2026, 10, 26), "patterns": date(2026, 10, 26)}


@respx.mock
def test_the_tally_window_really_ends_on_the_persons_day(monkeypatch):
    _capture(monkeypatch)
    _backend(httpx.Response(200, json={"timeZone": "Africa/Nairobi"}))

    days = client.get("/tally", headers=AUTH).json()["daysShownUp"]

    assert days[-1]["date"] == "2026-10-26"


@respx.mock
def test_a_utc_person_gets_the_utc_date(monkeypatch):
    seen = _capture(monkeypatch)
    _backend(httpx.Response(200, json={"timeZone": "UTC"}))

    client.get("/tally", headers=AUTH)

    assert seen["tally"] == date(2026, 10, 25)


@respx.mock
def test_without_a_token_it_stays_on_utc_and_never_asks_the_backend_for_a_profile(monkeypatch):
    seen = _capture(monkeypatch)
    _backend(httpx.Response(200, json={"timeZone": "Africa/Nairobi"}))

    client.get("/tally")

    assert seen["tally"] == date(2026, 10, 25)
    assert not any(call.request.url.path == "/api/users/me/profile" for call in respx.calls)


@respx.mock
def test_a_profile_that_cannot_be_read_falls_back_to_utc_and_the_request_still_works(monkeypatch):
    seen = _capture(monkeypatch)
    _backend(httpx.Response(500, json={}))

    assert client.get("/tally", headers=AUTH).status_code == 200
    assert seen["tally"] == date(2026, 10, 25)


@respx.mock
def test_a_saved_zone_that_is_not_real_falls_back_to_utc(monkeypatch):
    seen = _capture(monkeypatch)
    _backend(httpx.Response(200, json={"timeZone": "Mars/Base"}))

    assert client.get("/patterns", headers=AUTH).status_code == 200
    assert seen["patterns"] == date(2026, 10, 25)
