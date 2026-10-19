-- Phase 12 groundwork (docs/product-direction.md's "Organisations and cohorts"): the data
-- model only. No CSV invites, no dashboard metrics yet — see OrganisationService.
CREATE TABLE organisations (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Who administers an organisation (and therefore every cohort under it). Separate from
-- cohort_memberships: an admin oversees potentially many cohorts, a learner belongs to one.
CREATE TABLE organisation_admins (
    id BIGSERIAL PRIMARY KEY,
    organisation_id BIGINT NOT NULL REFERENCES organisations(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organisation_id, user_id)
);
CREATE INDEX idx_organisation_admins_user ON organisation_admins(user_id);

CREATE TABLE cohorts (
    id BIGSERIAL PRIMARY KEY,
    organisation_id BIGINT NOT NULL REFERENCES organisations(id),
    name VARCHAR(255) NOT NULL,
    starts_on DATE NOT NULL,
    weeks INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_cohorts_organisation ON cohorts(organisation_id);

CREATE TABLE cohort_memberships (
    id BIGSERIAL PRIMARY KEY,
    cohort_id BIGINT NOT NULL REFERENCES cohorts(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (cohort_id, user_id)
);
CREATE INDEX idx_cohort_memberships_user ON cohort_memberships(user_id);
