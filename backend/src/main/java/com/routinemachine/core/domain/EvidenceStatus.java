package com.routinemachine.core.domain;

// Phase 9: what the qualify step of the evidence-to-credit pipeline decided. ACCEPTED and
// SELF_REPORTED both earn a credit (self-reports count, but stay labelled as such — see
// docs/product-direction.md); REJECTED and PENDING never do.
public enum EvidenceStatus {
    ACCEPTED,
    REJECTED,
    PENDING,
    SELF_REPORTED
}
