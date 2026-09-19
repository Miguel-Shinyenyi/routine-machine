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

@Entity
@Table(name = "current_reading_log")
public class CurrentReadingLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private ReadingSource source;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    protected CurrentReadingLog() {
    }

    public CurrentReadingLog(String title, ReadingSource source) {
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.source = Objects.requireNonNull(source, "source must not be null");
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public ReadingSource getSource() {
        return source;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
