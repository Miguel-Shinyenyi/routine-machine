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

import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class HillsideV2ComputeTest extends AbstractIntegrationTest {

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

    private HillsideV2Service service() {
        return new HillsideV2Service(dailyCreditRepository, userRepository);
    }

    private User saveOptedInUser(String email) {
        User user = new User(email, "irrelevant-hash", null, null);
        user.optIntoHillside();
        return userRepository.save(user);
    }

    private void tend(User user, Goal goal, LocalDate day) {
        evidenceService().ingest(
                user, goal, DailyLogSource.MANUAL, user.getEmail() + "-" + goal.getId() + "-" + day,
                day.atStartOfDay(ZoneOffset.UTC).toInstant(), null);
    }

    @Test
    void ranksParticipantsByTheirSeasonScore() {
        LocalDate seasonStart = LocalDate.of(2026, 10, 12);
        LocalDate seasonEnd = LocalDate.of(2026, 10, 18);
        User you = saveOptedInUser("you@example.com");
        User rival = saveOptedInUser("rival@example.com");
        Goal goal = goalRepository.save(new Goal("coding", null));

        tend(you, goal, seasonStart);
        tend(rival, goal, seasonStart);
        tend(rival, goal, seasonStart.plusDays(1));
        tend(rival, goal, seasonStart.plusDays(2));

        HillsideV2Result result = service().compute(null, seasonStart, seasonEnd, you.getId());

        assertThat(result.totalParticipants()).isEqualTo(2);
        assertThat(result.yourScore()).isEqualTo(1);
        assertThat(result.yourRank()).isEqualTo(2);
        assertThat(result.neighbors()).extracting(NeighborCreditRow::isYou).contains(true);
    }

    @Test
    void aUserWhoHasNotOptedInIsNeverAParticipant() {
        LocalDate seasonStart = LocalDate.of(2026, 10, 12);
        LocalDate seasonEnd = LocalDate.of(2026, 10, 18);
        User notOptedIn = userRepository.save(new User("not-opted-in@example.com", "irrelevant-hash", null, null));
        Goal goal = goalRepository.save(new Goal("coding-2", null));

        tend(notOptedIn, goal, seasonStart);

        HillsideV2Result result = service().compute(null, seasonStart, seasonEnd, notOptedIn.getId());

        assertThat(result.totalParticipants()).isZero();
        assertThat(result.yourScore()).isNull();
    }

    @Test
    void filteringByGoalOnlyScoresThatGoalsCredits() {
        LocalDate seasonStart = LocalDate.of(2026, 10, 12);
        LocalDate seasonEnd = LocalDate.of(2026, 10, 18);
        User you = saveOptedInUser("filtered@example.com");
        Goal coding = goalRepository.save(new Goal("filtered-coding", null));
        Goal reading = goalRepository.save(new Goal("filtered-reading", null));

        tend(you, coding, seasonStart);
        tend(you, reading, seasonStart);
        tend(you, reading, seasonStart.plusDays(1));

        HillsideV2Result all = service().compute(null, seasonStart, seasonEnd, you.getId());
        HillsideV2Result readingOnly = service().compute(reading.getId(), seasonStart, seasonEnd, you.getId());

        assertThat(all.yourScore()).isEqualTo(3);
        assertThat(readingOnly.yourScore()).isEqualTo(2);
    }

    @Test
    void cohortReturnsCountsEveryoneWhoCameBackAtLeastOnce() {
        LocalDate seasonStart = LocalDate.of(2026, 10, 12);
        LocalDate seasonEnd = LocalDate.of(2026, 10, 18);
        User steady = saveOptedInUser("steady@example.com");
        User comeback = saveOptedInUser("comeback@example.com");
        Goal goal = goalRepository.save(new Goal("returns-compute-goal", null));

        tend(steady, goal, seasonStart);
        tend(steady, goal, seasonStart.plusDays(1));

        tend(comeback, goal, seasonStart);
        // 13th and 14th missed
        tend(comeback, goal, seasonStart.plusDays(3));

        HillsideV2Result result = service().compute(null, seasonStart, seasonEnd, steady.getId());

        assertThat(result.cohortReturns()).isEqualTo(1);
    }
}
