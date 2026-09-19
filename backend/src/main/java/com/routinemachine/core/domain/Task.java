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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_template_id")
    private ScheduleTemplate scheduleTemplate;

    @Column(name = "task_date", nullable = false)
    private LocalDate taskDate;

    @Column(nullable = false)
    private String label;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "routine_item_id")
    private RoutineItem routineItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "learning_topic_id")
    private LearningTopic learningTopic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "daily_log_id")
    private DailyLog dailyLog;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    protected Task() {
    }

    private Task(
            LocalDate taskDate, ScheduleTemplate scheduleTemplate, String label,
            RoutineItem routineItem, LearningTopic learningTopic) {
        this.taskDate = Objects.requireNonNull(taskDate, "taskDate must not be null");
        this.scheduleTemplate = scheduleTemplate;
        this.label = Objects.requireNonNull(label, "label must not be null");
        this.routineItem = routineItem;
        this.learningTopic = learningTopic;
        this.status = TaskStatus.TODO;
    }

    public static Task fromTemplate(LocalDate taskDate, ScheduleTemplate template, LearningTopic resolvedLearningTopic) {
        Objects.requireNonNull(template, "template must not be null");
        if (template.getTargetType() == ScheduleTargetType.ROUTINE_ITEM) {
            if (resolvedLearningTopic != null) {
                throw new IllegalArgumentException(
                        "A routine-item template's task can't be given a resolved learning topic");
            }
            return new Task(taskDate, template, template.getLabel(), template.getRoutineItem(), null);
        }
        if (resolvedLearningTopic == null) {
            throw new IllegalArgumentException(
                    "A learning-slot template's task needs a resolved learning topic for the day");
        }
        return new Task(taskDate, template, template.getLabel(), null, resolvedLearningTopic);
    }

    public static Task adHoc(LocalDate taskDate, String label, RoutineItem routineItem, LearningTopic learningTopic) {
        if (routineItem != null && learningTopic != null) {
            throw new IllegalArgumentException("An ad-hoc task can target at most one of routineItem or learningTopic");
        }
        return new Task(taskDate, null, label, routineItem, learningTopic);
    }

    public void updateStatus(TaskStatus status) {
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    public void attachDailyLog(DailyLog dailyLog) {
        this.dailyLog = Objects.requireNonNull(dailyLog, "dailyLog must not be null");
    }

    public void clearDailyLog() {
        this.dailyLog = null;
    }

    public Long getId() {
        return id;
    }

    public ScheduleTemplate getScheduleTemplate() {
        return scheduleTemplate;
    }

    public LocalDate getTaskDate() {
        return taskDate;
    }

    public String getLabel() {
        return label;
    }

    public RoutineItem getRoutineItem() {
        return routineItem;
    }

    public LearningTopic getLearningTopic() {
        return learningTopic;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public DailyLog getDailyLog() {
        return dailyLog;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
