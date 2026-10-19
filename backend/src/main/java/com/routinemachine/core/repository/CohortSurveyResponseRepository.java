package com.routinemachine.core.repository;

import com.routinemachine.core.domain.CohortSurveyResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CohortSurveyResponseRepository extends JpaRepository<CohortSurveyResponse, Long> {
    Optional<CohortSurveyResponse> findByCohortIdAndUserId(Long cohortId, Long userId);

    List<CohortSurveyResponse> findByCohortId(Long cohortId);
}
