package com.routinemachine.core.service;

// What happened to one row of a bulk CSV invite — see OrganisationService.bulkInviteByCsv.
// Every row gets an outcome; nothing is silently skipped.
public enum CsvInviteOutcome {
    ADDED_IMMEDIATELY,
    INVITE_RESERVED,
    ALREADY_A_MEMBER,
    ALREADY_INVITED,
    SEAT_LIMIT_REACHED,
    INVALID_EMAIL
}
