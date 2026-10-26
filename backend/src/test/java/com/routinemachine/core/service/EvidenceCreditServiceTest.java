package com.routinemachine.core.service;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.DailyLogSource;
import com.routinemachine.core.domain.EvidenceEvent;
import com.routinemachine.core.domain.EvidenceStatus;
import com.routinemachine.core.domain.Goal;
import com.routinemachine.core.domain.ScoringRule;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.DailyCreditRepository;
import com.routinemachine.core.repository.EvidenceEventRepository;
import com.routinemachine.core.repository.GoalRepository;
import com.routinemachine.core.repository.ScoringRuleRepository;
import com.routinemachine.core.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class EvidenceCreditServiceTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private GoalRepository goalRepository;
    @Autowired
    private EvidenceEventRepository evidenceEventRepository;
    @Autowired
    private DailyCreditRepository dailyCreditRepository;
    @Autowired
    private ScoringRuleRepository scoringRuleRepository;
    @Autowired
    private DataSource dataSource;

    private EvidenceCreditService service() {
        return new EvidenceCreditService(evidenceEventRepository, dailyCreditRepository, scoringRuleRepository);
    }

    private User saveUser(String email) {
        return userRepository.save(new User(email, "irrelevant-hash", null, null));
    }

    private User saveUserInZone(String email, String zone) {
        User user = new User(email, "irrelevant-hash", null, null);
        user.changeTimeZone(zone);
        return userRepository.save(user);
    }

    private Goal saveGoal(String name) {
        return goalRepository.save(new Goal(name, null));
    }

    @Test
    void aDuplicateEventEarnsNoSecondCredit() {
        User user = saveUser("duplicate@example.com");
        Goal goal = saveGoal("coding");
        Instant occurredAt = Instant.parse("2026-10-16T10:00:00Z");

        service().ingest(user, goal, DailyLogSource.WAKATIME_SYNC, "waka-2026-10-16", occurredAt, 45);
        service().ingest(user, goal, DailyLogSource.WAKATIME_SYNC, "waka-2026-10-16", occurredAt, 45);

        assertThat(evidenceEventRepository.findAll()).hasSize(1);
        assertThat(dailyCreditRepository.findByUserIdAndLocalDateBetween(
                user.getId(), LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 16))).hasSize(1);
    }

    // No enclosing transaction: @DataJpaTest's default per-test transaction (rolled back at
    // the end) would keep this test's own setup rows invisible to the two separate raw JDBC
    // connections below, since Postgres isolation hides another transaction's uncommitted
    // writes — this needs genuinely committed rows for a real concurrent-commit test.
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void twoConcurrentInsertsForTheSameUserGoalAndDayProduceOneRow() throws InterruptedException {
        User user = saveUser("concurrent@example.com");
        Goal goal = saveGoal("concurrent-goal");
        LocalDate day = LocalDate.of(2026, 10, 16);

        // Two already-accepted evidence events for the same (user, goal, day) — the race under
        // test is the daily_credits upsert itself, not the qualify step. Raw JDBC, two
        // separate connections, so this is a genuine concurrent commit against Postgres, not
        // serialized behind one Spring-managed test transaction.
        Long eventIdA = insertAcceptedEvidenceEvent(user, goal, day, "evt-a");
        Long eventIdB = insertAcceptedEvidenceEvent(user, goal, day, "evt-b");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        var futureA = pool.submit(racingInsert(user.getId(), goal.getId(), day, eventIdA, ready, go));
        var futureB = pool.submit(racingInsert(user.getId(), goal.getId(), day, eventIdB, ready, go));
        try {
            ready.await(5, TimeUnit.SECONDS);
            go.countDown();
            futureA.get(5, TimeUnit.SECONDS);
            futureB.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            pool.shutdown();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }

        assertThat(dailyCreditRepository.findByUserIdAndLocalDateBetween(user.getId(), day, day)).hasSize(1);
    }

    private Long insertAcceptedEvidenceEvent(User user, Goal goal, LocalDate day, String sourceEventId) {
        EvidenceEvent event = new EvidenceEvent(
                user, goal, DailyLogSource.MANUAL, sourceEventId, day.atStartOfDay(ZoneOffset.UTC).toInstant(),
                day, null, EvidenceStatus.SELF_REPORTED, 0, "test fixture");
        return evidenceEventRepository.saveAndFlush(event).getId();
    }

    private Runnable racingInsert(
            Long userId, Long goalId, LocalDate day, Long evidenceEventId, CountDownLatch ready, CountDownLatch go) {
        return () -> {
            try (Connection connection = dataSource.getConnection()) {
                ready.countDown();
                go.await(5, TimeUnit.SECONDS);
                try (var statement = connection.prepareStatement(
                        "INSERT INTO daily_credits (user_id, goal_id, local_date, credits, evidence_event_id) "
                                + "VALUES (?, ?, ?, 1, ?) ON CONFLICT (user_id, goal_id, local_date) DO NOTHING")) {
                    statement.setLong(1, userId);
                    statement.setLong(2, goalId);
                    statement.setObject(3, day);
                    statement.setLong(4, evidenceEventId);
                    statement.executeUpdate();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    @Test
    void aMissedDaySubtractsNothing() {
        User user = saveUser("missed-day@example.com");
        Goal goal = saveGoal("missed-day-goal");

        service().ingest(user, goal, DailyLogSource.MANUAL, "day-1", Instant.parse("2026-10-14T12:00:00Z"), null);
        // 2026-10-15 is deliberately skipped — no evidence at all.
        service().ingest(user, goal, DailyLogSource.MANUAL, "day-3", Instant.parse("2026-10-16T12:00:00Z"), null);

        HillsideV2Service hillsideV2Service = new HillsideV2Service(dailyCreditRepository, userRepository);
        int score = hillsideV2Service.seasonScore(user.getId(), LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 16));

        assertThat(score).isEqualTo(2);
    }

    @Test
    void theDailyCapHolds() {
        User user = saveUser("daily-cap@example.com");
        Goal coding = saveGoal("coding-goal");
        Goal reading = saveGoal("reading-goal");
        Goal learning = saveGoal("learning-goal");
        Goal writing = saveGoal("writing-goal");
        Instant occurredAt = Instant.parse("2026-10-16T12:00:00Z");

        service().ingest(user, coding, DailyLogSource.MANUAL, "coding-1", occurredAt, null);
        service().ingest(user, reading, DailyLogSource.MANUAL, "reading-1", occurredAt, null);
        service().ingest(user, learning, DailyLogSource.MANUAL, "learning-1", occurredAt, null);
        service().ingest(user, writing, DailyLogSource.MANUAL, "writing-1", occurredAt, null);

        assertThat(dailyCreditRepository.findByUserIdAndLocalDateBetween(
                user.getId(), LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 16))).hasSize(4);

        HillsideV2Service hillsideV2Service = new HillsideV2Service(dailyCreditRepository, userRepository);
        int score = hillsideV2Service.seasonScore(user.getId(), LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 16));

        assertThat(score).isEqualTo(3);
    }

    @Test
    void aCreditDecidedOnNairobiTimeNearMidnightLandsOnTheRightLocalDate() {
        User user = saveUserInZone("nairobi@example.com", "Africa/Nairobi");
        Goal goal = saveGoal("nairobi-goal");
        // 21:30 UTC on the 16th is 00:30 EAT (UTC+3) on the 17th.
        Instant occurredAt = Instant.parse("2026-10-16T21:30:00Z");

        EvidenceEvent event = service().ingest(user, goal, DailyLogSource.MANUAL, "nairobi-1", occurredAt, null);

        assertThat(event.getLocalDate()).isEqualTo(LocalDate.of(2026, 10, 17));
        assertThat(dailyCreditRepository.findByUserIdAndLocalDateBetween(
                user.getId(), LocalDate.of(2026, 10, 17), LocalDate.of(2026, 10, 17))).hasSize(1);
    }

    @Test
    void lateStudyBeforeTheConfiguredDayEndHourStillCountsForThePreviousDay() {
        User user = saveUserInZone("late-study@example.com", "Africa/Nairobi");
        user.updateProfile(null, null, null, null, 3, null, null);
        Goal goal = saveGoal("late-study-goal");
        // 00:30 EAT on the 17th, before the 3am day-end hour: still counts as the 16th.
        Instant occurredAt = Instant.parse("2026-10-16T21:30:00Z");

        EvidenceEvent event = service().ingest(user, goal, DailyLogSource.MANUAL, "late-study-1", occurredAt, null);

        assertThat(event.getLocalDate()).isEqualTo(LocalDate.of(2026, 10, 16));
    }

    @Test
    void studyAtOrAfterTheConfiguredDayEndHourCountsForTheNewDay() {
        User user = saveUserInZone("after-day-end@example.com", "Africa/Nairobi");
        user.updateProfile(null, null, null, null, 3, null, null);
        Goal goal = saveGoal("after-day-end-goal");
        // 03:30 EAT on the 17th, at/after the 3am day-end hour: counts as the 17th.
        Instant occurredAt = Instant.parse("2026-10-17T00:30:00Z");

        EvidenceEvent event = service().ingest(user, goal, DailyLogSource.MANUAL, "after-day-end-1", occurredAt, null);

        assertThat(event.getLocalDate()).isEqualTo(LocalDate.of(2026, 10, 17));
    }

    @Test
    void evidenceBelowTheMinuteThresholdIsRejectedAndEarnsNoCredit() {
        User user = saveUser("rejected@example.com");
        Goal goal = saveGoal("rejected-goal");
        scoringRuleRepository.save(new ScoringRule(1, goal, 30, "30 minutes or more", LocalDate.of(2026, 10, 1)));

        EvidenceEvent event = service().ingest(
                user, goal, DailyLogSource.WAKATIME_SYNC, "short-session", Instant.parse("2026-10-16T12:00:00Z"), 18);

        assertThat(event.getStatus()).isEqualTo(EvidenceStatus.REJECTED);
        assertThat(dailyCreditRepository.findByUserIdAndLocalDateBetween(
                user.getId(), LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 16))).isEmpty();
    }

    @Test
    void evidenceAtOrAboveTheMinuteThresholdIsAcceptedAndEarnsCredit() {
        User user = saveUser("accepted@example.com");
        Goal goal = saveGoal("accepted-goal");
        scoringRuleRepository.save(new ScoringRule(1, goal, 30, "30 minutes or more", LocalDate.of(2026, 10, 1)));

        EvidenceEvent event = service().ingest(
                user, goal, DailyLogSource.WAKATIME_SYNC, "long-session", Instant.parse("2026-10-16T12:00:00Z"), 52);

        assertThat(event.getStatus()).isEqualTo(EvidenceStatus.ACCEPTED);
        assertThat(dailyCreditRepository.findByUserIdAndLocalDateBetween(
                user.getId(), LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 16))).hasSize(1);
    }

    @Test
    void manualEvidenceIsSelfReportedAndStillEarnsCredit() {
        User user = saveUser("self-report@example.com");
        Goal goal = saveGoal("self-report-goal");

        EvidenceEvent event = service().ingest(
                user, goal, DailyLogSource.MANUAL, "tap-1", Instant.parse("2026-10-16T12:00:00Z"), null);

        assertThat(event.getStatus()).isEqualTo(EvidenceStatus.SELF_REPORTED);
        assertThat(dailyCreditRepository.findByUserIdAndLocalDateBetween(
                user.getId(), LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 16))).hasSize(1);
    }

    @Test
    void aGoalWithNoScoringRuleFallsBackToPresenceBasedAcceptance() {
        User user = saveUser("no-rule@example.com");
        Goal goal = saveGoal("ungoverned-goal");

        EvidenceEvent event = service().ingest(
                user, goal, DailyLogSource.ACTIVITYWATCH_SYNC, "aw-1", Instant.parse("2026-10-16T12:00:00Z"), 5);

        assertThat(event.getStatus()).isEqualTo(EvidenceStatus.ACCEPTED);
        assertThat(event.getScoringRuleVersion()).isZero();
    }

    @Test
    void evidenceWithNoMinutesAgainstAMinuteBasedRuleIsPending() {
        User user = saveUser("pending@example.com");
        Goal goal = saveGoal("pending-goal");
        scoringRuleRepository.save(new ScoringRule(1, goal, 20, "20 minutes of movement", LocalDate.of(2026, 10, 1)));

        EvidenceEvent event = service().ingest(
                user, goal, DailyLogSource.HUB_SYNC, "pending-1", Instant.parse("2026-10-16T12:00:00Z"), null);

        assertThat(event.getStatus()).isEqualTo(EvidenceStatus.PENDING);
        assertThat(dailyCreditRepository.findByUserIdAndLocalDateBetween(
                user.getId(), LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 16))).isEmpty();
    }
}
