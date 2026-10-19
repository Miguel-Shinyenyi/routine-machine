-- Phase 12 part 4 (docs/product-direction.md's cohort dashboard, "by study group" breakdown
-- and "small groups stay hidden"): an optional label a member is invited or added with.
-- Nullable — a member with no group just doesn't appear in the by-group breakdown.
ALTER TABLE cohort_memberships ADD COLUMN study_group VARCHAR(255);
ALTER TABLE cohort_invites ADD COLUMN study_group VARCHAR(255);
