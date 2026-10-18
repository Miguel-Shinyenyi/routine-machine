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

    // Defaults false: the hillside should never enroll a user who hasn't explicitly agreed to
    // be compared against others, even if they already have non-private routine items.
    @Column(name = "hillside_opt_in", nullable = false)
    private boolean hillsideOptIn = false;

    // Phase 9 groundwork (see V18__user_time_zone.sql): an IANA zone id ("Africa/Nairobi"),
    // defaulting to UTC. Decides which calendar day a credit lands on — see
    // EvidenceCreditService. Phase 10's Profile page is where a user actually sets this.
    @Column(name = "time_zone", nullable = false)
    private String timeZone = "UTC";

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

    public void optIntoHillside() {
        this.hillsideOptIn = true;
    }

    public void optOutOfHillside() {
        this.hillsideOptIn = false;
    }

    public boolean isHillsideOptIn() {
        return hillsideOptIn;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void changeTimeZone(String timeZone) {
        this.timeZone = Objects.requireNonNull(timeZone, "timeZone must not be null");
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
