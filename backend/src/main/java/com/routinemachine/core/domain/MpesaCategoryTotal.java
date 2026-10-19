package com.routinemachine.core.domain;

import com.routinemachine.core.service.MpesaCategory;
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

// Phase 11: one category's spending total for one statement month, imported from an M-Pesa
// statement — see docs/product-direction.md's granary section. Only the total is kept, never
// the transactions it was computed from.
@Entity
@Table(name = "mpesa_category_totals")
public class MpesaCategoryTotal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "statement_month", nullable = false)
    private LocalDate statementMonth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MpesaCategory category;

    @Column(nullable = false)
    private int amount;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    protected MpesaCategoryTotal() {
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public LocalDate getStatementMonth() {
        return statementMonth;
    }

    public MpesaCategory getCategory() {
        return category;
    }

    public int getAmount() {
        return amount;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
