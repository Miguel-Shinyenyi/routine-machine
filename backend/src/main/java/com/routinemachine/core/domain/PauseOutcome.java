package com.routinemachine.core.domain;

// Phase 11: "the pause" (docs/product-direction.md). Null on PauseDecision means still
// waiting — this enum is only ever set once the user has actually decided.
public enum PauseOutcome {
    DECLINED,
    PROCEEDED
}
