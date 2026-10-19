package com.routinemachine.core.repository;

import com.routinemachine.core.domain.Cohort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CohortRepository extends JpaRepository<Cohort, Long> {
    List<Cohort> findByOrganisationId(Long organisationId);
}
