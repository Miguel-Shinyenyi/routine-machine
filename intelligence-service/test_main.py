import json
from datetime import date, timedelta

import httpx
import respx
from fastapi.testclient import TestClient

from main import app, BACKEND_BASE_URL, ANTHROPIC_BASE_URL

client = TestClient(app)


def test_health_check():
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


@respx.mock
def test_suggestion_calls_the_backend_and_returns_a_reasoned_suggestion():
    respx.get(f"{BACKEND_BASE_URL}/api/goals").mock(
        return_value=httpx.Response(200, json=[
            {"id": 1, "name": "cmu-masters-application", "description": None},
            {"id": 2, "name": "target-roles", "description": None},
        ])
    )
    respx.get(f"{BACKEND_BASE_URL}/api/learning-topics").mock(
        return_value=httpx.Response(200, json=[
            {"id": 10, "name": "CMU essay draft", "goalId": 1, "goalName": "cmu-masters-application"},
            {"id": 11, "name": "PostHog product analytics", "goalId": 2, "goalName": "target-roles"},
        ])
    )
    respx.get(f"{BACKEND_BASE_URL}/api/daily-logs").mock(
        return_value=httpx.Response(200, json=[
            {"id": 1, "logDate": "2026-09-10", "routineItemId": None, "learningTopicId": 10, "source": "manual"},
        ])
    )

    response = client.get("/suggestion")

    assert response.status_code == 200
    body = response.json()
    assert body["goal"]["id"] == 2
    assert body["topic"]["id"] == 11
    assert body["lastLoggedOn"] is None
    assert "target-roles" in body["reason"]


@respx.mock
def test_suggestion_returns_404_when_no_learning_topics_exist_yet():
    respx.get(f"{BACKEND_BASE_URL}/api/goals").mock(
        return_value=httpx.Response(200, json=[{"id": 1, "name": "cmu-masters-application", "description": None}])
    )
    respx.get(f"{BACKEND_BASE_URL}/api/learning-topics").mock(return_value=httpx.Response(200, json=[]))
    respx.get(f"{BACKEND_BASE_URL}/api/daily-logs").mock(return_value=httpx.Response(200, json=[]))

    response = client.get("/suggestion")

    assert response.status_code == 404


@respx.mock
def test_suggestion_ignores_goals_and_logs_owned_by_a_hillside_demo_user():
    # Phase 8's hillside demo users each have their own goals/daily_logs now (userId set);
    # this service has no "current user" concept and must keep treating only the unowned
    # (userId None) data as its single-user world, or demo data leaks into real suggestions.
    respx.get(f"{BACKEND_BASE_URL}/api/goals").mock(
        return_value=httpx.Response(200, json=[
            {"id": 1, "name": "cmu-masters-application", "description": None, "userId": None},
            {"id": 99, "name": "someone else's goal", "description": None, "userId": 7},
        ])
    )
    respx.get(f"{BACKEND_BASE_URL}/api/learning-topics").mock(
        return_value=httpx.Response(200, json=[
            {"id": 10, "name": "CMU essay draft", "goalId": 1, "goalName": "cmu-masters-application"},
        ])
    )
    respx.get(f"{BACKEND_BASE_URL}/api/daily-logs").mock(
        return_value=httpx.Response(200, json=[
            {
                "id": 1, "logDate": "2026-09-10", "routineItemId": None, "learningTopicId": 10,
                "source": "manual", "userId": None,
            },
            {
                "id": 2, "logDate": "2026-10-16", "routineItemId": None, "learningTopicId": 10,
                "source": "manual", "userId": 7,
            },
        ])
    )

    response = client.get("/suggestion")

    assert response.status_code == 200
    # Only the unowned log should count toward "last logged on" — if the demo user's log
    # leaked in, this would be the more recent 2026-10-16 date instead.
    assert response.json()["lastLoggedOn"] == "2026-09-10"


