from datetime import date

from patterns import compute_patterns


def item(id_, name, created_date):
    return {"id": id_, "name": name, "description": None, "createdAt": f"{created_date}T08:00:00Z"}


def log(routine_item_id=None, learning_topic_id=None, log_date="2026-09-01"):
    return {
        "id": 1,
        "logDate": log_date,
        "routineItemId": routine_item_id,
        "learningTopicId": learning_topic_id,
        "source": "manual",
    }


def test_completion_rate_counts_distinct_logged_days_over_the_items_lifetime():
    items = [item(1, "journaling", "2026-09-01")]
    logs = [
        log(routine_item_id=1, log_date="2026-09-01"),
        log(routine_item_id=1, log_date="2026-09-02"),
        # duplicate log on the same day must not inflate the count
        log(routine_item_id=1, log_date="2026-09-02"),
    ]

    result = compute_patterns(items, logs, today=date(2026, 9, 4))

    rate = result.completion_rates[0]
    assert rate.logged_days == 2
    assert rate.total_days == 4  # Sep 1, 2, 3, 4 inclusive
    assert rate.rate == 0.5


def test_current_streak_has_a_one_day_grace_period_but_longest_streak_does_not():
    items = [item(1, "exercise", "2026-09-01")]
    logs = [
        log(routine_item_id=1, log_date="2026-09-01"),
        log(routine_item_id=1, log_date="2026-09-02"),
        log(routine_item_id=1, log_date="2026-09-03"),
        # gap on Sep 4
        log(routine_item_id=1, log_date="2026-09-05"),
    ]

    # "Today" is Sep 6: Sep 5 was logged (yesterday), so the streak is still alive at 1 day.
    result = compute_patterns(items, logs, today=date(2026, 9, 6))

    streak = result.streaks[0]
    assert streak.current_streak == 1
    assert streak.longest_streak == 3


def test_a_log_backdated_before_the_item_existed_does_not_count_toward_its_streak():
    items = [item(1, "journaling", "2026-09-18")]
    logs = [
        log(routine_item_id=1, log_date="2026-09-17"),  # the day before the item was created
        log(routine_item_id=1, log_date="2026-09-18"),
    ]

    result = compute_patterns(items, logs, today=date(2026, 9, 18))

    # Only Sep 18 is a real completion; the backdated Sep 17 entry can't extend the streak.
    assert result.streaks[0].current_streak == 1
    assert result.streaks[0].longest_streak == 1


def test_current_streak_is_zero_once_the_grace_period_has_also_passed():
    items = [item(1, "exercise", "2026-09-01")]
    logs = [log(routine_item_id=1, log_date="2026-09-01")]

    result = compute_patterns(items, logs, today=date(2026, 9, 5))

    assert result.streaks[0].current_streak == 0
    assert result.streaks[0].longest_streak == 1


def test_flags_a_day_where_at_least_half_of_active_items_were_skipped():
    items = [item(1, "journaling", "2026-09-01"), item(2, "exercise", "2026-09-01")]
    logs = [log(routine_item_id=1, log_date="2026-09-01")]  # item 2 skipped

    result = compute_patterns(items, logs, today=date(2026, 9, 1))

    assert len(result.skip_heavy_days) == 1
    day = result.skip_heavy_days[0]
    assert day.date == "2026-09-01"
    assert day.completed_count == 1
    assert day.total_items == 2
    assert day.skip_ratio == 0.5


def test_does_not_flag_a_day_where_most_active_items_were_completed():
    items = [item(1, "journaling", "2026-09-01"), item(2, "exercise", "2026-09-01")]
    logs = [
        log(routine_item_id=1, log_date="2026-09-01"),
        log(routine_item_id=2, log_date="2026-09-01"),
    ]

    result = compute_patterns(items, logs, today=date(2026, 9, 1))

    assert result.skip_heavy_days == []


def test_a_day_before_any_routine_item_existed_is_excluded_from_skip_heavy_days():
    items = [item(1, "journaling", "2026-09-05")]

    result = compute_patterns(items, [], today=date(2026, 9, 5))

    # Sep 5 is the only day the item has existed; it should be flagged (0 of 1 completed),
    # but nothing before Sep 5 should appear even though the date range starts there anyway.
    assert len(result.skip_heavy_days) == 1
    assert result.skip_heavy_days[0].date == "2026-09-05"


def test_topic_routine_correlation_compares_average_completions_on_topic_log_days():
    items = [item(1, "journaling", "2026-09-01")]
    logs = [
        log(routine_item_id=1, log_date="2026-09-01"),
        log(learning_topic_id=10, log_date="2026-09-01"),
        # Sep 2: no topic log, no routine completion either
    ]

    result = compute_patterns(items, logs, today=date(2026, 9, 2))

    correlation = result.topic_routine_correlation
    assert correlation.days_with_topic_log == 1
    assert correlation.days_without_topic_log == 1
    assert correlation.avg_routine_completions_on_topic_log_days == 1.0
    assert correlation.avg_routine_completions_on_other_days == 0.0


def test_returns_empty_results_when_there_are_no_routine_items_yet():
    result = compute_patterns([], [log(learning_topic_id=10, log_date="2026-09-01")], today=date(2026, 9, 1))

    assert result.completion_rates == []
    assert result.streaks == []
    assert result.skip_heavy_days == []
    assert result.topic_routine_correlation.days_with_topic_log == 0
    assert result.topic_routine_correlation.avg_routine_completions_on_topic_log_days == 0.0
