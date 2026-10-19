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

// Phase 12: a learner's seat in a Cohort, with an optional study group label (part 4's "by
// study group" breakdown — see docs/backend.md's decisions log).
@Entity
@Table(name = "cohort_memberships")
public class CohortMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cohort_id", nullable = false)
    private Long cohortId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "study_group")
    private String studyGroup;

    @Generated(event = EventType.INSERT)
    @Column(name = "joined_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime joinedAt;

    protected CohortMembership() {
    }

    public CohortMembership(Long cohortId, Long userId) {
        this(cohortId, userId, null);
    }

    public CohortMembership(Long cohortId, Long userId, String studyGroup) {
        this.cohortId = Objects.requireNonNull(cohortId, "cohortId must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.studyGroup = studyGroup;
    }

    public Long getId() {
        return id;
    }

    public Long getCohortId() {
        return cohortId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getStudyGroup() {
        return studyGroup;
    }

    public OffsetDateTime getJoinedAt() {
        return joinedAt;
    }
}
