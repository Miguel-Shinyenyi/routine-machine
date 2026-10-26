-- Phase 10: profile data (docs/product-direction.md). interests/thinkers/learning_preferences
-- are JSON arrays in a TEXT column (StringListConverter), same pattern reading_notes already
-- uses for ideas_connected/quoted_phrases — nothing queries into them, they're read back whole.
-- day_end_hour feeds Phase 9's local-date rule (EvidenceCreditService): an hour before which a
-- credit still counts for the previous calendar day, so late study doesn't miss its day.
ALTER TABLE users ADD COLUMN interests TEXT;
ALTER TABLE users ADD COLUMN thinkers TEXT;
ALTER TABLE users ADD COLUMN learning_preferences TEXT;
ALTER TABLE users ADD COLUMN currency VARCHAR(8);
ALTER TABLE users ADD COLUMN day_end_hour INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN analogies_enabled BOOLEAN NOT NULL DEFAULT true;

ALTER TABLE users ADD CONSTRAINT users_day_end_hour_range CHECK (day_end_hour BETWEEN 0 AND 23);
