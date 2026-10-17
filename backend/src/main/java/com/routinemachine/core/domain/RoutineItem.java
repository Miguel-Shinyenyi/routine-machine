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

import java.time.OffsetDateTime;

@Entity
@Table(name = "routine_items")
public class RoutineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @Enumerated(EnumType.STRING)
    @Column
    private RoutineItemCategory category;

    @Column(name = "count_rule")
    private String countRule;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoutineItemSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "hillside_field")
    private HillsideField hillsideField;

    @Column(name = "private", nullable = false)
    private boolean privateItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_id")
    private Goal goal;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    protected RoutineItem() {
    }

    public RoutineItem(String name, String description) {
        this(name, description, null, null, RoutineItemSource.MANUAL, null, true, null);
    }

    public RoutineItem(
            String name, String description, RoutineItemCategory category, String countRule,
            RoutineItemSource source, HillsideField hillsideField, boolean privateItem, Goal goal) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.countRule = countRule;
        this.source = source == null ? RoutineItemSource.MANUAL : source;
        this.hillsideField = hillsideField;
        this.privateItem = privateItem;
        this.goal = goal;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public RoutineItemCategory getCategory() {
        return category;
    }

    public String getCountRule() {
        return countRule;
    }

    public RoutineItemSource getSource() {
        return source;
    }

    public HillsideField getHillsideField() {
        return hillsideField;
    }

    public boolean isPrivate() {
        return privateItem;
    }

    public Goal getGoal() {
        return goal;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
