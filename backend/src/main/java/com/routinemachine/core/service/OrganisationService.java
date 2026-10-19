package com.routinemachine.core.service;

import com.routinemachine.core.domain.Cohort;
import com.routinemachine.core.domain.CohortInvite;
import com.routinemachine.core.domain.CohortMembership;
import com.routinemachine.core.domain.Organisation;
import com.routinemachine.core.domain.OrganisationAdmin;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.CohortInviteRepository;
import com.routinemachine.core.repository.CohortMembershipRepository;
import com.routinemachine.core.repository.CohortRepository;
import com.routinemachine.core.repository.OrganisationAdminRepository;
import com.routinemachine.core.repository.OrganisationRepository;
import com.routinemachine.core.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;

// Phase 12 (docs/product-direction.md's "Organisations and cohorts"): the data model, the
// admin/learner access rule, and bulk CSV invites. The cohort dashboard's pilot measures
// (activation, weekly participation, returns, the supportive/pressuring survey,
// small-groups-hidden) and the frontend are later parts, not built here — see
// docs/backend.md's decisions log and PROJECT.md's status log.
@Service
public class OrganisationService {

    // Not a full RFC 5322 validator — just enough to reject an obviously malformed CSV row
    // ("not-an-email") rather than silently reserving a seat for garbage input.
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final OrganisationRepository organisationRepository;
    private final OrganisationAdminRepository organisationAdminRepository;
    private final CohortRepository cohortRepository;
    private final CohortMembershipRepository cohortMembershipRepository;
    private final CohortInviteRepository cohortInviteRepository;
    private final UserRepository userRepository;

    public OrganisationService(
            OrganisationRepository organisationRepository,
            OrganisationAdminRepository organisationAdminRepository,
            CohortRepository cohortRepository,
            CohortMembershipRepository cohortMembershipRepository,
            CohortInviteRepository cohortInviteRepository,
            UserRepository userRepository) {
        this.organisationRepository = organisationRepository;
        this.organisationAdminRepository = organisationAdminRepository;
        this.cohortRepository = cohortRepository;
        this.cohortMembershipRepository = cohortMembershipRepository;
        this.cohortInviteRepository = cohortInviteRepository;
        this.userRepository = userRepository;
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

    public void setSeatLimit(Long organisationId, Long requestingUserId, Integer seatLimit) {
        requireAdmin(organisationId, requestingUserId);
        Organisation organisation = organisationRepository.findById(organisationId)
                .orElseThrow(() -> new NoSuchElementException("No organisation with id " + organisationId));
        organisation.changeSeatLimit(seatLimit);
        organisationRepository.save(organisation);
    }

    // There's no outbound email infrastructure in this app — "invite" means reserving a seat,
    // not sending mail. An email with an existing account is added to the cohort immediately;
    // an unknown email gets a CohortInvite, fulfilled by fulfillInvitesForNewUser the moment
    // that email registers. Every row gets a reported outcome, nothing is silently dropped —
    // same "fail loudly, never silently wrong" instinct as the M-Pesa statement parser.
    public List<CsvInviteRowResult> bulkInviteByCsv(Long cohortId, Long requestingUserId, String csvContent) {
        Cohort cohort = findCohort(cohortId);
        requireAdmin(cohort.getOrganisationId(), requestingUserId);
        Organisation organisation = organisationRepository.findById(cohort.getOrganisationId())
                .orElseThrow(() -> new NoSuchElementException("No organisation with id " + cohort.getOrganisationId()));

        List<Long> cohortIdsInOrganisation = cohortRepository.findByOrganisationId(organisation.getId()).stream()
                .map(Cohort::getId)
                .toList();
        long usedSeats = cohortMembershipRepository.countByCohortIdIn(cohortIdsInOrganisation)
                + cohortInviteRepository.countByCohortIdIn(cohortIdsInOrganisation);

        List<CsvInviteRowResult> results = new ArrayList<>();
        for (String rawEmail : parseCsvEmails(csvContent)) {
            CsvInviteOutcome outcome = inviteOneEmail(cohort, organisation, rawEmail, usedSeats);
            if (outcome == CsvInviteOutcome.ADDED_IMMEDIATELY || outcome == CsvInviteOutcome.INVITE_RESERVED) {
                usedSeats++;
            }
            results.add(new CsvInviteRowResult(rawEmail, outcome));
        }
        return results;
    }

    private CsvInviteOutcome inviteOneEmail(Cohort cohort, Organisation organisation, String email, long usedSeats) {
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            return CsvInviteOutcome.INVALID_EMAIL;
        }

        var existingUser = userRepository.findByEmailIgnoreCase(email);
        if (existingUser.isPresent()) {
            if (cohortMembershipRepository.existsByCohortIdAndUserId(cohort.getId(), existingUser.get().getId())) {
                return CsvInviteOutcome.ALREADY_A_MEMBER;
            }
            if (seatLimitReached(organisation, usedSeats)) {
                return CsvInviteOutcome.SEAT_LIMIT_REACHED;
            }
            cohortMembershipRepository.save(new CohortMembership(cohort.getId(), existingUser.get().getId()));
            return CsvInviteOutcome.ADDED_IMMEDIATELY;
        }

        if (cohortInviteRepository.findByCohortIdAndEmailIgnoreCase(cohort.getId(), email).isPresent()) {
            return CsvInviteOutcome.ALREADY_INVITED;
        }
        if (seatLimitReached(organisation, usedSeats)) {
            return CsvInviteOutcome.SEAT_LIMIT_REACHED;
        }
        cohortInviteRepository.save(new CohortInvite(cohort.getId(), email));
        return CsvInviteOutcome.INVITE_RESERVED;
    }

    private boolean seatLimitReached(Organisation organisation, long usedSeats) {
        return organisation.getSeatLimit() != null && usedSeats >= organisation.getSeatLimit();
    }

    // Turns every pending CohortInvite for this user's email into a real CohortMembership.
    // Called from AuthController right after an account is created — register() and the
    // GitHub login path both go through it, so an invite is fulfilled regardless of which
    // login method the invited person actually used to sign up.
    public void fulfillInvitesForNewUser(User user) {
        List<CohortInvite> invites = cohortInviteRepository.findByEmailIgnoreCase(user.getEmail());
        for (CohortInvite invite : invites) {
            cohortMembershipRepository.save(new CohortMembership(invite.getCohortId(), user.getId()));
            cohortInviteRepository.delete(invite);
        }
    }

    private List<String> parseCsvEmails(String csvContent) {
        List<String> emails = new ArrayList<>();
        for (String line : csvContent.split("\\R")) {
            String field = line.split(",")[0].trim();
            if (field.isEmpty() || field.equalsIgnoreCase("email")) {
                continue;
            }
            emails.add(field);
        }
        return emails;
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
