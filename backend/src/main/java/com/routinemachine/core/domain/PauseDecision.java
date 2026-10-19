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
import java.time.OffsetDateTime;

// Phase 11: "the pause" — a tempting decision, written down, checked back on at two fixed
// points (docs/product-direction.md's granary section). outcome is null until the user
// actually decides, via the 24-hour follow-up or "I've already decided." reflectionThirtyDay
// is a separate, later look-back, independent of outcome — the Shepherd asks again in 30
// days no matter what was decided.
@Entity
@Table(name = "pause_decisions")
public class PauseDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String what;

    @Column
    private String amount;

    @Column
    private String why;

    @Column(name = "heard_from")
    private String heardFrom;

    @Enumerated(EnumType.STRING)
    @Column
    private PauseOutcome outcome;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "follow_up_24h_at", nullable = false)
    private Instant followUp24hAt;

    @Column(name = "follow_up_30d_at", nullable = false)
    private Instant followUp30dAt;

    @Column(name = "reflection_30d")
    private String reflectionThirtyDay;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    protected PauseDecision() {
    }

    public PauseDecision(User user, String what, String amount, String why, String heardFrom, Instant now) {
        this.user = user;
        this.what = what;
        this.amount = amount;
        this.why = why;
        this.heardFrom = heardFrom;
        this.followUp24hAt = now.plusSeconds(24 * 3600L);
        this.followUp30dAt = now.plusSeconds(30 * 24 * 3600L);
    }

    public void decide(PauseOutcome outcome, Instant now) {
        this.outcome = outcome;
        this.decidedAt = now;
    }

    public void reflect(String reflection) {
        this.reflectionThirtyDay = reflection;
    }

    public boolean isDueForTwentyFourHourFollowUp(Instant now) {
        return outcome == null && !now.isBefore(followUp24hAt);
    }

    public boolean isDueForThirtyDayFollowUp(Instant now) {
        return reflectionThirtyDay == null && !now.isBefore(followUp30dAt);
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getWhat() {
        return what;
    }

    public String getAmount() {
        return amount;
    }

    public String getWhy() {
        return why;
    }

    public String getHeardFrom() {
        return heardFrom;
    }

    public PauseOutcome getOutcome() {
        return outcome;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public Instant getFollowUp24hAt() {
        return followUp24hAt;
    }

    public Instant getFollowUp30dAt() {
        return followUp30dAt;
    }

    public String getReflectionThirtyDay() {
        return reflectionThirtyDay;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
