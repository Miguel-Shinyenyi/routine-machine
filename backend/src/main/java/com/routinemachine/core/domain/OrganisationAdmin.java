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

// Phase 12 groundwork: who administers an Organisation (and therefore every Cohort under
// it). Separate from CohortMembership on purpose — see OrganisationService's decisions log.
@Entity
@Table(name = "organisation_admins")
public class OrganisationAdmin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    protected OrganisationAdmin() {
    }

    public OrganisationAdmin(Long organisationId, Long userId) {
        this.organisationId = Objects.requireNonNull(organisationId, "organisationId must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
    }

    public Long getId() {
        return id;
    }

    public Long getOrganisationId() {
        return organisationId;
    }

    public Long getUserId() {
        return userId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
