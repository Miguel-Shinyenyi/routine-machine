from datetime import date, timedelta

import httpx
import respx
from fastapi.testclient import TestClient

from main import app, BACKEND_BASE_URL

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
