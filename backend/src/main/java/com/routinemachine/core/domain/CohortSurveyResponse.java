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

import java.time.OffsetDateTime;
import java.util.Objects;

// Phase 12 part 3: one learner's answer to the pilot's "did this feel supportive, neutral,
// or pressuring?" question. Optional, one per learner per cohort — resubmitting replaces it.
@Entity
@Table(name = "cohort_survey_responses")
public class CohortSurveyResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cohort_id", nullable = false)
    private Long cohortId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CohortSurveyAnswer answer;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    protected CohortSurveyResponse() {
    }

    public CohortSurveyResponse(Long cohortId, Long userId, CohortSurveyAnswer answer) {
        this.cohortId = Objects.requireNonNull(cohortId, "cohortId must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.answer = Objects.requireNonNull(answer, "answer must not be null");
    }

    public void changeAnswer(CohortSurveyAnswer answer) {
        this.answer = Objects.requireNonNull(answer, "answer must not be null");
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

    public CohortSurveyAnswer getAnswer() {
        return answer;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
