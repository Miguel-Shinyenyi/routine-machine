package com.routinemachine.core.service;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.DailyLogSource;
import com.routinemachine.core.domain.Goal;
import com.routinemachine.core.domain.ScoringRule;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.DailyCreditRepository;
import com.routinemachine.core.repository.EvidenceEventRepository;
import com.routinemachine.core.repository.GoalRepository;
import com.routinemachine.core.repository.ScoringRuleRepository;
import com.routinemachine.core.repository.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

// Observability, part 3: every new piece of evidence is counted by what was decided about it and by
// which published rule version decided it. No user, goal name or email is a tag.
@DataJpaTest
class EvidenceCreditMetricsTest extends AbstractIntegrationTest {

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

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private EvidenceCreditService service() {
        return new EvidenceCreditService(evidenceEventRepository, dailyCreditRepository, scoringRuleRepository, registry);
    }

    private double count(String status, String ruleVersion) {
        var counter = registry.find("credits.events").tag("status", status).tag("rule_version", ruleVersion).counter();
        return counter == null ? 0 : counter.count();
    }

    private Goal goalWithRuleVersionTwo() {
        Goal goal = goalRepository.save(new Goal("metrics-goal", null));
        scoringRuleRepository.save(new ScoringRule(2, goal, 30, "30 minutes counts", LocalDate.of(2026, 1, 1)));
        return goal;
    }

    @Test
    void countsAcceptedRejectedPendingAndSelfReportedByRuleVersion() {
        User user = userRepository.save(new User("metrics-a@example.com", "irrelevant-hash", null, null));
        Goal goal = goalWithRuleVersionTwo();
        Instant when = Instant.parse("2026-10-16T12:00:00Z");

        service().ingest(user, goal, DailyLogSource.WAKATIME_SYNC, "m-1", when, 45);
        service().ingest(user, goal, DailyLogSource.WAKATIME_SYNC, "m-2", when, 10);
        service().ingest(user, goal, DailyLogSource.WAKATIME_SYNC, "m-3", when, null);
        service().ingest(user, goal, DailyLogSource.MANUAL, "m-4", when, null);

        assertThat(count("ACCEPTED", "2")).isEqualTo(1);
        assertThat(count("REJECTED", "2")).isEqualTo(1);
        assertThat(count("PENDING", "2")).isEqualTo(1);
        assertThat(count("SELF_REPORTED", "2")).isEqualTo(1);
    }

    @Test
    void aGoalWithNoPublishedRuleIsVersionZero() {
        User user = userRepository.save(new User("metrics-b@example.com", "irrelevant-hash", null, null));
        Goal goal = goalRepository.save(new Goal("metrics-no-rule", null));

        service().ingest(user, goal, DailyLogSource.WAKATIME_SYNC, "m-5", Instant.parse("2026-10-16T12:00:00Z"), 5);

        assertThat(count("ACCEPTED", "0")).isEqualTo(1);
    }

    @Test
    void aDuplicateEventIsNotCountedAgain() {
        User user = userRepository.save(new User("metrics-c@example.com", "irrelevant-hash", null, null));
        Goal goal = goalWithRuleVersionTwo();
        Instant when = Instant.parse("2026-10-16T12:00:00Z");

        service().ingest(user, goal, DailyLogSource.WAKATIME_SYNC, "m-dup", when, 45);
        service().ingest(user, goal, DailyLogSource.WAKATIME_SYNC, "m-dup", when, 45);

        assertThat(count("ACCEPTED", "2")).isEqualTo(1);
    }

    @Test
    void noMeterCarriesAUserOrGoalTag() {
        User user = userRepository.save(new User("metrics-d@example.com", "irrelevant-hash", null, null));
        service().ingest(user, goalWithRuleVersionTwo(), DailyLogSource.WAKATIME_SYNC, "m-6",
                Instant.parse("2026-10-16T12:00:00Z"), 45);

        registry.getMeters().forEach(meter -> assertThat(meter.getId().getTags())
                .extracting(tag -> tag.getKey()).containsOnly("status", "rule_version"));
    }
}
