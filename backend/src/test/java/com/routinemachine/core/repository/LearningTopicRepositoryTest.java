package com.routinemachine.core.repository;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.Goal;
import com.routinemachine.core.domain.LearningTopic;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class LearningTopicRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private LearningTopicRepository learningTopicRepository;

    @Autowired
    private GoalRepository goalRepository;

    @Test
    void savesATopicLinkedToAGoalAndFindsItByGoalId() {
        Goal goal = goalRepository.findByName("target-roles").orElseThrow();

        LearningTopic saved = learningTopicRepository.save(new LearningTopic("PostHog product analytics", goal));

        List<LearningTopic> found = learningTopicRepository.findByGoalIdWithGoal(goal.getId());

        assertThat(found).extracting(LearningTopic::getId).contains(saved.getId());
        assertThat(found.get(0).getGoal().getName()).isEqualTo("target-roles");
    }
}
