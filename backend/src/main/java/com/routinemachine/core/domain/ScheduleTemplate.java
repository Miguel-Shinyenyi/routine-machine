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

import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(name = "schedule_templates")
public class ScheduleTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false)
    private DayOfWeek dayOfWeek;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private ScheduleTargetType targetType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "routine_item_id")
    private RoutineItem routineItem;

    @Column(name = "target_duration_minutes", nullable = false)
    private int targetDurationMinutes;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private String label;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    protected ScheduleTemplate() {
    }

    private ScheduleTemplate(
            DayOfWeek dayOfWeek, ScheduleTargetType targetType, RoutineItem routineItem,
            int targetDurationMinutes, int sortOrder, String label) {
        this.dayOfWeek = Objects.requireNonNull(dayOfWeek, "dayOfWeek must not be null");
        this.targetType = targetType;
        this.routineItem = routineItem;
        this.targetDurationMinutes = targetDurationMinutes;
        this.sortOrder = sortOrder;
        this.label = Objects.requireNonNull(label, "label must not be null");
    }

    public static ScheduleTemplate forRoutineItem(
            DayOfWeek dayOfWeek, RoutineItem routineItem, int targetDurationMinutes, int sortOrder, String label) {
        Objects.requireNonNull(routineItem, "routineItem must not be null");
        return new ScheduleTemplate(
                dayOfWeek, ScheduleTargetType.ROUTINE_ITEM, routineItem, targetDurationMinutes, sortOrder, label);
    }

    public static ScheduleTemplate forLearningSlot(
            DayOfWeek dayOfWeek, int targetDurationMinutes, int sortOrder, String label) {
        return new ScheduleTemplate(
                dayOfWeek, ScheduleTargetType.LEARNING_SLOT, null, targetDurationMinutes, sortOrder, label);
    }

    public Long getId() {
        return id;
    }

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public ScheduleTargetType getTargetType() {
        return targetType;
    }

    public RoutineItem getRoutineItem() {
        return routineItem;
    }

    public int getTargetDurationMinutes() {
        return targetDurationMinutes;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public String getLabel() {
        return label;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
