ALTER TABLE routine_items ADD COLUMN category VARCHAR(10);
ALTER TABLE routine_items ADD CONSTRAINT routine_items_category_valid
    CHECK (category IN ('BODY', 'MIND', 'WORK', 'HOME'));

ALTER TABLE routine_items ADD COLUMN count_rule TEXT;

ALTER TABLE routine_items ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE routine_items ADD CONSTRAINT routine_items_source_valid CHECK (source IN (
    'MANUAL', 'HUB', 'WAKATIME', 'ACTIVITYWATCH', 'HEALTH', 'READER_NOTES', 'GITHUB', 'STRAVA'
));

ALTER TABLE routine_items ADD COLUMN hillside_field VARCHAR(20);
ALTER TABLE routine_items ADD CONSTRAINT routine_items_hillside_field_valid CHECK (hillside_field IN (
    'READING', 'MOVEMENT', 'DEEP_WORK', 'LEARNING', 'SLEEP', 'WRITING'
));

ALTER TABLE routine_items ADD COLUMN private BOOLEAN NOT NULL DEFAULT true;

ALTER TABLE routine_items ADD COLUMN goal_id BIGINT REFERENCES goals(id);
CREATE INDEX idx_routine_items_goal_id ON routine_items(goal_id);

ALTER TABLE daily_logs ADD COLUMN measured_minutes INT;
