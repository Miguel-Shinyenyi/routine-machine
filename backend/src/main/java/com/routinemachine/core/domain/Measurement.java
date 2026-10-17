package com.routinemachine.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;

// One row per (source, metric, day). A sync job upserts into this table, so re-running it
// updates the existing row's value instead of creating a second one for the same day.
@Entity
@Table(name = "measurements")
public class Measurement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MeasurementSource source;

    @Column(nullable = false)
    private String metric;

    @Column(name = "measurement_date", nullable = false)
    private LocalDate measurementDate;

    @Column(nullable = false)
    private BigDecimal value;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Measurement() {
    }

    public Measurement(MeasurementSource source, String metric, LocalDate measurementDate, BigDecimal value) {
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.metric = Objects.requireNonNull(metric, "metric must not be null");
        this.measurementDate = Objects.requireNonNull(measurementDate, "measurementDate must not be null");
        this.value = Objects.requireNonNull(value, "value must not be null");
        this.updatedAt = OffsetDateTime.now();
    }

    public void updateValue(BigDecimal value) {
        this.value = Objects.requireNonNull(value, "value must not be null");
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public MeasurementSource getSource() {
        return source;
    }

    public String getMetric() {
        return metric;
    }

    public LocalDate getMeasurementDate() {
        return measurementDate;
    }

    public BigDecimal getValue() {
        return value;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
