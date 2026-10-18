-- Additive, nullable scoping only: NULL user_id means "legacy, unowned" data, exactly the
-- single-user behaviour every existing row and every background sync service already has.
-- Nothing is backfilled here and no column is required; this is seeding/testing groundwork
-- for Phase 8, not the real per-table migration (see docs/backend.md's open questions).
ALTER TABLE routine_items ADD COLUMN user_id BIGINT REFERENCES users(id);
ALTER TABLE daily_logs ADD COLUMN user_id BIGINT REFERENCES users(id);

CREATE INDEX idx_routine_items_user_id ON routine_items(user_id);
CREATE INDEX idx_daily_logs_user_id ON daily_logs(user_id);
