package com.routinemachine.core.service;

// Thrown when someone who is neither an admin of the cohort's organisation nor a member of
// the cohort itself tries to view its dashboard or submit its survey — "admins see aggregates
// only, learners can read the same page" (docs/product-direction.md), nobody else.
public class NotACohortParticipantException extends RuntimeException {
    public NotACohortParticipantException(String message) {
        super(message);
    }
}