@respx.mock
def test_patterns_calls_the_backend_and_returns_computed_statistics():
    today = date.today()
    created = today - timedelta(days=1)

    respx.get(f"{BACKEND_BASE_URL}/api/routine-items").mock(
        return_value=httpx.Response(200, json=[
            {"id": 1, "name": "journaling", "description": None, "createdAt": f"{created}T08:00:00Z"},
        ])
    )
    respx.get(f"{BACKEND_BASE_URL}/api/daily-logs").mock(
        return_value=httpx.Response(200, json=[
            {"id": 1, "logDate": str(created), "routineItemId": 1, "learningTopicId": None, "source": "manual"},
            {"id": 2, "logDate": str(today), "routineItemId": 1, "learningTopicId": None, "source": "manual"},
        ])
    )

    response = client.get("/patterns")

    assert response.status_code == 200
    body = response.json()
    assert body["completionRates"] == [
        {"routineItemId": 1, "name": "journaling", "loggedDays": 2, "totalDays": 2, "rate": 1.0}
    ]
    assert body["streaks"][0]["currentStreak"] == 2
    assert body["streaks"][0]["longestStreak"] == 2
    assert body["skipHeavyDays"] == []
    assert body["topicRoutineCorrelation"]["daysWithTopicLog"] == 0


@respx.mock
def test_tally_calls_the_backend_and_returns_computed_statistics():
    today = date.today()

    respx.get(f"{BACKEND_BASE_URL}/api/routine-items").mock(
        return_value=httpx.Response(200, json=[
            {"id": 1, "name": "deep work", "createdAt": f"{today}T08:00:00Z", "goalId": 5},
        ])
    )
    respx.get(f"{BACKEND_BASE_URL}/api/learning-topics").mock(return_value=httpx.Response(200, json=[]))
    respx.get(f"{BACKEND_BASE_URL}/api/daily-logs").mock(
        return_value=httpx.Response(200, json=[
            {
                "id": 1, "logDate": str(today), "routineItemId": 1, "learningTopicId": None,
                "source": "manual", "measuredMinutes": 60,
            },
        ])
    )

    response = client.get("/tally")

    assert response.status_code == 200
    body = response.json()
    assert body["daysShownUpCount"] == 0  # only one routine tended, below the threshold of 3
    assert body["goalWeekHours"][0]["goalId"] == 5
    assert body["goalWeekHours"][0]["cumulativeHours"] == 1.0
    assert body["routineShownUp"][0]["shownUpDays"] == 1


# --- Per-user scoping, part 4 (docs/intelligence-service.md): /suggestion, /patterns and /tally
# take an optional userId. Given, every backend read carries it and only that user's rows count;
# omitted, only the unowned (pre-account) rows do, exactly as before.

def _mock_backend_for_user(user_id, goals=None, topics=None, items=None, logs=None):
    routes = {
        "goals": respx.get(f"{BACKEND_BASE_URL}/api/goals").mock(
            return_value=httpx.Response(200, json=goals or [])),
        "topics": respx.get(f"{BACKEND_BASE_URL}/api/learning-topics").mock(
            return_value=httpx.Response(200, json=topics or [])),
        "items": respx.get(f"{BACKEND_BASE_URL}/api/routine-items").mock(
            return_value=httpx.Response(200, json=items or [])),
        "logs": respx.get(f"{BACKEND_BASE_URL}/api/daily-logs").mock(
            return_value=httpx.Response(200, json=logs or [])),
    }
    return routes


def _asked_for_user(route, user_id):
    return route.called and all(call.request.url.params.get("userId") == str(user_id) for call in route.calls)


@respx.mock
def test_suggestion_asks_the_backend_for_that_user_and_ignores_rows_of_anyone_else():
    routes = _mock_backend_for_user(
        5,
        goals=[
            {"id": 1, "name": "mine", "description": None, "userId": 5},
            {"id": 2, "name": "someone elses", "description": None, "userId": 7},
        ],
        topics=[{"id": 10, "name": "my topic", "goalId": 1, "goalName": "mine"}],
        logs=[
            {"id": 1, "logDate": "2026-09-10", "routineItemId": None, "learningTopicId": 10,
             "source": "manual", "userId": 5},
            {"id": 2, "logDate": "2026-10-16", "routineItemId": None, "learningTopicId": 10,
             "source": "manual", "userId": 7},
        ],
    )

    response = client.get("/suggestion", params={"userId": 5})

    assert response.status_code == 200
    assert response.json()["goal"]["name"] == "mine"
    assert response.json()["lastLoggedOn"] == "2026-09-10"
    for name in ("goals", "topics", "logs"):
        assert _asked_for_user(routes[name], 5), name


