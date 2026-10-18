from collections import defaultdict
from dataclasses import dataclass
from datetime import date, datetime, timedelta

# A day counts as "shown up" once at least this many distinct routine items were tended that
# day. Named and documented per docs/design.md's tally stick, not derived from any data.
TALLY_STICK_THRESHOLD = 3


@dataclass
class DayShownUp:
    date: str
    shown_up: bool


@dataclass
class GoalWeekHours:
    goal_id: int
    week_start: str
    cumulative_hours: float


@dataclass
class RoutineShownUp:
    routine_item_id: int
    name: str
    shown_up_days: int
    window_days: int


@dataclass
class Tally:
    days_shown_up: list[DayShownUp]
    days_shown_up_count: int
    goal_week_hours: list[GoalWeekHours]
    routine_shown_up: list[RoutineShownUp]


def _parse_log_date(value: str) -> date:
    return date.fromisoformat(value)


def _parse_created_date(value: str) -> date:
    return datetime.fromisoformat(value).date()


def _week_start(day: date) -> date:
    # Monday-anchored week, matching the schedule's own Monday-first weekly layout.
    return day - timedelta(days=day.weekday())


def _daterange(start: date, end: date):
    for offset in range((end - start).days + 1):
        yield start + timedelta(days=offset)


def compute_tally(
        routine_items: list[dict], learning_topics: list[dict], daily_logs: list[dict],
        today: date, window_days: int = 28, since_start: bool = False) -> Tally:
    goal_id_by_routine_item = {
        item["id"]: item["goalId"] for item in routine_items if item.get("goalId") is not None
    }
    goal_id_by_topic = {topic["id"]: topic["goalId"] for topic in learning_topics}

    # "Since the start" (docs/design.md's Tally toggle) means since the earliest real record,
    # not a fixed calendar window — a flat large window_days would make the tally stick render
    # one notch per day back to a date nothing ever happened, most of them meaningless.
    if since_start:
        log_dates = [_parse_log_date(entry["logDate"]) for entry in daily_logs]
        window_start = min(log_dates) if log_dates else today
    else:
        window_start = today - timedelta(days=window_days - 1)

    routines_tended_by_date: dict[date, set[int]] = defaultdict(set)
    logged_dates_by_item: dict[int, set[date]] = defaultdict(set)
    minutes_by_goal_and_week: dict[tuple[int, date], int] = defaultdict(int)

    for entry in daily_logs:
        log_date = _parse_log_date(entry["logDate"])
        routine_item_id = entry.get("routineItemId")
        learning_topic_id = entry.get("learningTopicId")
        measured_minutes = entry.get("measuredMinutes")

        if routine_item_id is not None:
            routines_tended_by_date[log_date].add(routine_item_id)
            logged_dates_by_item[routine_item_id].add(log_date)
            goal_id = goal_id_by_routine_item.get(routine_item_id)
        else:
            goal_id = goal_id_by_topic.get(learning_topic_id)

        if goal_id is not None and measured_minutes:
            minutes_by_goal_and_week[(goal_id, _week_start(log_date))] += measured_minutes

    days_shown_up = [
        DayShownUp(
            date=day.isoformat(),
            shown_up=len(routines_tended_by_date.get(day, set())) >= TALLY_STICK_THRESHOLD,
        )
        for day in _daterange(window_start, today)
    ]
    days_shown_up_count = sum(1 for d in days_shown_up if d.shown_up)

    goal_week_hours = []
    for goal_id in sorted({g for g, _ in minutes_by_goal_and_week}):
        weeks = sorted(week for (g, week) in minutes_by_goal_and_week if g == goal_id)
        cumulative_minutes = 0
        for week in weeks:
            cumulative_minutes += minutes_by_goal_and_week[(goal_id, week)]
            goal_week_hours.append(GoalWeekHours(
                goal_id=goal_id, week_start=week.isoformat(), cumulative_hours=cumulative_minutes / 60,
            ))

    routine_shown_up = []
    for item in routine_items:
        created_date = _parse_created_date(item["createdAt"])
        item_window_start = max(window_start, created_date)
        item_window_days = (today - item_window_start).days + 1
        logged_days = len({
            d for d in logged_dates_by_item.get(item["id"], set())
            if item_window_start <= d <= today
        })
        routine_shown_up.append(RoutineShownUp(
            routine_item_id=item["id"], name=item["name"], shown_up_days=logged_days,
            window_days=item_window_days,
        ))

    return Tally(days_shown_up, days_shown_up_count, goal_week_hours, routine_shown_up)
