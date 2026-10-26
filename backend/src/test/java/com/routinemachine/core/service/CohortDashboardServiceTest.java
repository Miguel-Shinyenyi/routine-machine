package com.routinemachine.core.service;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.Cohort;
import com.routinemachine.core.domain.CohortMembership;
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

import jakarta.persistence.EntityManager;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

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
    @Autowired
    private EntityManager entityManager;

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

    // joined_at and goals.created_at are database-generated (DEFAULT now()) and not settable
    // through the entities, but activation is measured against them. A test that leaves them at
    // the real "now" only passes on the calendar day it was written, so every test that depends
    // on activation pins them to the date it is about.
    private void pinTimestamp(String table, Long id, String column, LocalDate date) {
        entityManager.flush();
        entityManager.createNativeQuery("update " + table + " set " + column + " = ?1 where id = ?2")
                .setParameter(1, date.atTime(12, 0).atOffset(ZoneOffset.UTC))
                .setParameter(2, id)
                .executeUpdate();
        entityManager.clear();
    }

    private void addMember(Cohort cohort, User user, LocalDate joinedOn) {
        CohortMembership membership = organisationService().addCohortMember(cohort.getId(), adminIdOf(cohort), user.getId());
        pinTimestamp("cohort_memberships", membership.getId(), "joined_at", joinedOn);
    }

    private void setGoal(User user, LocalDate createdOn) {
        Goal goal = goalRepository.save(new Goal("Learn Spring", "desc", user));
        pinTimestamp("goals", goal.getId(), "created_at", createdOn);
    }

    private Cohort setUp(LocalDate joinedDate) {
        User admin = saveUser("admin-" + joinedDate + "@example.com");
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
        addMember(cohort, activated, today);
        addMember(cohort, notActivated, today);
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
        addMember(cohort, member, today);
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
        addMember(cohort, activated, today);
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
        addMember(cohort, returned, LocalDate.of(2026, 10, 19));
        addMember(cohort, neverMissed, LocalDate.of(2026, 10, 19));
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

    @Test
    void aGroupOfFiveOrMoreShowsItsRealCheckedInAndReturnedRates() {
        LocalDate today = LocalDate.of(2026, 10, 19);
        Cohort cohort = setUp(today);
        for (int i = 0; i < 4; i++) {
            User member = saveUser("morning" + i + "@example.com");
            cohortMembershipRepository.save(
                    new CohortMembership(cohort.getId(), member.getId(), "Morning group"));
        }
        User checkedIn = saveUser("morning-checked-in@example.com");
        cohortMembershipRepository.save(
                new CohortMembership(cohort.getId(), checkedIn.getId(), "Morning group"));
        log(checkedIn, today);

        List<CohortGroupMeasure> groups = service().summarizeByGroup(cohort.getId(), adminIdOf(cohort), today);

        assertThat(groups).hasSize(1);
        CohortGroupMeasure morning = groups.get(0);
        assertThat(morning.studyGroup()).isEqualTo("Morning group");
        assertThat(morning.memberCount()).isEqualTo(5);
        assertThat(morning.hidden()).isFalse();
        assertThat(morning.checkedInThisWeek()).isEqualTo(new CohortMeasure(1, 5));
    }

    @Test
    void aGroupUnderFiveIsHiddenButStillShowsItsMemberCount() {
        LocalDate today = LocalDate.of(2026, 10, 19);
        Cohort cohort = setUp(today);
        for (int i = 0; i < 4; i++) {
            User member = saveUser("weekend" + i + "@example.com");
            cohortMembershipRepository.save(
                    new CohortMembership(cohort.getId(), member.getId(), "Weekend group"));
        }

        List<CohortGroupMeasure> groups = service().summarizeByGroup(cohort.getId(), adminIdOf(cohort), today);

        assertThat(groups).hasSize(1);
        CohortGroupMeasure weekend = groups.get(0);
        assertThat(weekend.memberCount()).isEqualTo(4);
        assertThat(weekend.hidden()).isTrue();
        assertThat(weekend.checkedInThisWeek()).isNull();
        assertThat(weekend.returnedAfterAMissedDay()).isNull();
    }

    @Test
    void membersWithNoStudyGroupDoNotAppearInTheByGroupBreakdown() {
        LocalDate today = LocalDate.of(2026, 10, 19);
        Cohort cohort = setUp(today);
        User ungrouped = saveUser("ungrouped@example.com");
        cohortMembershipRepository.save(new CohortMembership(cohort.getId(), ungrouped.getId()));

        List<CohortGroupMeasure> groups = service().summarizeByGroup(cohort.getId(), adminIdOf(cohort), today);

        assertThat(groups).isEmpty();
    }

    @Test
    void weeklyCheckInsCountsActivatedMembersWhoLoggedInEachElapsedWeek() {
        LocalDate cohortStart = LocalDate.of(2026, 9, 1);
        LocalDate today = cohortStart.plusDays(15);
        Cohort cohort = setUp(cohortStart);
        User activated = saveUser("activated@example.com");
        addMember(cohort, activated, cohortStart);
        setGoal(activated, cohortStart);
        log(activated, cohortStart);
        log(activated, cohortStart.plusDays(8));

        CohortWeeklyCheckInChart chart = service().weeklyCheckIns(cohort.getId(), adminIdOf(cohort), today);

        assertThat(chart.activatedDenominator()).isEqualTo(1);
        assertThat(chart.weeks()).hasSize(8);
        assertThat(chart.weeks().get(0).week()).isEqualTo(1);
        assertThat(chart.weeks().get(0).checkedIn()).isEqualTo(1);
        assertThat(chart.weeks().get(0).isFuture()).isFalse();
        assertThat(chart.weeks().get(1).checkedIn()).isEqualTo(1);
        assertThat(chart.weeks().get(2).isCurrentWeek()).isTrue();
        assertThat(chart.weeks().get(2).checkedIn()).isEqualTo(0);
    }

    // Proves the activation tests no longer depend on the wall clock: the same scenario dated
    // years from now (and, below, years ago) must give the same answer. Before the timestamps
    // were pinned, a cohort dated away from the real "now" never activated anyone.
    @Test
    void activationDoesNotDependOnTheRealDate() {
        for (LocalDate start : new LocalDate[] {LocalDate.of(2031, 3, 4), LocalDate.of(2019, 6, 10)}) {
            Cohort cohort = setUp(start);
            User activated = saveUser("far-" + start + "@example.com");
            addMember(cohort, activated, start);
            setGoal(activated, start);
            log(activated, start.plusDays(1));

            CohortDashboardSummary summary = service().summarize(cohort.getId(), adminIdOf(cohort), start.plusDays(2));

            assertThat(summary.activatedWithinSevenDays()).as("cohort starting %s", start).isEqualTo(new CohortMeasure(1, 1));
        }
    }

    @Test
    void weeksThatHaveNotStartedYetReportNullNotZero() {
        LocalDate cohortStart = LocalDate.of(2026, 9, 1);
        LocalDate today = LocalDate.of(2026, 9, 2);
        Cohort cohort = setUp(cohortStart);

        CohortWeeklyCheckInChart chart = service().weeklyCheckIns(cohort.getId(), adminIdOf(cohort), today);

        assertThat(chart.weeks().get(0).isFuture()).isFalse();
        assertThat(chart.weeks().get(1).isFuture()).isTrue();
        assertThat(chart.weeks().get(1).checkedIn()).isNull();
    }

    @Test
    void someoneWhoIsNeitherAnAdminNorAMemberCannotViewTheWeeklyChart() {
        LocalDate today = LocalDate.of(2026, 10, 19);
        Cohort cohort = setUp(today);
        User outsider = saveUser("outsider@example.com");

        assertThatThrownBy(() -> service().weeklyCheckIns(cohort.getId(), outsider.getId(), today))
                .isInstanceOf(NotACohortParticipantException.class);
    }

    private Long adminIdOf(Cohort cohort) {
        return organisationAdminRepository.findByOrganisationId(cohort.getOrganisationId()).get(0).getUserId();
    }
}
