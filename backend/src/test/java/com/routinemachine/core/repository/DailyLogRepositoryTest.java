package com.routinemachine.core.repository;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.DailyLog;
import com.routinemachine.core.domain.DailyLogSource;
import com.routinemachine.core.domain.Goal;
import com.routinemachine.core.domain.LearningTopic;
import com.routinemachine.core.domain.RoutineItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DailyLogRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private DailyLogRepository dailyLogRepository;

    @Autowired
    private RoutineItemRepository routineItemRepository;

    @Autowired
    private LearningTopicRepository learningTopicRepository;

    @Autowired
    private GoalRepository goalRepository;

    @Test
    void savesARoutineItemCompletionAndFindsItByDate() {
        RoutineItem item = routineItemRepository.save(new RoutineItem("journaling", null));
        LocalDate today = LocalDate.of(2026, 9, 18);

        dailyLogRepository.save(DailyLog.forRoutineItem(today, item));

        List<DailyLog> found = dailyLogRepository.findByLogDate(today);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getRoutineItem().getId()).isEqualTo(item.getId());
        assertThat(found.get(0).getSource()).isEqualTo(DailyLogSource.MANUAL);
    }

    @Test
    void savesALearningTopicCompletionAndFindsItByDate() {
        Goal goal = goalRepository.findByName("target-roles").orElseThrow();
        LearningTopic topic = learningTopicRepository.save(new LearningTopic("PostHog product analytics", goal));
        LocalDate today = LocalDate.of(2026, 9, 18);

        dailyLogRepository.save(DailyLog.forLearningTopic(today, topic));

        List<DailyLog> found = dailyLogRepository.findByLogDate(today);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getLearningTopic().getId()).isEqualTo(topic.getId());
    }
}
