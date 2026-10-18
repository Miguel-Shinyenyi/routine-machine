package com.routinemachine.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.LocalDate;
import java.time.OffsetDateTime;

// Phase 9: one earned credit for one (user, goal, local day) — see docs/backend.md's
// decisions log. Rows are never written through this entity's own repository.save(); see
// EvidenceCreditService, which upserts natively (ON CONFLICT DO NOTHING) so the database's own
// UNIQUE(user_id, goal_id, local_date) constraint is what actually prevents a double credit,
// not an application-level check-then-insert that a race could slip past.
@Entity
@Table(name = "daily_credits")
public class DailyCredit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_id", nullable = false)
    private Goal goal;

    @Column(name = "local_date", nullable = false)
    private LocalDate localDate;

    @Column(nullable = false)
    private short credits;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evidence_event_id", nullable = false)
    private EvidenceEvent evidenceEvent;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    protected DailyCredit() {
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Goal getGoal() {
        return goal;
    }

    public LocalDate getLocalDate() {
        return localDate;
    }

    public short getCredits() {
        return credits;
    }

    public EvidenceEvent getEvidenceEvent() {
        return evidenceEvent;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
