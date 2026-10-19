-- Phase 9 groundwork for its own correctness requirement: a credit's local date depends on
-- the user's own time zone (see docs/product-direction.md's evidence-to-credit pipeline:
-- "The day is decided in the user's configured time zone"). The full Profile page (Phase 10)
-- owns the rest of the profile and its UI; this is the one column Phase 9 itself needs,
-- same narrow-groundwork pattern V14__users.sql used for accounts ahead of full auth.
ALTER TABLE users ADD COLUMN time_zone VARCHAR(64) NOT NULL DEFAULT 'UTC';
