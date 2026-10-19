package com.routinemachine.core.service;

// Thrown when a user who isn't an admin of an Organisation tries to do something only an
// admin can do (create a cohort, add a member, read the admin roster). See
// GlobalExceptionHandler for the 403 this maps to.
public class NotAnOrganisationAdminException extends RuntimeException {
    public NotAnOrganisationAdminException(String message) {
        super(message);
    }
}
