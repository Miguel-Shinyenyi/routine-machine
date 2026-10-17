-- Closes a gap flagged since Phase 8 started: goals previously had no user_id and no
-- creation API at all, unlike routine_items/daily_logs. NULL user_id still means
-- legacy/shared reference data (the three seeded goals), exactly as before.
ALTER TABLE goals ADD COLUMN user_id BIGINT REFERENCES users(id);
CREATE INDEX idx_goals_user_id ON goals(user_id);

ALTER TABLE goals DROP CONSTRAINT goals_name_key;
ALTER TABLE goals ADD CONSTRAINT goals_user_id_name_unique UNIQUE (user_id, name);
