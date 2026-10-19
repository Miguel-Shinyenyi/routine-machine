package com.routinemachine.core.repository;

import com.routinemachine.core.domain.CohortInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CohortInviteRepository extends JpaRepository<CohortInvite, Long> {
    Optional<CohortInvite> findByCohortIdAndEmailIgnoreCase(Long cohortId, String email);

    List<CohortInvite> findByEmailIgnoreCase(String email);

    long countByCohortIdIn(List<Long> cohortIds);
}
