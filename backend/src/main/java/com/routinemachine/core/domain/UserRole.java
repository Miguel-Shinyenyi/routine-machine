package com.routinemachine.core.domain;

// USER is every ordinary account. OWNER is held by exactly one account, the one whose email is in
// the OWNER_EMAIL environment variable; only it may open operations data and the API docs.
public enum UserRole {
    USER,
    OWNER
}
