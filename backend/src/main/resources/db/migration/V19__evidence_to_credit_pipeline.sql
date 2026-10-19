-- Phase 9: the hillside v2 evidence-to-credit pipeline, per docs/product-direction.md.
-- goal_id throughout is a FK to the existing goals table (a user's own named goal, e.g.
-- "cmu-masters-application"), not a new taxonomy — see docs/backend.md's decisions log.

-- A versioned ruleset: what makes a piece of evidence for a given goal qualify for credit.
-- Not every goal has a row; EvidenceCreditService falls back to a presence-based default
-- when none exists, so a brand-new goal is never silently un-creditable.
CREATE TABLE scoring_rules (
    id BIGSERIAL PRIMARY KEY,
    version INT NOT NULL,
    goal_id BIGINT NOT NULL REFERENCES goals(id),
    min_minutes INT,
    description TEXT NOT NULL,
    published_on DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (version, goal_id)
);

-- Every ingested piece of evidence, whatever its outcome. source_event_id is the stable id
-- the pipeline's own dedupe step keys on (step 4 of the pipeline); the UNIQUE constraint is
-- the actual enforcement, not just application logic, so a duplicate POST is safe even under
-- concurrent retries.
CREATE TABLE evidence_events (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    goal_id BIGINT NOT NULL REFERENCES goals(id),
    source VARCHAR(20) NOT NULL,
    source_event_id VARCHAR(255) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    local_date DATE NOT NULL,
    minutes INT,
    status VARCHAR(20) NOT NULL,
    scoring_rule_version INT NOT NULL,
    reason TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT evidence_events_status_valid CHECK (
        status IN ('ACCEPTED', 'REJECTED', 'PENDING', 'SELF_REPORTED')
    ),
    UNIQUE (user_id, source, source_event_id)
);

CREATE INDEX idx_evidence_events_user_goal_date ON evidence_events(user_id, goal_id, local_date);

-- One row per (user, goal, local day) that actually earned a credit. The UNIQUE constraint is
-- the whole mechanism behind "one credit per qualifying goal per day, extra evidence never
-- multiplies it" and "two concurrent inserts produce one row" — the pipeline upserts with
-- ON CONFLICT DO NOTHING rather than check-then-insert, so the database enforces it under
-- real concurrency, not application-level locking.
CREATE TABLE daily_credits (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    goal_id BIGINT NOT NULL REFERENCES goals(id),
    local_date DATE NOT NULL,
    credits SMALLINT NOT NULL DEFAULT 1,
    evidence_event_id BIGINT NOT NULL REFERENCES evidence_events(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, goal_id, local_date)
);

CREATE INDEX idx_daily_credits_user_date ON daily_credits(user_id, local_date);
