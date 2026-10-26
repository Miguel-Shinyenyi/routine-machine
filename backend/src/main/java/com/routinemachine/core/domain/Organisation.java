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

// Phase 12 groundwork (docs/product-direction.md): a bootcamp or training programme buying
// seats in bulk. See OrganisationService for how admins and cohorts attach to one of these.
@Entity
@Table(name = "organisations")
public class Organisation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    // Null means unlimited. Set via OrganisationService.setSeatLimit, enforced by
    // bulkInviteByCsv — "buy seats in bulk" (docs/product-direction.md), not a hard cap most
    // organisations need from day one.
    @Column(name = "seat_limit")
    private Integer seatLimit;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    protected Organisation() {
    }

    public Organisation(String name) {
        this.name = Objects.requireNonNull(name, "name must not be null");
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Integer getSeatLimit() {
        return seatLimit;
    }

    public void changeSeatLimit(Integer seatLimit) {
        if (seatLimit != null && seatLimit <= 0) {
            throw new IllegalArgumentException("seatLimit must be positive");
        }
        this.seatLimit = seatLimit;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
