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

// Phase 9: a versioned, published rule for what counts as a qualifying piece of evidence for
// one goal — see docs/product-direction.md's "Audit and correct" pipeline step. Not every
// goal has a row; EvidenceCreditService falls back to a presence-based default when none
// exists, so a newly created goal is never silently un-creditable.
@Entity
@Table(name = "scoring_rules")
public class ScoringRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_id", nullable = false)
    private Goal goal;

    // Null means presence-based: any accepted-source evidence for this goal qualifies,
    // whatever its duration (or lack of one).
    @Column(name = "min_minutes")
    private Integer minMinutes;

    @Column(nullable = false)
    private String description;

    @Column(name = "published_on", nullable = false)
    private LocalDate publishedOn;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    protected ScoringRule() {
    }

    public ScoringRule(int version, Goal goal, Integer minMinutes, String description, LocalDate publishedOn) {
        this.version = version;
        this.goal = goal;
        this.minMinutes = minMinutes;
        this.description = description;
        this.publishedOn = publishedOn;
    }

    public Long getId() {
        return id;
    }

    public int getVersion() {
        return version;
    }

    public Goal getGoal() {
        return goal;
    }

    public Integer getMinMinutes() {
        return minMinutes;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getPublishedOn() {
        return publishedOn;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
