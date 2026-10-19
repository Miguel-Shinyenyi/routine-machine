package com.routinemachine.core.service;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.Cohort;
import com.routinemachine.core.domain.CohortSurveyAnswer;
import com.routinemachine.core.domain.DailyLog;
import com.routinemachine.core.domain.Goal;
import com.routinemachine.core.domain.Organisation;
import com.routinemachine.core.domain.OrganisationAdmin;
import com.routinemachine.core.domain.RoutineItem;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.CohortMembershipRepository;
import com.routinemachine.core.repository.CohortRepository;
import com.routinemachine.core.repository.CohortSurveyResponseRepository;
import com.routinemachine.core.repository.DailyLogRepository;
import com.routinemachine.core.repository.GoalRepository;
import com.routinemachine.core.repository.OrganisationAdminRepository;
import com.routinemachine.core.repository.OrganisationRepository;
import com.routinemachine.core.repository.RoutineItemRepository;
import com.routinemachine.core.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class CohortDashboardServiceTest extends AbstractIntegrationTest {

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
    private CohortSurveyResponseRepository cohortSurveyResponseRepository;
    @Autowired
    private GoalRepository goalRepository;
    @Autowired
    private RoutineItemRepository routineItemRepository;
    @Autowired
    private DailyLogRepository dailyLogRepository;

    private CohortDashboardService service() {
        return new CohortDashboardService(
                cohortRepository, cohortMembershipRepository, organisationAdminRepository, goalRepository,
                dailyLogRepository, cohortSurveyResponseRepository);
    }

    private OrganisationService organisationService() {
        return new OrganisationService(
                organisationRepository, organisationAdminRepository, cohortRepository, cohortMembershipRepository,
                null, userRepository);
    }

    private User saveUser(String email) {
        return userRepository.save(new User(email, "irrelevant-hash", null, null));
    }

    private void log(User user, LocalDate date) {
        RoutineItem item = routineItemRepository.save(new RoutineItem("Reading", "desc"));
        DailyLog entry = DailyLog.forRoutineItem(date, item);
        entry.assignUser(user);
        dailyLogRepository.save(entry);
    }

    private void setGoal(User user, LocalDate createdOnOrAfter) {
        goalRepository.save(new Goal("Learn Spring", "desc", user));
    }

    private Cohort setUp(LocalDate joinedDate) {
        User admin = saveUser("admin@example.com");
        Organisation organisation = organisationRepository.save(new Organisation("Moringa School"));
        organisationAdminRepository.save(new OrganisationAdmin(organisation.getId(), admin.getId()));
        return cohortRepository.save(new Cohort(organisation.getId(), "Cohort 14", joinedDate, 8));
    }

    @Test
    void aMemberWhoSetsAGoalAndChecksInWithinSevenDaysIsActivated() {
        LocalDate today = LocalDate.of(2026, 10, 19);
        Cohort cohort = setUp(today);
        User activated = saveUser("activated@example.com");
        User notActivated = saveUser("not-activated@example.com");
        organisationService().addCohortMember(cohort.getId(), adminIdOf(cohort), activated.getId());
        organisationService().addCohortMember(cohort.getId(), adminIdOf(cohort), notActivated.getId());
        setGoal(activated, today);
        log(activated, today.plusDays(2));
        setGoal(notActivated, today);
        // notActivated never logs

        CohortDashboardSummary summary = service().summarize(cohort.getId(), adminIdOf(cohort), today);

        assertThat(summary.activatedWithinSevenDays()).isEqualTo(new CohortMeasure(1, 2));
    }

    @Test
    void aGoalOrLogOutsideTheSevenDayWindowDoesNotCountTowardActivation() {
        LocalDate today = LocalDate.of(2026, 10, 19);
        Cohort cohort = setUp(today);
        User member = saveUser("member@example.com");
        organisationService().addCohortMember(cohort.getId(), adminIdOf(cohort), member.getId());
        setGoal(member, today);
        log(member, today.plusDays(10));

        CohortDashboardSummary summary = service().summarize(cohort.getId(), adminIdOf(cohort), today.plusDays(10));

        assertThat(summary.activatedWithinSevenDays()).isEqualTo(new CohortMeasure(0, 1));
    }

    @Test
    void checkedInThisWeekIsOutOfActivatedMembersOnly() {
        LocalDate today = LocalDate.of(2026, 10, 19);
        Cohort cohort = setUp(today);
        User activated = saveUser("activated@example.com");
        organisationService().addCohortMember(cohort.getId(), adminIdOf(cohort), activated.getId());
        setGoal(activated, today);
        log(activated, today);

        CohortDashboardSummary summary = service().summarize(cohort.getId(), adminIdOf(cohort), today);

        assertThat(summary.checkedInThisWeek()).isEqualTo(new CohortMeasure(1, 1));
    }

    @Test
    void returningWithinSevenDaysOfAMissedDayIsCountedOutOfThoseWhoEverMissedADay() {
        LocalDate today = LocalDate.of(2026, 10, 30);
        Cohort cohort = setUp(LocalDate.of(2026, 10, 19));
        User returned = saveUser("returned@example.com");
        User neverMissed = saveUser("never-missed@example.com");
        organisationService().addCohortMember(cohort.getId(), adminIdOf(cohort), returned.getId());
        organisationService().addCohortMember(cohort.getId(), adminIdOf(cohort), neverMissed.getId());
        setGoal(returned, LocalDate.of(2026, 10, 19));
        setGoal(neverMissed, LocalDate.of(2026, 10, 19));
        log(returned, LocalDate.of(2026, 10, 19));
        log(returned, LocalDate.of(2026, 10, 20));
        // missed the 21st
        log(returned, LocalDate.of(2026, 10, 22));
        log(neverMissed, LocalDate.of(2026, 10, 19));
        log(neverMissed, LocalDate.of(2026, 10, 20));

        CohortDashboardSummary summary = service().summarize(cohort.getId(), adminIdOf(cohort), today);

        assertThat(summary.returnedAfterAMissedDay()).isEqualTo(new CohortMeasure(1, 1));
    }

    @Test
    void theSurveyCountsEachAnswerOutOfTotalResponses() {
        LocalDate today = LocalDate.of(2026, 10, 19);
        Cohort cohort = setUp(today);
        User a = saveUser("a@example.com");
        User b = saveUser("b@example.com");
        User c = saveUser("c@example.com");
        organisationService().addCohortMember(cohort.getId(), adminIdOf(cohort), a.getId());
        organisationService().addCohortMember(cohort.getId(), adminIdOf(cohort), b.getId());
        organisationService().addCohortMember(cohort.getId(), adminIdOf(cohort), c.getId());
        service().submitSurveyResponse(cohort.getId(), a.getId(), CohortSurveyAnswer.SUPPORTIVE);
        service().submitSurveyResponse(cohort.getId(), b.getId(), CohortSurveyAnswer.NEUTRAL);
        service().submitSurveyResponse(cohort.getId(), c.getId(), CohortSurveyAnswer.PRESSURING);

        CohortDashboardSummary summary = service().summarize(cohort.getId(), adminIdOf(cohort), today);

        assertThat(summary.feltSupportive()).isEqualTo(new CohortMeasure(1, 3));
        assertThat(summary.saidNeutral()).isEqualTo(1);
        assertThat(summary.saidPressuring()).isEqualTo(1);
    }

    @Test
    void resubmittingASurveyResponseReplacesTheAnswerRatherThanAddingASecondRow() {
        LocalDate today = LocalDate.of(2026, 10, 19);
        Cohort cohort = setUp(today);
        User learner = saveUser("learner@example.com");
        organisationService().addCohortMember(cohort.getId(), adminIdOf(cohort), learner.getId());

        service().submitSurveyResponse(cohort.getId(), learner.getId(), CohortSurveyAnswer.PRESSURING);
        service().submitSurveyResponse(cohort.getId(), learner.getId(), CohortSurveyAnswer.SUPPORTIVE);

        CohortDashboardSummary summary = service().summarize(cohort.getId(), adminIdOf(cohort), today);
        assertThat(summary.feltSupportive()).isEqualTo(new CohortMeasure(1, 1));
        assertThat(summary.saidPressuring()).isEqualTo(0);
    }

    @Test
    void aLearnerInTheCohortCanViewTheDashboard() {
        LocalDate today = LocalDate.of(2026, 10, 19);
        Cohort cohort = setUp(today);
        User learner = saveUser("learner@example.com");
        organisationService().addCohortMember(cohort.getId(), adminIdOf(cohort), learner.getId());

        CohortDashboardSummary summary = service().summarize(cohort.getId(), learner.getId(), today);

        assertThat(summary).isNotNull();
    }

    @Test
    void someoneWhoIsNeitherAnAdminNorAMemberCannotViewTheDashboard() {
        LocalDate today = LocalDate.of(2026, 10, 19);
        Cohort cohort = setUp(today);
        User outsider = saveUser("outsider@example.com");

        assertThatThrownBy(() -> service().summarize(cohort.getId(), outsider.getId(), today))
                .isInstanceOf(NotACohortParticipantException.class);
    }

    @Test
    void someoneWhoIsNotAMemberCannotSubmitASurveyResponse() {
        LocalDate today = LocalDate.of(2026, 10, 19);
        Cohort cohort = setUp(today);
        User outsider = saveUser("outsider@example.com");

        assertThatThrownBy(() -> service().submitSurveyResponse(cohort.getId(), outsider.getId(), CohortSurveyAnswer.SUPPORTIVE))
                .isInstanceOf(NotACohortParticipantException.class);
    }

    private Long adminIdOf(Cohort cohort) {
        return organisationAdminRepository.findByOrganisationId(cohort.getOrganisationId()).get(0).getUserId();
    }
}
