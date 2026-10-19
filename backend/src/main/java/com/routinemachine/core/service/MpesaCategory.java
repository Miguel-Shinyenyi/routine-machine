package com.routinemachine.core.service;

// Phase 11: M-Pesa statement import (docs/product-direction.md). A fixed, named set matching
// the design reference's own category list (docs/design-reference/pages/granary.html). Not
// exhaustive of every possible M-Pesa transaction type — anything that matches no keyword
// below lands in OTHER rather than being silently dropped or guessed at.
public enum MpesaCategory {
    FOOD,
    TRANSPORT,
    FAMILY_SUPPORT,
    DATA_AND_AIRTIME,
    LEARNING,
    BETTING,
    OVERDRAFT_FEES,
    OTHER
}
