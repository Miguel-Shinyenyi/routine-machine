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

// Phase 8 groundwork: an account someone can register or log into (password, GitHub, or
// both), nothing more yet. No other table references this one — routine items, daily logs,
// goals, books, and reading notes are still single-user, exactly as before. See
// docs/backend.md's decisions log for why this is scoped narrowly on purpose.
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "github_id", unique = true)
    private String githubId;

    @Column(name = "display_name")
    private String displayName;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    protected User() {
    }

    public User(String email, String passwordHash, String githubId, String displayName) {
        this.email = Objects.requireNonNull(email, "email must not be null");
        if (passwordHash == null && githubId == null) {
            throw new IllegalArgumentException("a user needs at least one login method");
        }
        this.passwordHash = passwordHash;
        this.githubId = githubId;
        this.displayName = displayName;
    }

    public void linkGithubId(String githubId) {
        this.githubId = Objects.requireNonNull(githubId, "githubId must not be null");
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getGithubId() {
        return githubId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
