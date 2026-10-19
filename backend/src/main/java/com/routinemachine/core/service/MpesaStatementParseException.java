package com.routinemachine.core.service;

// Phase 11: thrown when no recognisable transaction row is found at all — the statement's
// format isn't what this parser expects (M-Pesa changed it, or the file isn't a statement),
// never silently reported as "KSh 0 everywhere." See docs/product-direction.md's M-Pesa
// section and docs/backend.md's decisions log for why this fails loudly instead.
public class MpesaStatementParseException extends RuntimeException {

    public MpesaStatementParseException(String message) {
        super(message);
    }
}
