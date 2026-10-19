package com.routinemachine.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.OffsetDateTime;
import java.util.List;
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

    // Phase 10 (docs/product-direction.md's Profile section): what this person loves, who
    // they return to for ideas, and how they like to learn. Never read by HillsideV2Service
    // or anything cohort-facing — see docs/backend.md's decisions log and the privacy test
    // that proves it.
    @Convert(converter = StringListConverter.class)
    @Column(name = "interests")
    private List<String> interests;

    @Convert(converter = StringListConverter.class)
    @Column(name = "thinkers")
    private List<String> thinkers;

    @Convert(converter = StringListConverter.class)
    @Column(name = "learning_preferences")
    private List<String> learningPreferences;

    @Column(name = "currency")
    private String currency;

    // An hour (0-23) before which an evidence event still counts for the previous calendar
    // day — see EvidenceCreditService. Defaults 0 (midnight): no shift, an ordinary calendar
    // day, until the user sets otherwise on their Profile page.
    @Column(name = "day_end_hour", nullable = false)
    private int dayEndHour = 0;

    // Defaults true: an analogy is an aid offered by default, not something a user has to
    // opt into — matches Today's staff card already showing a suggestion unprompted.
    @Column(name = "analogies_enabled", nullable = false)
    private boolean analogiesEnabled = true;

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

    public List<String> getInterests() {
        return interests;
    }

    public List<String> getThinkers() {
        return thinkers;
    }

    public List<String> getLearningPreferences() {
        return learningPreferences;
    }

    public String getCurrency() {
        return currency;
    }

    public int getDayEndHour() {
        return dayEndHour;
    }

    public boolean isAnalogiesEnabled() {
        return analogiesEnabled;
    }

    public void updateProfile(
            List<String> interests, List<String> thinkers, List<String> learningPreferences,
            String currency, Integer dayEndHour, String timeZone, Boolean analogiesEnabled) {
        if (interests != null) this.interests = interests;
        if (thinkers != null) this.thinkers = thinkers;
        if (learningPreferences != null) this.learningPreferences = learningPreferences;
        if (currency != null) this.currency = currency;
        if (dayEndHour != null) {
            if (dayEndHour < 0 || dayEndHour > 23) {
                throw new IllegalArgumentException("dayEndHour must be between 0 and 23");
            }
            this.dayEndHour = dayEndHour;
        }
        if (timeZone != null) this.timeZone = timeZone;
        if (analogiesEnabled != null) this.analogiesEnabled = analogiesEnabled;
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
