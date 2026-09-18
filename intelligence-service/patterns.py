from collections import defaultdict
from dataclasses import dataclass
from datetime import date, datetime, timedelta

# A day counts as skip-heavy once at least half of that day's active routine items went
# unlogged. Simple, stated threshold rather than a statistically derived one, matching Phase
# 2's "descriptive statistics, not a model" scope.
SKIP_HEAVY_THRESHOLD = 0.5


@dataclass
class CompletionRate:
    routine_item_id: int
    name: str
    logged_days: int
    total_days: int
    rate: float


@dataclass
class Streak:
    routine_item_id: int
    name: str
    current_streak: int
    longest_streak: int


@dataclass
class SkipHeavyDay:
    date: str
    completed_count: int
    total_items: int
    skip_ratio: float


@dataclass
class TopicRoutineCorrelation:
    days_with_topic_log: int
    days_without_topic_log: int
    avg_routine_completions_on_topic_log_days: float
    avg_routine_completions_on_other_days: float


@dataclass
class Patterns:
    completion_rates: list[CompletionRate]
    streaks: list[Streak]
    skip_heavy_days: list[SkipHeavyDay]
    topic_routine_correlation: TopicRoutineCorrelation


def _parse_log_date(value: str) -> date:
    return date.fromisoformat(value)


def _parse_created_date(value: str) -> date:
    # RoutineItem.createdAt arrives as an ISO-8601 datetime (the entity's OffsetDateTime);
    # only the calendar date matters for windowing completion rates and skip-heavy days.
    return datetime.fromisoformat(value).date()


def _daterange(start: date, end: date):
    for offset in range((end - start).days + 1):
        yield start + timedelta(days=offset)


def _current_streak(logged_dates: set[date], today: date) -> int:
    if today in logged_dates:
        anchor = today
    elif (today - timedelta(days=1)) in logged_dates:
        # One day's grace: not yet logging today doesn't break a streak until tomorrow.
        anchor = today - timedelta(days=1)
    else:
        return 0

    streak = 0
    day = anchor
    while day in logged_dates:
        streak += 1
        day -= timedelta(days=1)
    return streak


def _longest_streak(logged_dates: set[date]) -> int:
    longest = 0
    current = 0
    previous = None
    for day in sorted(logged_dates):
        current = current + 1 if previous == day - timedelta(days=1) else 1
        longest = max(longest, current)
        previous = day
    return longest


def _avg(values: list[int]) -> float:
    return sum(values) / len(values) if values else 0.0


def compute_patterns(routine_items: list[dict], daily_logs: list[dict], today: date) -> Patterns:
    logged_dates_by_item: dict[int, set[date]] = defaultdict(set)
    routine_completions_by_date: dict[date, set[int]] = defaultdict(set)
    topic_log_dates: set[date] = set()

    for entry in daily_logs:
        log_date = _parse_log_date(entry["logDate"])
        routine_item_id = entry.get("routineItemId")
        learning_topic_id = entry.get("learningTopicId")
        if routine_item_id is not None:
            logged_dates_by_item[routine_item_id].add(log_date)
            routine_completions_by_date[log_date].add(routine_item_id)
        if learning_topic_id is not None:
            topic_log_dates.add(log_date)

    completion_rates = []
    streaks = []
    for item in routine_items:
        item_id = item["id"]
        created_date = _parse_created_date(item["createdAt"])
        # A log dated before the item existed can't be a real completion (nothing stops a
        # backdated logDate at the API level) so it's excluded here, the same as it already is
        # from the completion-rate window below.
        logged_dates = {d for d in logged_dates_by_item.get(item_id, set()) if d >= created_date}
        total_days = max((today - created_date).days + 1, 1)
        logged_days = len({d for d in logged_dates if d <= today})

        completion_rates.append(CompletionRate(
            routine_item_id=item_id,
            name=item["name"],
            logged_days=logged_days,
            total_days=total_days,
            rate=logged_days / total_days,
        ))
        streaks.append(Streak(
            routine_item_id=item_id,
            name=item["name"],
            current_streak=_current_streak(logged_dates, today),
            longest_streak=_longest_streak(logged_dates),
        ))

    if not routine_items:
        return Patterns(completion_rates, streaks, [], TopicRoutineCorrelation(0, 0, 0.0, 0.0))

    created_dates = [_parse_created_date(item["createdAt"]) for item in routine_items]
    range_start = min(created_dates)

    skip_heavy_days = []
    topic_day_counts = []
    other_day_counts = []

    for day in _daterange(range_start, today):
        active_count = sum(1 for created in created_dates if created <= day)
        if active_count == 0:
            continue
        completed_count = len(routine_completions_by_date.get(day, set()))

        skip_ratio = 1 - (completed_count / active_count)
        if skip_ratio >= SKIP_HEAVY_THRESHOLD:
            skip_heavy_days.append(SkipHeavyDay(
                date=day.isoformat(),
                completed_count=completed_count,
                total_items=active_count,
                skip_ratio=skip_ratio,
            ))

        (topic_day_counts if day in topic_log_dates else other_day_counts).append(completed_count)

    correlation = TopicRoutineCorrelation(
        days_with_topic_log=len(topic_day_counts),
        days_without_topic_log=len(other_day_counts),
        avg_routine_completions_on_topic_log_days=_avg(topic_day_counts),
        avg_routine_completions_on_other_days=_avg(other_day_counts),
    )

    return Patterns(completion_rates, streaks, skip_heavy_days, correlation)