@respx.mock
def test_suggestion_for_a_new_user_with_no_topics_is_a_404_not_someone_elses_topic():
    _mock_backend_for_user(9, goals=[], topics=[], logs=[])

    response = client.get("/suggestion", params={"userId": 9})

    assert response.status_code == 404


@respx.mock
def test_patterns_asks_the_backend_for_that_user_and_ignores_rows_of_anyone_else():
    today = date.today()
    routes = _mock_backend_for_user(
        5,
        items=[
            {"id": 1, "name": "mine", "userId": 5, "createdAt": f"{today}T08:00:00Z"},
            {"id": 2, "name": "someone elses", "userId": 7, "createdAt": f"{today}T08:00:00Z"},
        ],
        logs=[
            {"id": 1, "logDate": today.isoformat(), "routineItemId": 1, "learningTopicId": None,
             "source": "manual", "userId": 5},
            {"id": 2, "logDate": today.isoformat(), "routineItemId": 2, "learningTopicId": None,
             "source": "manual", "userId": 7},
        ],
    )

    response = client.get("/patterns", params={"userId": 5})

    assert response.status_code == 200
    assert [r["name"] for r in response.json()["completionRates"]] == ["mine"]
    for name in ("items", "logs"):
        assert _asked_for_user(routes[name], 5), name


@respx.mock
def test_tally_for_a_new_user_is_empty():
    _mock_backend_for_user(9, items=[], topics=[], logs=[])

    response = client.get("/tally", params={"userId": 9})

    assert response.status_code == 200
    assert response.json()["daysShownUpCount"] == 0
    assert response.json()["goalWeekHours"] == []


@respx.mock
def test_tally_asks_the_backend_for_that_user_and_ignores_rows_of_anyone_else():
    today = date.today()
    routes = _mock_backend_for_user(
        5,
        items=[{"id": 1, "name": "mine", "userId": 5, "createdAt": f"{today}T08:00:00Z"}],
        topics=[],
        logs=[
            {"id": 1, "logDate": today.isoformat(), "routineItemId": 1, "learningTopicId": None,
             "source": "manual", "userId": 5},
            {"id": 2, "logDate": (today - timedelta(days=1)).isoformat(), "routineItemId": 1,
             "learningTopicId": None, "source": "manual", "userId": 7},
        ],
    )

    response = client.get("/tally", params={"userId": 5})

    assert response.status_code == 200
    # Only user 5's log of today counts; user 7's log of yesterday would make this 2.
    assert [r["shownUpDays"] for r in response.json()["routineShownUp"]] == [1]
    for name in ("items", "topics", "logs"):
        assert _asked_for_user(routes[name], 5), name


@respx.mock
def test_without_a_user_id_the_backend_is_not_asked_for_one():
    routes = _mock_backend_for_user(None, items=[], topics=[], logs=[])

    client.get("/tally")

    assert all("userId" not in call.request.url.params for call in routes["logs"].calls)


@respx.mock
def test_assess_reading_note_calls_claude_and_returns_the_parsed_assessment(monkeypatch):
    monkeypatch.setenv("ANTHROPIC_API_KEY", "test-key")
    claude_payload = {
        "chapterPlaced": "Chapter 3, on habit formation",
        "ownWordsPercentage": 75,
        "ideasConnected": ["Links it to the earlier point about willpower"],
        "quotedPhrases": ["the plateau of latent potential"],
        "questionBack": "What would make this automatic for you?",
    }
    respx.post(f"{ANTHROPIC_BASE_URL}/v1/messages").mock(
        return_value=httpx.Response(200, json={
            "content": [{"type": "text", "text": json.dumps(claude_payload)}],
        })
    )

    response = client.post("/reading-notes/assess", json={
        "noteText": "This idea about habits stacking felt true to my own mornings.",
        "bookTitle": "Atomic Habits",
        "currentChapter": 3,
        "totalChapters": 20,
    })

    assert response.status_code == 200
    body = response.json()
    assert body["chapterPlaced"] == "Chapter 3, on habit formation"
    assert body["ownWordsPercentage"] == 75
    assert body["quotedPhrases"] == ["the plateau of latent potential"]


