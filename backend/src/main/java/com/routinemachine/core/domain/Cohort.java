package com.routinemachine.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;

// Phase 12 groundwork: one run of a training programme under an Organisation — "Cohort 14"
// in the design reference. Weekly pilot measures (docs/product-direction.md) are a later part.
@Entity
@Table(name = "cohorts")
public class Cohort {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(nullable = false)
    private String name;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(nullable = false)
    private int weeks;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    protected Cohort() {
    }

    public Cohort(Long organisationId, String name, LocalDate startsOn, int weeks) {
        this.organisationId = Objects.requireNonNull(organisationId, "organisationId must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.startsOn = Objects.requireNonNull(startsOn, "startsOn must not be null");
        if (weeks <= 0) {
            throw new IllegalArgumentException("weeks must be positive");
        }
        this.weeks = weeks;
    }

    public Long getId() {
        return id;
    }

    public Long getOrganisationId() {
        return organisationId;
    }

    public String getName() {
        return name;
    }

    public LocalDate getStartsOn() {
        return startsOn;
    }

    public int getWeeks() {
        return weeks;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
