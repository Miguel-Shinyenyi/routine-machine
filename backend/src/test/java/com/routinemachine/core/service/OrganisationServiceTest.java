package com.routinemachine.core.service;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.Cohort;
import com.routinemachine.core.domain.CohortMembership;
import com.routinemachine.core.domain.Organisation;
import com.routinemachine.core.domain.User;
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

    private OrganisationService service() {
        return new OrganisationService(
                organisationRepository, organisationAdminRepository, cohortRepository, cohortMembershipRepository);
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
}
