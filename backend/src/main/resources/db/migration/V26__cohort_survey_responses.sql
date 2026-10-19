-- Phase 12 part 3 (docs/product-direction.md's cohort dashboard pilot measures): the
-- optional "supportive, neutral or pressuring" check. One answer per learner per cohort;
-- resubmitting replaces the previous answer rather than adding a second row.
CREATE TABLE cohort_survey_responses (
    id BIGSERIAL PRIMARY KEY,
    cohort_id BIGINT NOT NULL REFERENCES cohorts(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    answer VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (cohort_id, user_id)
);
