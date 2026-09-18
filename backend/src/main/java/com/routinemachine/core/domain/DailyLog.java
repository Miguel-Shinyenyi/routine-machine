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
import java.util.Objects;

@Entity
@Table(name = "daily_logs")
public class DailyLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "routine_item_id")
    private RoutineItem routineItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "learning_topic_id")
    private LearningTopic learningTopic;

    @Column(nullable = false)
    private DailyLogSource source;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    protected DailyLog() {
    }

    private DailyLog(LocalDate logDate, RoutineItem routineItem, LearningTopic learningTopic, DailyLogSource source) {
        this.logDate = Objects.requireNonNull(logDate, "logDate must not be null");
        this.routineItem = routineItem;
        this.learningTopic = learningTopic;
        this.source = Objects.requireNonNull(source, "source must not be null");
    }

    public static DailyLog forRoutineItem(LocalDate logDate, RoutineItem routineItem) {
        return forRoutineItem(logDate, routineItem, DailyLogSource.MANUAL);
    }

    public static DailyLog forRoutineItem(LocalDate logDate, RoutineItem routineItem, DailyLogSource source) {
        Objects.requireNonNull(routineItem, "routineItem must not be null");
        return new DailyLog(logDate, routineItem, null, source);
    }

    public static DailyLog forLearningTopic(LocalDate logDate, LearningTopic learningTopic) {
        return forLearningTopic(logDate, learningTopic, DailyLogSource.MANUAL);
    }

    public static DailyLog forLearningTopic(LocalDate logDate, LearningTopic learningTopic, DailyLogSource source) {
        Objects.requireNonNull(learningTopic, "learningTopic must not be null");
        return new DailyLog(logDate, null, learningTopic, source);
    }

    public Long getId() {
        return id;
    }

    public LocalDate getLogDate() {
        return logDate;
    }

    public RoutineItem getRoutineItem() {
        return routineItem;
    }

    public LearningTopic getLearningTopic() {
        return learningTopic;
    }

    public DailyLogSource getSource() {
        return source;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
