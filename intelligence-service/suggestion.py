from collections import defaultdict
from dataclasses import dataclass
from typing import Optional


class NoTopicsAvailableError(Exception):
    pass


@dataclass
class Suggestion:
    goal: dict
    topic: dict
    last_logged_on: Optional[str]
    reason: str


def _most_recent_log_date_by_topic(logs: list[dict]) -> dict[int, str]:
    most_recent: dict[int, str] = {}
    for entry in logs:
        topic_id = entry.get("learningTopicId")
        if topic_id is None:
            continue
        log_date = entry["logDate"]
        if topic_id not in most_recent or log_date > most_recent[topic_id]:
            most_recent[topic_id] = log_date
    return most_recent


def choose_suggestion(goals: list[dict], topics: list[dict], logs: list[dict]) -> Suggestion:
    topics_by_goal: dict[int, list[dict]] = defaultdict(list)
    for topic in topics:
        topics_by_goal[topic["goalId"]].append(topic)

    eligible_goals = [goal for goal in goals if topics_by_goal.get(goal["id"])]
    if not eligible_goals:
        raise NoTopicsAvailableError("No goal has any learning topics to suggest")

    most_recent_by_topic = _most_recent_log_date_by_topic(logs)

    def topic_sort_key(topic: dict) -> tuple:
        last_logged = most_recent_by_topic.get(topic["id"])
        # None (never logged) sorts before any real date, since it needs attention most.
        return (last_logged is not None, last_logged, topic["id"])

    def goal_sort_key(goal: dict) -> tuple:
        goal_topics = topics_by_goal[goal["id"]]
        last_logged_dates = [most_recent_by_topic.get(t["id"]) for t in goal_topics]
        dated = [d for d in last_logged_dates if d is not None]
        goal_last_logged = max(dated) if dated else None
        return (goal_last_logged is not None, goal_last_logged, goal["id"])

    chosen_goal = min(eligible_goals, key=goal_sort_key)
    chosen_topic = min(topics_by_goal[chosen_goal["id"]], key=topic_sort_key)
    last_logged_on = most_recent_by_topic.get(chosen_topic["id"])

    reason = (
        f"'{chosen_goal['name']}' has had the least recent attention "
        f"({'last logged ' + last_logged_on if last_logged_on else 'never logged'})"
    )

    return Suggestion(goal=chosen_goal, topic=chosen_topic, last_logged_on=last_logged_on, reason=reason)
