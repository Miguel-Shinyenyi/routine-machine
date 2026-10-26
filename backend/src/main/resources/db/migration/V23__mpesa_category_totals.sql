-- Phase 11: M-Pesa statement import (docs/product-direction.md). Only category totals are
-- kept — no transaction-level detail (receipt numbers, counterparties, timestamps), and the
-- uploaded PDF itself is never written to disk, let alone to this table. Re-importing the
-- same month replaces that month's totals (upsert), not a second set of rows.
CREATE TABLE mpesa_category_totals (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    statement_month DATE NOT NULL,
    category VARCHAR(50) NOT NULL,
    amount INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, statement_month, category)
);

CREATE INDEX idx_mpesa_category_totals_user_month ON mpesa_category_totals(user_id, statement_month);
