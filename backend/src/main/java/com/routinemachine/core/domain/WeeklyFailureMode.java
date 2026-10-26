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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;

// One person's answer for one week. Owned by the person; nothing reads it except that person (and nothing here reaches the hillside
// or a cohort dashboard).
@Entity
@Table(name = "weekly_failure_modes")
public class WeeklyFailureMode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_mode", nullable = false)
    private FailureMode failureMode;

    @Column(name = "answered_at", nullable = false)
    private OffsetDateTime answeredAt = OffsetDateTime.now();

    protected WeeklyFailureMode() {
    }

    public WeeklyFailureMode(User user, LocalDate weekStart, FailureMode failureMode) {
        this.user = Objects.requireNonNull(user, "user must not be null");
        this.weekStart = Objects.requireNonNull(weekStart, "weekStart must not be null");
        this.failureMode = Objects.requireNonNull(failureMode, "failureMode must not be null");
    }

    public void changeTo(FailureMode failureMode) {
        this.failureMode = Objects.requireNonNull(failureMode, "failureMode must not be null");
        this.answeredAt = OffsetDateTime.now();
    }

    public User getUser() {
        return user;
    }

    public LocalDate getWeekStart() {
        return weekStart;
    }

    public FailureMode getFailureMode() {
        return failureMode;
    }
}
