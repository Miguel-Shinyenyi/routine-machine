ALTER TABLE daily_logs DROP CONSTRAINT daily_logs_source_valid;
ALTER TABLE daily_logs ADD CONSTRAINT daily_logs_source_valid
    CHECK (source IN ('manual', 'hub-sync', 'schedule'));

CREATE TABLE schedule_templates (
    id BIGSERIAL PRIMARY KEY,
    day_of_week VARCHAR(10) NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    routine_item_id BIGINT REFERENCES routine_items(id),
    target_duration_minutes INT NOT NULL,
    sort_order INT NOT NULL,
    label VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT schedule_templates_day_valid CHECK (day_of_week IN (
        'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'
    )),
    CONSTRAINT schedule_templates_target_type_valid CHECK (target_type IN (
        'ROUTINE_ITEM', 'LEARNING_SLOT'
    )),
    CONSTRAINT schedule_templates_target_matches_type CHECK (
        (target_type = 'ROUTINE_ITEM' AND routine_item_id IS NOT NULL) OR
        (target_type = 'LEARNING_SLOT' AND routine_item_id IS NULL)
    )
);

CREATE TABLE tasks (
    id BIGSERIAL PRIMARY KEY,
    schedule_template_id BIGINT REFERENCES schedule_templates(id),
    task_date DATE NOT NULL,
    label VARCHAR(200) NOT NULL,
    routine_item_id BIGINT REFERENCES routine_items(id),
    learning_topic_id BIGINT REFERENCES learning_topics(id),
    status VARCHAR(20) NOT NULL DEFAULT 'TODO',
    daily_log_id BIGINT REFERENCES daily_logs(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT tasks_status_valid CHECK (status IN ('TODO', 'IN_PROGRESS', 'DONE')),
    CONSTRAINT tasks_at_most_one_target CHECK (
        (routine_item_id IS NOT NULL)::int + (learning_topic_id IS NOT NULL)::int <= 1
    )
);

CREATE TABLE current_reading_log (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(300) NOT NULL,
    source VARCHAR(20) NOT NULL DEFAULT 'manual',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT current_reading_log_source_valid CHECK (source IN ('manual', 'hub-sync'))
);

CREATE INDEX idx_tasks_task_date ON tasks(task_date);
CREATE INDEX idx_schedule_templates_day_of_week ON schedule_templates(day_of_week);
