-- Staged Prompt 7: the weekly answer to "Which failure mode did you meet this week?" (docs/understanding-first.md, the eight modes).
-- One row per person per week (the week starts on Monday); answering again in the same week replaces the answer.
CREATE TABLE weekly_failure_modes (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users(id),
    week_start   DATE         NOT NULL,
    failure_mode VARCHAR(20)  NOT NULL,
    answered_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_weekly_failure_mode_user_week UNIQUE (user_id, week_start)
);
CREATE INDEX idx_weekly_failure_modes_user ON weekly_failure_modes(user_id);
