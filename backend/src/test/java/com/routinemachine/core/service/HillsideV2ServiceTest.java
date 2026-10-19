package com.routinemachine.core.service;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.DailyLogSource;
import com.routinemachine.core.domain.Goal;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.DailyCreditRepository;
import com.routinemachine.core.repository.EvidenceEventRepository;
import com.routinemachine.core.repository.GoalRepository;
import com.routinemachine.core.repository.ScoringRuleRepository;
import com.routinemachine.core.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class HillsideV2ServiceTest extends AbstractIntegrationTest {

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

    private EvidenceCreditService evidenceService() {
        return new EvidenceCreditService(evidenceEventRepository, dailyCreditRepository, scoringRuleRepository);
    }

    private HillsideV2Service hillsideV2Service() {
        return new HillsideV2Service(dailyCreditRepository, userRepository);
    }

    private void tend(User user, Goal goal, LocalDate day) {
        evidenceService().ingest(
                user, goal, DailyLogSource.MANUAL, "tap-" + day, day.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(), null);
    }

    @Test
    void aReturnIsCountedWhenADayFollowsAGapButNotOnTheFirstEverCreditedDay() {
        User user = userRepository.save(new User("returns@example.com", "irrelevant-hash", null, null));
        Goal goal = goalRepository.save(new Goal("returns-goal", null));

        tend(user, goal, LocalDate.of(2026, 10, 12)); // first ever day: a start, not a return
        // 10-13 and 10-14 missed
        tend(user, goal, LocalDate.of(2026, 10, 15)); // a return
        tend(user, goal, LocalDate.of(2026, 10, 16)); // consecutive, not a return
        // 10-17 missed
        tend(user, goal, LocalDate.of(2026, 10, 18)); // a second return

        int returns = hillsideV2Service().countReturns(user.getId(), LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18));

        assertThat(returns).isEqualTo(2);
    }

    @Test
    void theFirstEverCreditedDayIsNeverCountedAsAReturnEvenAtTheStartOfASeason() {
        User user = userRepository.save(new User("first-day@example.com", "irrelevant-hash", null, null));
        Goal goal = goalRepository.save(new Goal("first-day-goal", null));

        tend(user, goal, LocalDate.of(2026, 10, 12));

        int returns = hillsideV2Service().countReturns(user.getId(), LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18));

        assertThat(returns).isZero();
    }
}
