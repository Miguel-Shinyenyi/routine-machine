package com.routinemachine.core.service;

import com.routinemachine.core.domain.Cohort;
import com.routinemachine.core.domain.CohortMembership;
import com.routinemachine.core.domain.Organisation;
import com.routinemachine.core.domain.OrganisationAdmin;
import com.routinemachine.core.repository.CohortMembershipRepository;
import com.routinemachine.core.repository.CohortRepository;
import com.routinemachine.core.repository.OrganisationAdminRepository;
import com.routinemachine.core.repository.OrganisationRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

// Phase 12 groundwork (docs/product-direction.md's "Organisations and cohorts"): just the
// data model and admin/learner access rule. Bulk CSV invites and the cohort dashboard's
// pilot measures (activation, weekly participation, returns, the supportive/pressuring
// survey, small-groups-hidden) are later parts, not built here — see docs/backend.md's
// decisions log and PROJECT.md's status log.
@Service
public class OrganisationService {

    private final OrganisationRepository organisationRepository;
    private final OrganisationAdminRepository organisationAdminRepository;
    private final CohortRepository cohortRepository;
    private final CohortMembershipRepository cohortMembershipRepository;

    public OrganisationService(
            OrganisationRepository organisationRepository,
            OrganisationAdminRepository organisationAdminRepository,
            CohortRepository cohortRepository,
            CohortMembershipRepository cohortMembershipRepository) {
        this.organisationRepository = organisationRepository;
        this.organisationAdminRepository = organisationAdminRepository;
        this.cohortRepository = cohortRepository;
        this.cohortMembershipRepository = cohortMembershipRepository;
    }

    public Organisation createOrganisation(String name, Long foundingAdminUserId) {
        Organisation organisation = organisationRepository.save(new Organisation(name));
        organisationAdminRepository.save(new OrganisationAdmin(organisation.getId(), foundingAdminUserId));
        return organisation;
    }

    public Cohort createCohort(Long organisationId, Long requestingUserId, String name, LocalDate startsOn, int weeks) {
        if (!organisationRepository.existsById(organisationId)) {
            throw new NoSuchElementException("No organisation with id " + organisationId);
        }
        requireAdmin(organisationId, requestingUserId);
        return cohortRepository.save(new Cohort(organisationId, name, startsOn, weeks));
    }

    public CohortMembership addCohortMember(Long cohortId, Long requestingUserId, Long newMemberUserId) {
        Cohort cohort = findCohort(cohortId);
        requireAdmin(cohort.getOrganisationId(), requestingUserId);
        return cohortMembershipRepository.findByCohortIdAndUserId(cohortId, newMemberUserId)
                .orElseGet(() -> cohortMembershipRepository.save(new CohortMembership(cohortId, newMemberUserId)));
    }

    public List<CohortMembership> listCohortMembers(Long cohortId, Long requestingUserId) {
        Cohort cohort = findCohort(cohortId);
        requireAdmin(cohort.getOrganisationId(), requestingUserId);
        return cohortMembershipRepository.findByCohortId(cohortId);
    }

    private Cohort findCohort(Long cohortId) {
        return cohortRepository.findById(cohortId)
                .orElseThrow(() -> new NoSuchElementException("No cohort with id " + cohortId));
    }

    private void requireAdmin(Long organisationId, Long userId) {
        if (!organisationAdminRepository.existsByOrganisationIdAndUserId(organisationId, userId)) {
            throw new NotAnOrganisationAdminException(
                    "User " + userId + " is not an admin of organisation " + organisationId);
        }
    }
}
