CREATE TABLE goals (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE routine_items (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE learning_topics (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    goal_id BIGINT NOT NULL REFERENCES goals(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE daily_logs (
    id BIGSERIAL PRIMARY KEY,
    log_date DATE NOT NULL,
    routine_item_id BIGINT REFERENCES routine_items(id),
    learning_topic_id BIGINT REFERENCES learning_topics(id),
    source VARCHAR(20) NOT NULL DEFAULT 'manual',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT daily_logs_source_valid CHECK (source IN ('manual', 'hub-sync')),
    CONSTRAINT daily_logs_exactly_one_target CHECK (
        (routine_item_id IS NOT NULL)::int + (learning_topic_id IS NOT NULL)::int = 1
    )
);

CREATE INDEX idx_daily_logs_log_date ON daily_logs(log_date);
CREATE INDEX idx_learning_topics_goal_id ON learning_topics(goal_id);
