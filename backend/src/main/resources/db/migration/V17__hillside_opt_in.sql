-- Phase 8: the hillside currently treats every user with a non-private daily log as a
-- participant, with no actual consent step. This closes that gap with a real opt-in flag,
-- defaulting to false so no existing user is silently enrolled by this migration.
ALTER TABLE users ADD COLUMN hillside_opt_in BOOLEAN NOT NULL DEFAULT false;
