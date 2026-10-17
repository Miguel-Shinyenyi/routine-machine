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
