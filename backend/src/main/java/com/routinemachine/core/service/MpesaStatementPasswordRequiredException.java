package com.routinemachine.core.service;

// Phase 11: thrown when a statement PDF is password-protected and either no password was
// given or it was wrong — distinct from MpesaStatementParseException so the frontend can ask
// for a password specifically, rather than showing a generic "couldn't read this" error. See
// docs/product-direction.md's M-Pesa section.
public class MpesaStatementPasswordRequiredException extends RuntimeException {

    public MpesaStatementPasswordRequiredException(String message) {
        super(message);
    }
}
