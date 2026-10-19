-- Phase 9: "Question a credit" (docs/product-direction.md) — a simple record of the request,
-- not a resolution workflow. NULL means never questioned.
ALTER TABLE evidence_events ADD COLUMN questioned_at TIMESTAMPTZ;
