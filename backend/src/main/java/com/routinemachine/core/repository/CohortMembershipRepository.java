package com.routinemachine.core.repository;

import com.routinemachine.core.domain.CohortMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CohortMembershipRepository extends JpaRepository<CohortMembership, Long> {
    List<CohortMembership> findByCohortId(Long cohortId);

    Optional<CohortMembership> findByCohortIdAndUserId(Long cohortId, Long userId);

    boolean existsByCohortIdAndUserId(Long cohortId, Long userId);
}
