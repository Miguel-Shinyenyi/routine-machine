CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255),
    github_id VARCHAR(255) UNIQUE,
    display_name VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT users_has_a_login_method CHECK (password_hash IS NOT NULL OR github_id IS NOT NULL)
);

-- Phase 8 groundwork only: this table exists and can be registered/logged into, but nothing
-- yet requires login, and no other table is scoped to a user. See docs/backend.md.
