-- Per-user scoping, part 1 (ownership data only; no query uses these columns yet).
-- goals, routine_items and daily_logs already have a nullable user_id (V15, V16). These four
-- personal tables did not, so a second user's data could not be told apart from the first's.
-- Nullable on purpose: existing rows are unowned until OwnerClaimService gives them to the
-- owner account (see docs/backend.md). learning_topics (via goals) and reading_notes (via
-- books) are scoped through their parent, so they need no column of their own.
ALTER TABLE schedule_templates ADD COLUMN user_id BIGINT REFERENCES users(id);
ALTER TABLE tasks ADD COLUMN user_id BIGINT REFERENCES users(id);
ALTER TABLE books ADD COLUMN user_id BIGINT REFERENCES users(id);
ALTER TABLE current_reading_log ADD COLUMN user_id BIGINT REFERENCES users(id);

CREATE INDEX idx_schedule_templates_user_id ON schedule_templates(user_id);
CREATE INDEX idx_tasks_user_id ON tasks(user_id);
CREATE INDEX idx_books_user_id ON books(user_id);
CREATE INDEX idx_current_reading_log_user_id ON current_reading_log(user_id);
