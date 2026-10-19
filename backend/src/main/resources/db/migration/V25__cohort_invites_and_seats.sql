-- Phase 12 part 2 (docs/product-direction.md's "Bulk seats and invites by CSV"), flagged to
-- the user and confirmed before building, same as Phase 11's M-Pesa handling: there's no
-- outbound email infrastructure in this app, so "invite" means reserving a seat, not sending
-- mail — see OrganisationService.
ALTER TABLE organisations ADD COLUMN seat_limit INTEGER;

-- A reserved seat for an email that doesn't have an account yet. Fulfilled (turned into a
-- real cohort_memberships row, and deleted) the moment someone registers with that email —
-- see OrganisationService.fulfillInvitesForNewUser, called from AuthController.
CREATE TABLE cohort_invites (
    id BIGSERIAL PRIMARY KEY,
    cohort_id BIGINT NOT NULL REFERENCES cohorts(id),
    email VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (cohort_id, email)
);
CREATE INDEX idx_cohort_invites_email ON cohort_invites(email);
