package com.routinemachine.core.repository;

import com.routinemachine.core.domain.OrganisationAdmin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrganisationAdminRepository extends JpaRepository<OrganisationAdmin, Long> {
    boolean existsByOrganisationIdAndUserId(Long organisationId, Long userId);

    List<OrganisationAdmin> findByOrganisationId(Long organisationId);
}
