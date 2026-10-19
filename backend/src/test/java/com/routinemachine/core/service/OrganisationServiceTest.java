package com.routinemachine.core.service;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.Cohort;
import com.routinemachine.core.domain.CohortInvite;
import com.routinemachine.core.domain.CohortMembership;
import com.routinemachine.core.domain.Organisation;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.CohortInviteRepository;
import com.routinemachine.core.repository.CohortMembershipRepository;
import com.routinemachine.core.repository.CohortRepository;
import com.routinemachine.core.repository.OrganisationAdminRepository;
import com.routinemachine.core.repository.OrganisationRepository;
import com.routinemachine.core.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class OrganisationServiceTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private OrganisationRepository organisationRepository;
    @Autowired
    private OrganisationAdminRepository organisationAdminRepository;
    @Autowired
    private CohortRepository cohortRepository;
    @Autowired
    private CohortMembershipRepository cohortMembershipRepository;
    @Autowired
    private CohortInviteRepository cohortInviteRepository;

    private OrganisationService service() {
        return new OrganisationService(
                organisationRepository, organisationAdminRepository, cohortRepository, cohortMembershipRepository,
                cohortInviteRepository, userRepository);
    }

    private User saveUser(String email) {
        return userRepository.save(new User(email, "irrelevant-hash", null, null));
    }

    @Test
    void creatingAnOrganisationMakesTheFoundingUserItsAdmin() {
        User founder = saveUser("founder@example.com");

        Organisation organisation = service().createOrganisation("Moringa School", founder.getId());

        assertThat(organisation.getId()).isNotNull();
        assertThat(organisationAdminRepository.existsByOrganisationIdAndUserId(organisation.getId(), founder.getId()))
                .isTrue();
    }

    @Test
    void anOrganisationAdminCanCreateACohortUnderIt() {
        User admin = saveUser("admin@example.com");
        Organisation organisation = service().createOrganisation("Moringa School", admin.getId());

        Cohort cohort = service().createCohort(organisation.getId(), admin.getId(), "Cohort 14", LocalDate.of(2026, 10, 19), 8);

        assertThat(cohort.getId()).isNotNull();
        assertThat(cohort.getOrganisationId()).isEqualTo(organisation.getId());
        assertThat(cohort.getName()).isEqualTo("Cohort 14");
        assertThat(cohort.getWeeks()).isEqualTo(8);
    }

    @Test
    void someoneWhoIsNotAnOrganisationAdminCannotCreateACohort() {
        User admin = saveUser("admin@example.com");
        User outsider = saveUser("outsider@example.com");
        Organisation organisation = service().createOrganisation("Moringa School", admin.getId());

        assertThatThrownBy(() ->
                service().createCohort(organisation.getId(), outsider.getId(), "Cohort 14", LocalDate.of(2026, 10, 19), 8))
                .isInstanceOf(NotAnOrganisationAdminException.class);
    }

    @Test
    void anOrganisationAdminCanAddALearnerToACohort() {
        User admin = saveUser("admin@example.com");
        User learner = saveUser("learner@example.com");
        Organisation organisation = service().createOrganisation("Moringa School", admin.getId());
        Cohort cohort = service().createCohort(organisation.getId(), admin.getId(), "Cohort 14", LocalDate.of(2026, 10, 19), 8);

        CohortMembership membership = service().addCohortMember(cohort.getId(), admin.getId(), learner.getId());

        assertThat(membership.getId()).isNotNull();
        assertThat(cohortMembershipRepository.existsByCohortIdAndUserId(cohort.getId(), learner.getId())).isTrue();
    }

    @Test
    void addingTheSameLearnerToACohortTwiceDoesNotDuplicateTheMembership() {
        User admin = saveUser("admin@example.com");
        User learner = saveUser("learner@example.com");
        Organisation organisation = service().createOrganisation("Moringa School", admin.getId());
        Cohort cohort = service().createCohort(organisation.getId(), admin.getId(), "Cohort 14", LocalDate.of(2026, 10, 19), 8);

        service().addCohortMember(cohort.getId(), admin.getId(), learner.getId());
        service().addCohortMember(cohort.getId(), admin.getId(), learner.getId());

        List<CohortMembership> memberships = cohortMembershipRepository.findByCohortId(cohort.getId());
        assertThat(memberships).hasSize(1);
    }

    @Test
    void someoneWhoIsNotAnOrganisationAdminCannotAddACohortMember() {
        User admin = saveUser("admin@example.com");
        User outsider = saveUser("outsider@example.com");
        User learner = saveUser("learner@example.com");
        Organisation organisation = service().createOrganisation("Moringa School", admin.getId());
        Cohort cohort = service().createCohort(organisation.getId(), admin.getId(), "Cohort 14", LocalDate.of(2026, 10, 19), 8);

        assertThatThrownBy(() -> service().addCohortMember(cohort.getId(), outsider.getId(), learner.getId()))
                .isInstanceOf(NotAnOrganisationAdminException.class);
    }

    @Test
    void anOrganisationAdminCanListCohortMembers() {
        User admin = saveUser("admin@example.com");
        User learner = saveUser("learner@example.com");
        Organisation organisation = service().createOrganisation("Moringa School", admin.getId());
        Cohort cohort = service().createCohort(organisation.getId(), admin.getId(), "Cohort 14", LocalDate.of(2026, 10, 19), 8);
        service().addCohortMember(cohort.getId(), admin.getId(), learner.getId());

        List<CohortMembership> members = service().listCohortMembers(cohort.getId(), admin.getId());

        assertThat(members).hasSize(1);
        assertThat(members.get(0).getUserId()).isEqualTo(learner.getId());
    }

    @Test
    void someoneWhoIsNotAnOrganisationAdminCannotListCohortMembers() {
        User admin = saveUser("admin@example.com");
        User outsider = saveUser("outsider@example.com");
        Organisation organisation = service().createOrganisation("Moringa School", admin.getId());
        Cohort cohort = service().createCohort(organisation.getId(), admin.getId(), "Cohort 14", LocalDate.of(2026, 10, 19), 8);

        assertThatThrownBy(() -> service().listCohortMembers(cohort.getId(), outsider.getId()))
                .isInstanceOf(NotAnOrganisationAdminException.class);
    }

    @Test
    void creatingACohortUnderANonexistentOrganisationFails() {
        User admin = saveUser("admin@example.com");

        assertThatThrownBy(() -> service().createCohort(999L, admin.getId(), "Cohort 14", LocalDate.of(2026, 10, 19), 8))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void anOrganisationAdminCanSetASeatLimit() {
        User admin = saveUser("admin@example.com");
        Organisation organisation = service().createOrganisation("Moringa School", admin.getId());

        service().setSeatLimit(organisation.getId(), admin.getId(), 100);

        assertThat(organisationRepository.findById(organisation.getId()).orElseThrow().getSeatLimit()).isEqualTo(100);
    }

    @Test
    void someoneWhoIsNotAnOrganisationAdminCannotSetASeatLimit() {
        User admin = saveUser("admin@example.com");
        User outsider = saveUser("outsider@example.com");
        Organisation organisation = service().createOrganisation("Moringa School", admin.getId());

        assertThatThrownBy(() -> service().setSeatLimit(organisation.getId(), outsider.getId(), 100))
                .isInstanceOf(NotAnOrganisationAdminException.class);
    }

    @Test
    void bulkInvitingAnExistingUserByCsvAddsThemImmediately() {
        User admin = saveUser("admin@example.com");
        User learner = saveUser("learner@example.com");
        Organisation organisation = service().createOrganisation("Moringa School", admin.getId());
        Cohort cohort = service().createCohort(organisation.getId(), admin.getId(), "Cohort 14", LocalDate.of(2026, 10, 19), 8);

        List<CsvInviteRowResult> results = service().bulkInviteByCsv(cohort.getId(), admin.getId(), "learner@example.com");

        assertThat(results).containsExactly(new CsvInviteRowResult("learner@example.com", CsvInviteOutcome.ADDED_IMMEDIATELY));
        assertThat(cohortMembershipRepository.existsByCohortIdAndUserId(cohort.getId(), learner.getId())).isTrue();
    }

    @Test
    void bulkInvitingAnUnknownEmailByCsvReservesASeat() {
        User admin = saveUser("admin@example.com");
        Organisation organisation = service().createOrganisation("Moringa School", admin.getId());
        Cohort cohort = service().createCohort(organisation.getId(), admin.getId(), "Cohort 14", LocalDate.of(2026, 10, 19), 8);

        List<CsvInviteRowResult> results = service().bulkInviteByCsv(cohort.getId(), admin.getId(), "nobody@example.com");

        assertThat(results).containsExactly(new CsvInviteRowResult("nobody@example.com", CsvInviteOutcome.INVITE_RESERVED));
        assertThat(cohortInviteRepository.findByCohortIdAndEmailIgnoreCase(cohort.getId(), "nobody@example.com")).isPresent();
    }

    @Test
    void bulkInvitingParsesMultipleLinesAndSkipsAHeaderAndBlankLines() {
        User admin = saveUser("admin@example.com");
        Organisation organisation = service().createOrganisation("Moringa School", admin.getId());
        Cohort cohort = service().createCohort(organisation.getId(), admin.getId(), "Cohort 14", LocalDate.of(2026, 10, 19), 8);
        String csv = "email\n\na@example.com\nb@example.com\n";

        List<CsvInviteRowResult> results = service().bulkInviteByCsv(cohort.getId(), admin.getId(), csv);

        assertThat(results).extracting(CsvInviteRowResult::email).containsExactly("a@example.com", "b@example.com");
    }

    @Test
    void bulkInvitingAnAlreadyInvitedEmailTwiceReportsAlreadyInvited() {
        User admin = saveUser("admin@example.com");
        Organisation organisation = service().createOrganisation("Moringa School", admin.getId());
        Cohort cohort = service().createCohort(organisation.getId(), admin.getId(), "Cohort 14", LocalDate.of(2026, 10, 19), 8);
        service().bulkInviteByCsv(cohort.getId(), admin.getId(), "nobody@example.com");

        List<CsvInviteRowResult> results = service().bulkInviteByCsv(cohort.getId(), admin.getId(), "nobody@example.com");

        assertThat(results).containsExactly(new CsvInviteRowResult("nobody@example.com", CsvInviteOutcome.ALREADY_INVITED));
    }

    @Test
    void bulkInvitingAnAlreadyAMemberEmailReportsAlreadyAMember() {
        User admin = saveUser("admin@example.com");
        User learner = saveUser("learner@example.com");
        Organisation organisation = service().createOrganisation("Moringa School", admin.getId());
        Cohort cohort = service().createCohort(organisation.getId(), admin.getId(), "Cohort 14", LocalDate.of(2026, 10, 19), 8);
        service().addCohortMember(cohort.getId(), admin.getId(), learner.getId());

        List<CsvInviteRowResult> results = service().bulkInviteByCsv(cohort.getId(), admin.getId(), "learner@example.com");

        assertThat(results).containsExactly(new CsvInviteRowResult("learner@example.com", CsvInviteOutcome.ALREADY_A_MEMBER));
    }

    @Test
    void bulkInvitingAnInvalidEmailReportsInvalidEmail() {
        User admin = saveUser("admin@example.com");
        Organisation organisation = service().createOrganisation("Moringa School", admin.getId());
        Cohort cohort = service().createCohort(organisation.getId(), admin.getId(), "Cohort 14", LocalDate.of(2026, 10, 19), 8);

        List<CsvInviteRowResult> results = service().bulkInviteByCsv(cohort.getId(), admin.getId(), "not-an-email");

        assertThat(results).containsExactly(new CsvInviteRowResult("not-an-email", CsvInviteOutcome.INVALID_EMAIL));
    }

    @Test
    void bulkInvitingStopsAddingOnceTheSeatLimitIsReached() {
        User admin = saveUser("admin@example.com");
        Organisation organisation = service().createOrganisation("Moringa School", admin.getId());
        service().setSeatLimit(organisation.getId(), admin.getId(), 1);
        Cohort cohort = service().createCohort(organisation.getId(), admin.getId(), "Cohort 14", LocalDate.of(2026, 10, 19), 8);
        String csv = "a@example.com\nb@example.com\n";

        List<CsvInviteRowResult> results = service().bulkInviteByCsv(cohort.getId(), admin.getId(), csv);

        assertThat(results).containsExactly(
                new CsvInviteRowResult("a@example.com", CsvInviteOutcome.INVITE_RESERVED),
                new CsvInviteRowResult("b@example.com", CsvInviteOutcome.SEAT_LIMIT_REACHED));
    }

    @Test
    void someoneWhoIsNotAnOrganisationAdminCannotBulkInvite() {
        User admin = saveUser("admin@example.com");
        User outsider = saveUser("outsider@example.com");
        Organisation organisation = service().createOrganisation("Moringa School", admin.getId());
        Cohort cohort = service().createCohort(organisation.getId(), admin.getId(), "Cohort 14", LocalDate.of(2026, 10, 19), 8);

        assertThatThrownBy(() -> service().bulkInviteByCsv(cohort.getId(), outsider.getId(), "nobody@example.com"))
                .isInstanceOf(NotAnOrganisationAdminException.class);
    }

    @Test
    void registeringWithAnInvitedEmailFulfillsTheInviteIntoARealMembership() {
        User admin = saveUser("admin@example.com");
        Organisation organisation = service().createOrganisation("Moringa School", admin.getId());
        Cohort cohort = service().createCohort(organisation.getId(), admin.getId(), "Cohort 14", LocalDate.of(2026, 10, 19), 8);
        service().bulkInviteByCsv(cohort.getId(), admin.getId(), "newcomer@example.com");
        User newcomer = saveUser("newcomer@example.com");

        service().fulfillInvitesForNewUser(newcomer);

        assertThat(cohortMembershipRepository.existsByCohortIdAndUserId(cohort.getId(), newcomer.getId())).isTrue();
        assertThat(cohortInviteRepository.findByCohortIdAndEmailIgnoreCase(cohort.getId(), "newcomer@example.com")).isEmpty();
    }

    @Test
    void fulfillingInvitesForAUserWithNoPendingInvitesDoesNothing() {
        User user = saveUser("nobody-invited@example.com");

        service().fulfillInvitesForNewUser(user);

        assertThat(cohortMembershipRepository.findByCohortId(999L)).isEmpty();
    }
}
