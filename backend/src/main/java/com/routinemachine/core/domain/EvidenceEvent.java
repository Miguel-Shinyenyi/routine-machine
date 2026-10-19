package com.routinemachine.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;

// Phase 9: one piece of ingested evidence, whatever the qualify step decided about it — see
// docs/product-direction.md's evidence-to-credit pipeline. Every event is kept, including
// rejected and pending ones, so the credit ledger can show why, not just the accepted rows.
@Entity
@Table(name = "evidence_events")
public class EvidenceEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_id", nullable = false)
    private Goal goal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DailyLogSource source;

    // The stable id the dedupe step keys on (pipeline step 4) — for a synced source, the
    // originating system's own event/day id; for a manual/self-reported entry, a client- or
    // server-generated id unique to that one tap.
    @Column(name = "source_event_id", nullable = false)
    private String sourceEventId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    // Computed once, at ingest time, from occurredAt and the user's time zone at that moment
    // — see EvidenceCreditService. Stored, not recomputed later, so a user's zone change
    // never silently rewrites history.
    @Column(name = "local_date", nullable = false)
    private LocalDate localDate;

    @Column
    private Integer minutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EvidenceStatus status;

    @Column(name = "scoring_rule_version", nullable = false)
    private int scoringRuleVersion;

    @Column(nullable = false)
    private String reason;

    // Phase 9: "Question a credit" — just a timestamp of the request, not a resolution
    // workflow (see docs/product-direction.md). Null means never questioned.
    @Column(name = "questioned_at")
    private Instant questionedAt;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    protected EvidenceEvent() {
    }

    public EvidenceEvent(
            User user, Goal goal, DailyLogSource source, String sourceEventId, Instant occurredAt,
            LocalDate localDate, Integer minutes, EvidenceStatus status, int scoringRuleVersion, String reason) {
        this.user = user;
        this.goal = goal;
        this.source = source;
        this.sourceEventId = sourceEventId;
        this.occurredAt = occurredAt;
        this.localDate = localDate;
        this.minutes = minutes;
        this.status = status;
        this.scoringRuleVersion = scoringRuleVersion;
        this.reason = reason;
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

    public DailyLogSource getSource() {
        return source;
    }

    public String getSourceEventId() {
        return sourceEventId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public LocalDate getLocalDate() {
        return localDate;
    }

    public Integer getMinutes() {
        return minutes;
    }

    public EvidenceStatus getStatus() {
        return status;
    }

    public int getScoringRuleVersion() {
        return scoringRuleVersion;
    }

    public String getReason() {
        return reason;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean earnsCredit() {
        return status == EvidenceStatus.ACCEPTED || status == EvidenceStatus.SELF_REPORTED;
    }

    public Instant getQuestionedAt() {
        return questionedAt;
    }

    public void question(Instant now) {
        this.questionedAt = now;
    }
}
