import pytest

from suggestion import NoTopicsAvailableError, choose_suggestion

GOALS = [
    {"id": 1, "name": "cmu-masters-application"},
    {"id": 2, "name": "target-roles"},
    {"id": 3, "name": "ai-engineer-evidence"},
]

TOPICS = [
    {"id": 10, "name": "CMU essay draft", "goalId": 1},
    {"id": 11, "name": "PostHog product analytics", "goalId": 2},
    {"id": 12, "name": "write article on evals", "goalId": 3},
]


def log(learning_topic_id, log_date):
    return {"id": 1, "logDate": log_date, "routineItemId": None, "learningTopicId": learning_topic_id, "source": "manual"}


def test_picks_the_goal_whose_topics_have_never_been_logged_over_goals_with_recent_logs():
    logs = [
        log(10, "2026-09-01"),
        log(11, "2026-09-10"),
        # goal 3 / topic 12 has no logs at all
    ]

    result = choose_suggestion(GOALS, TOPICS, logs)

    assert result.goal["id"] == 3
    assert result.topic["id"] == 12
    assert result.last_logged_on is None


def test_picks_the_goal_with_the_oldest_most_recent_log_when_all_goals_have_logs():
    logs = [
        log(10, "2026-09-15"),
        log(11, "2026-09-01"),
        log(12, "2026-09-10"),
    ]

    result = choose_suggestion(GOALS, TOPICS, logs)

    assert result.goal["id"] == 2
    assert result.topic["id"] == 11
    assert result.last_logged_on == "2026-09-01"


def test_uses_the_most_recent_log_per_goal_not_the_average():
    topics = TOPICS + [{"id": 13, "name": "CMU recommendation letters", "goalId": 1}]
    logs = [
        log(10, "2026-01-01"),
        log(13, "2026-09-16"),
        log(11, "2026-09-14"),
        log(12, "2026-09-15"),
    ]

    result = choose_suggestion(GOALS, topics, logs)

    assert result.goal["id"] == 2
    assert result.topic["id"] == 11


def test_ignores_routine_item_logs_when_computing_topic_attention():
    logs = [
        {"id": 1, "logDate": "2026-09-17", "routineItemId": 99, "learningTopicId": None, "source": "manual"},
    ]

    result = choose_suggestion(GOALS, TOPICS, logs)

    # No learning-topic logs exist at all, so every goal is equally "never attended";
    # the deterministic tie-break falls back to lowest goal id.
    assert result.goal["id"] == 1
    assert result.last_logged_on is None


def test_raises_when_no_goal_has_any_learning_topics():
    with pytest.raises(NoTopicsAvailableError):
        choose_suggestion(GOALS, [], [])
