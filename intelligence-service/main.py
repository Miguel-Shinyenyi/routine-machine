import os
from datetime import date

import httpx
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

from assess_note import AssessmentParseError, build_user_message, parse_assessment_response, ASSESSMENT_SYSTEM_PROMPT
from patterns import compute_patterns
from suggestion import NoTopicsAvailableError, choose_suggestion
from tally import compute_tally

BACKEND_BASE_URL = os.environ.get("BACKEND_BASE_URL", "http://localhost:8080")
ANTHROPIC_BASE_URL = os.environ.get("ANTHROPIC_BASE_URL", "https://api.anthropic.com")
# A small, fast model is enough for a short note-assessment prompt; override via env var
# rather than a code change if that stops being true.
ANTHROPIC_MODEL = os.environ.get("ANTHROPIC_MODEL", "claude-haiku-5-5")

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


class CompletionRateView(BaseModel):
    routineItemId: int
    name: str
    loggedDays: int
    totalDays: int
    rate: float


class StreakView(BaseModel):
    routineItemId: int
    name: str
    currentStreak: int
    longestStreak: int


class SkipHeavyDayView(BaseModel):
    date: str
    completedCount: int
    totalItems: int
    skipRatio: float


class TopicRoutineCorrelationView(BaseModel):
    daysWithTopicLog: int
    daysWithoutTopicLog: int
    avgRoutineCompletionsOnTopicLogDays: float
    avgRoutineCompletionsOnOtherDays: float


class PatternsResponse(BaseModel):
    completionRates: list[CompletionRateView]
    streaks: list[StreakView]
    skipHeavyDays: list[SkipHeavyDayView]
    topicRoutineCorrelation: TopicRoutineCorrelationView


class DayShownUpView(BaseModel):
    date: str
    shownUp: bool


class GoalWeekHoursView(BaseModel):
    goalId: int
    weekStart: str
    cumulativeHours: float


class RoutineShownUpView(BaseModel):
    routineItemId: int
    name: str
    shownUpDays: int
    windowDays: int


class TallyResponse(BaseModel):
    daysShownUp: list[DayShownUpView]
    daysShownUpCount: int
    goalWeekHours: list[GoalWeekHoursView]
    routineShownUp: list[RoutineShownUpView]


class ReadingNoteAssessmentRequest(BaseModel):
    noteText: str
    bookTitle: str
    currentChapter: int | None = None
    totalChapters: int | None = None


class ReadingNoteAssessmentResponse(BaseModel):
    chapterPlaced: str
    ownWordsPercentage: int
    ideasConnected: list[str]
    quotedPhrases: list[str]
    questionBack: str


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


@app.get("/patterns", response_model=PatternsResponse)
def patterns():
    with httpx.Client(base_url=BACKEND_BASE_URL, timeout=5.0) as client:
        routine_items = client.get("/api/routine-items").raise_for_status().json()
        logs = client.get("/api/daily-logs").raise_for_status().json()

    result = compute_patterns(routine_items, logs, today=date.today())

    return PatternsResponse(
        completionRates=[
            CompletionRateView(
                routineItemId=r.routine_item_id, name=r.name, loggedDays=r.logged_days,
                totalDays=r.total_days, rate=r.rate,
            )
            for r in result.completion_rates
        ],
        streaks=[
            StreakView(
                routineItemId=s.routine_item_id, name=s.name,
                currentStreak=s.current_streak, longestStreak=s.longest_streak,
            )
            for s in result.streaks
        ],
        skipHeavyDays=[
            SkipHeavyDayView(
                date=d.date, completedCount=d.completed_count,
                totalItems=d.total_items, skipRatio=d.skip_ratio,
            )
            for d in result.skip_heavy_days
        ],
        topicRoutineCorrelation=TopicRoutineCorrelationView(
            daysWithTopicLog=result.topic_routine_correlation.days_with_topic_log,
            daysWithoutTopicLog=result.topic_routine_correlation.days_without_topic_log,
            avgRoutineCompletionsOnTopicLogDays=result.topic_routine_correlation.avg_routine_completions_on_topic_log_days,
            avgRoutineCompletionsOnOtherDays=result.topic_routine_correlation.avg_routine_completions_on_other_days,
        ),
    )


@app.get("/tally", response_model=TallyResponse)
def tally(windowDays: int = 28):
    with httpx.Client(base_url=BACKEND_BASE_URL, timeout=5.0) as client:
        routine_items = client.get("/api/routine-items").raise_for_status().json()
        learning_topics = client.get("/api/learning-topics").raise_for_status().json()
        logs = client.get("/api/daily-logs").raise_for_status().json()

    result = compute_tally(routine_items, learning_topics, logs, today=date.today(), window_days=windowDays)

    return TallyResponse(
        daysShownUp=[DayShownUpView(date=d.date, shownUp=d.shown_up) for d in result.days_shown_up],
        daysShownUpCount=result.days_shown_up_count,
        goalWeekHours=[
            GoalWeekHoursView(goalId=w.goal_id, weekStart=w.week_start, cumulativeHours=w.cumulative_hours)
            for w in result.goal_week_hours
        ],
        routineShownUp=[
            RoutineShownUpView(
                routineItemId=r.routine_item_id, name=r.name, shownUpDays=r.shown_up_days,
                windowDays=r.window_days,
            )
            for r in result.routine_shown_up
        ],
    )


@app.post("/reading-notes/assess", response_model=ReadingNoteAssessmentResponse)
def assess_reading_note(request: ReadingNoteAssessmentRequest):
    api_key = os.environ.get("ANTHROPIC_API_KEY")
    if not api_key:
        raise HTTPException(status_code=503, detail="ANTHROPIC_API_KEY is not set, reading note assessment is unavailable")

    user_message = build_user_message(
        request.noteText, request.bookTitle, request.currentChapter, request.totalChapters)

    with httpx.Client(base_url=ANTHROPIC_BASE_URL, timeout=30.0) as client:
        response = client.post(
            "/v1/messages",
            headers={
                "x-api-key": api_key,
                "anthropic-version": "2023-06-01",
                "content-type": "application/json",
            },
            json={
                "model": ANTHROPIC_MODEL,
                "max_tokens": 1024,
                "system": ASSESSMENT_SYSTEM_PROMPT,
                "messages": [{"role": "user", "content": user_message}],
            },
        )
    try:
        response.raise_for_status()
    except httpx.HTTPStatusError as exc:
        raise HTTPException(
            status_code=502, detail=f"Anthropic API request failed: {response.status_code} {response.text}",
        ) from exc
    raw_text = "".join(block["text"] for block in response.json()["content"] if block["type"] == "text")

    try:
        result = parse_assessment_response(raw_text)
    except AssessmentParseError as exc:
        raise HTTPException(status_code=502, detail=f"could not parse the assessment: {exc}") from exc

    return ReadingNoteAssessmentResponse(
        chapterPlaced=result.chapter_placed,
        ownWordsPercentage=result.own_words_percentage,
        ideasConnected=result.ideas_connected,
        quotedPhrases=result.quoted_phrases,
        questionBack=result.question_back,
    )
