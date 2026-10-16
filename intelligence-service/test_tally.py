from datetime import date

from tally import compute_tally


def item(id_, name, created_date, goal_id=None):
    return {
        "id": id_, "name": name, "createdAt": f"{created_date}T08:00:00Z", "goalId": goal_id,
    }


def topic(id_, name, goal_id):
    return {"id": id_, "name": name, "goalId": goal_id, "goalName": f"goal-{goal_id}"}


def log(routine_item_id=None, learning_topic_id=None, log_date="2026-09-01", measured_minutes=None):
    return {
        "id": 1,
        "logDate": log_date,
        "routineItemId": routine_item_id,
        "learningTopicId": learning_topic_id,
        "source": "manual",
        "measuredMinutes": measured_minutes,
    }


def test_a_day_counts_as_shown_up_once_at_least_the_threshold_of_routines_were_tended():
    items = [item(1, "a", "2026-09-01"), item(2, "b", "2026-09-01"), item(3, "c", "2026-09-01")]
    logs = [
        log(routine_item_id=1, log_date="2026-09-01"),
        log(routine_item_id=2, log_date="2026-09-01"),
        log(routine_item_id=3, log_date="2026-09-01"),
        log(routine_item_id=1, log_date="2026-09-02"),
    ]

    result = compute_tally(items, [], logs, today=date(2026, 9, 2), window_days=2)

    by_date = {d.date: d.shown_up for d in result.days_shown_up}
    assert by_date["2026-09-01"] is True
    assert by_date["2026-09-02"] is False


def test_days_shown_up_in_window_counts_only_the_true_days():
    items = [item(1, "a", "2026-08-01"), item(2, "b", "2026-08-01"), item(3, "c", "2026-08-01")]
    logs = [
        log(routine_item_id=1, log_date="2026-09-01"),
        log(routine_item_id=2, log_date="2026-09-01"),
        log(routine_item_id=3, log_date="2026-09-01"),
    ]

    result = compute_tally(items, [], logs, today=date(2026, 9, 2), window_days=2)

    assert result.days_shown_up_count == 1


def test_goal_week_hours_rolls_up_measured_minutes_from_routine_items_and_learning_topics():
    items = [item(1, "deep work", "2026-08-01", goal_id=5)]
    topics = [topic(10, "settlement-engine study", goal_id=5)]
    logs = [
        # Monday 2026-08-31
        log(routine_item_id=1, log_date="2026-08-31", measured_minutes=60),
        log(learning_topic_id=10, log_date="2026-09-01", measured_minutes=30),
    ]

    result = compute_tally(items, topics, logs, today=date(2026, 9, 7), window_days=28)

    weeks = {(w.goal_id, w.week_start): w.cumulative_hours for w in result.goal_week_hours}
    assert weeks[(5, "2026-08-31")] == 1.5


def test_goal_week_hours_is_cumulative_across_weeks():
    items = [item(1, "deep work", "2026-08-01", goal_id=5)]
    logs = [
        log(routine_item_id=1, log_date="2026-08-31", measured_minutes=60),  # week of 2026-08-31
        log(routine_item_id=1, log_date="2026-09-07", measured_minutes=60),  # next week
    ]

    result = compute_tally(items, [], logs, today=date(2026, 9, 7), window_days=28)

    weeks = sorted(
        (w.week_start, w.cumulative_hours) for w in result.goal_week_hours if w.goal_id == 5
    )
    assert weeks == [("2026-08-31", 1.0), ("2026-09-07", 2.0)]


def test_logs_with_no_goal_mapping_are_ignored_for_goal_hours():
    items = [item(1, "exercise", "2026-08-01", goal_id=None)]
    logs = [log(routine_item_id=1, log_date="2026-09-01", measured_minutes=60)]

    result = compute_tally(items, [], logs, today=date(2026, 9, 1), window_days=28)

    assert result.goal_week_hours == []


def test_routine_shown_up_counts_distinct_logged_days_within_the_window():
    items = [item(1, "exercise", "2026-08-01")]
    logs = [
        log(routine_item_id=1, log_date="2026-09-01"),
        log(routine_item_id=1, log_date="2026-09-01"),  # duplicate same day
        log(routine_item_id=1, log_date="2026-09-02"),
    ]

    result = compute_tally(items, [], logs, today=date(2026, 9, 2), window_days=28)

    routine = result.routine_shown_up[0]
    assert routine.routine_item_id == 1
    assert routine.shown_up_days == 2
    assert routine.window_days == 28


def test_routine_shown_up_window_is_clipped_to_the_items_lifetime():
    items = [item(1, "exercise", "2026-09-01")]

    result = compute_tally(items, [], [], today=date(2026, 9, 2), window_days=28)

    # the item has only existed for 2 days, so its window can't be the full 28
    assert result.routine_shown_up[0].window_days == 2
