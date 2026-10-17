ALTER TABLE routine_items DROP CONSTRAINT routine_items_source_valid;
ALTER TABLE routine_items ADD CONSTRAINT routine_items_source_valid CHECK (source IN (
    'MANUAL', 'HUB', 'WAKATIME', 'ACTIVITYWATCH', 'HEALTH', 'READER_NOTES', 'GITHUB', 'STRAVA', 'RESCUETIME'
));

ALTER TABLE daily_logs DROP CONSTRAINT daily_logs_source_valid;
ALTER TABLE daily_logs ADD CONSTRAINT daily_logs_source_valid
    CHECK (source IN ('manual', 'hub-sync', 'schedule', 'wakatime', 'activitywatch', 'github', 'rescuetime'));
