package com.routinemachine.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.OffsetDateTime;
import java.util.Objects;

// Phase 12 part 2: a reserved seat for an email with no account yet. Fulfilled (turned into a
// CohortMembership, then deleted) the moment that email registers — see
// OrganisationService.fulfillInvitesForNewUser.
@Entity
@Table(name = "cohort_invites")
public class CohortInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cohort_id", nullable = false)
    private Long cohortId;

    @Column(nullable = false)
    private String email;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    protected CohortInvite() {
    }

    public CohortInvite(Long cohortId, String email) {
        this.cohortId = Objects.requireNonNull(cohortId, "cohortId must not be null");
        this.email = Objects.requireNonNull(email, "email must not be null").trim().toLowerCase();
    }

    public Long getId() {
        return id;
    }

    public Long getCohortId() {
        return cohortId;
    }

    public String getEmail() {
        return email;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