def test_assess_reading_note_returns_503_when_no_api_key_is_configured(monkeypatch):
    monkeypatch.delenv("ANTHROPIC_API_KEY", raising=False)

    response = client.post("/reading-notes/assess", json={
        "noteText": "Some note.",
        "bookTitle": "Some Book",
    })

    assert response.status_code == 503


@respx.mock
def test_assess_reading_note_returns_502_when_claude_responds_with_unparseable_content(monkeypatch):
    monkeypatch.setenv("ANTHROPIC_API_KEY", "test-key")
    respx.post(f"{ANTHROPIC_BASE_URL}/v1/messages").mock(
        return_value=httpx.Response(200, json={
            "content": [{"type": "text", "text": "I'm not able to assess this."}],
        })
    )

    response = client.post("/reading-notes/assess", json={
        "noteText": "Some note.",
        "bookTitle": "Some Book",
    })

    assert response.status_code == 502


@respx.mock
def test_assess_reading_note_returns_502_when_the_anthropic_api_request_fails(monkeypatch):
    monkeypatch.setenv("ANTHROPIC_API_KEY", "test-key")
    respx.post(f"{ANTHROPIC_BASE_URL}/v1/messages").mock(
        return_value=httpx.Response(400, json={
            "type": "error",
            "error": {"type": "invalid_request_error", "message": "something is wrong with the request"},
        })
    )

    response = client.post("/reading-notes/assess", json={
        "noteText": "Some note.",
        "bookTitle": "Some Book",
    })

    assert response.status_code == 502


@respx.mock
def test_analogy_calls_claude_and_returns_the_analogy_and_definition(monkeypatch):
    monkeypatch.setenv("ANTHROPIC_API_KEY", "test-key")
    respx.post(f"{ANTHROPIC_BASE_URL}/v1/messages").mock(
        return_value=httpx.Response(200, json={
            "content": [{"type": "text", "text": json.dumps({
                "analogy": "A goal counts once, however many replays.",
                "definition": "A key that makes a repeated request idempotent.",
            })}],
        })
    )

    response = client.post("/analogy", json={"topic": "idempotency keys", "interest": "football"})

    assert response.status_code == 200
    body = response.json()
    assert body["analogy"] == "A goal counts once, however many replays."
    assert body["definition"] == "A key that makes a repeated request idempotent."
    assert body["label"] == "analogy"
    assert body["topic"] == "idempotency keys"
    assert body["interest"] == "football"


@respx.mock
def test_analogy_is_cached_per_topic_and_interest_and_claude_is_called_only_once(monkeypatch):
    monkeypatch.setenv("ANTHROPIC_API_KEY", "test-key")
    route = respx.post(f"{ANTHROPIC_BASE_URL}/v1/messages").mock(
        return_value=httpx.Response(200, json={
            "content": [{"type": "text", "text": json.dumps({
                "analogy": "x", "definition": "y",
            })}],
        })
    )

    first = client.post("/analogy", json={"topic": "a cached topic", "interest": "chess"})
    second = client.post("/analogy", json={"topic": "a cached topic", "interest": "chess"})
    # Case-insensitive, whitespace-trimmed: the same pairing however it's typed next time.
    third = client.post("/analogy", json={"topic": "  A Cached Topic  ", "interest": "Chess"})

    assert first.status_code == second.status_code == third.status_code == 200
    assert second.json()["analogy"] == "x"
    assert route.call_count == 1


def test_analogy_returns_503_when_no_api_key_is_configured(monkeypatch):
    monkeypatch.delenv("ANTHROPIC_API_KEY", raising=False)

    response = client.post("/analogy", json={"topic": "a topic with no key configured", "interest": "music"})

    assert response.status_code == 503


@respx.mock
def test_analogy_returns_502_when_claude_responds_with_unparseable_content(monkeypatch):
    monkeypatch.setenv("ANTHROPIC_API_KEY", "test-key")
    respx.post(f"{ANTHROPIC_BASE_URL}/v1/messages").mock(
        return_value=httpx.Response(200, json={
            "content": [{"type": "text", "text": "I can't help with that."}],
        })
    )

    response = client.post("/analogy", json={"topic": "an unparseable topic", "interest": "cooking"})

    assert response.status_code == 502
