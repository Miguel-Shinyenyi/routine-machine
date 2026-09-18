import os

import httpx
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

from suggestion import NoTopicsAvailableError, choose_suggestion

BACKEND_BASE_URL = os.environ.get("BACKEND_BASE_URL", "http://localhost:8080")

app = FastAPI(title="Routine Machine Intelligence Service")


class GoalView(BaseModel):
    id: int
    name: str


class TopicView(BaseModel):
    id: int
    name: str


class SuggestionResponse(BaseModel):
    goal: GoalView
    topic: TopicView
    lastLoggedOn: str | None
    reason: str


@app.get("/health")
def health():
    return {"status": "ok"}


@app.get("/suggestion", response_model=SuggestionResponse)
def suggestion():
    with httpx.Client(base_url=BACKEND_BASE_URL, timeout=5.0) as client:
        goals = client.get("/api/goals").raise_for_status().json()
        topics = client.get("/api/learning-topics").raise_for_status().json()
        logs = client.get("/api/daily-logs").raise_for_status().json()

    try:
        result = choose_suggestion(goals, topics, logs)
    except NoTopicsAvailableError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc

    return SuggestionResponse(
        goal=GoalView(id=result.goal["id"], name=result.goal["name"]),
        topic=TopicView(id=result.topic["id"], name=result.topic["name"]),
        lastLoggedOn=result.last_logged_on,
        reason=result.reason,
    )
