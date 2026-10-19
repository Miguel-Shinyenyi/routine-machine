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

// Phase 11: one day's small saving, tapped — see docs/product-direction.md's granary
// section. Routine Machine never holds or moves this money; this row only records that the
// user set something aside, same "evidence of activity, not evidence of money moving"
// principle the rest of the app already follows.
@Entity
@Table(name = "savings")
public class Saving {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private int amount;

    @Column(name = "local_date", nullable = false)
    private LocalDate localDate;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    protected Saving() {
    }

    public Saving(User user, int amount, LocalDate localDate) {
        this.user = user;
        this.amount = amount;
        this.localDate = localDate;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public int getAmount() {
        return amount;
    }

    public LocalDate getLocalDate() {
        return localDate;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
