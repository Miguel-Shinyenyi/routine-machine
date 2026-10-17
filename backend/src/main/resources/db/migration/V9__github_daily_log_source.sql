ALTER TABLE daily_logs DROP CONSTRAINT daily_logs_source_valid;
ALTER TABLE daily_logs ADD CONSTRAINT daily_logs_source_valid
    CHECK (source IN ('manual', 'hub-sync', 'schedule', 'wakatime', 'activitywatch', 'github'));
