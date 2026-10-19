-- Phase 11: the granary (docs/product-direction.md). Never holds or moves money — these
-- tables record that a saving or a decision happened, nothing more.
ALTER TABLE users ADD COLUMN saving_target_amount INTEGER;

CREATE TABLE savings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    amount INTEGER NOT NULL,
    local_date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, local_date)
);

-- "The pause": a tempting decision logged, then checked back on at two fixed points. outcome
-- is null until the user actually decides (via the 24-hour follow-up or "I've already
-- decided"); reflection_30d is null until the 30-day look-back, independent of outcome.
CREATE TABLE pause_decisions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    what TEXT NOT NULL,
    amount VARCHAR(100),
    why TEXT,
    heard_from VARCHAR(50),
    outcome VARCHAR(20),
    decided_at TIMESTAMPTZ,
    follow_up_24h_at TIMESTAMPTZ NOT NULL,
    follow_up_30d_at TIMESTAMPTZ NOT NULL,
    reflection_30d TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pause_decisions_outcome_valid CHECK (outcome IN ('DECLINED', 'PROCEEDED'))
);

CREATE INDEX idx_savings_user_date ON savings(user_id, local_date);
CREATE INDEX idx_pause_decisions_user ON pause_decisions(user_id);
