package com.routinemachine.core.repository;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.Goal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class GoalRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private GoalRepository goalRepository;

    @Test
    void seedMigrationInsertsTheThreeTrackedGoals() {
        List<Goal> goals = goalRepository.findAll();

        assertThat(goals).extracting(Goal::getName).containsExactlyInAnyOrder(
                "cmu-masters-application", "target-roles", "ai-engineer-evidence");
    }

    @Test
    void savesAndReloadsAGoalByName() {
        Goal found = goalRepository.findByName("target-roles").orElseThrow();

        assertThat(found.getDescription()).contains("PostHog");
        assertThat(found.getCreatedAt()).isNotNull();
    }
}
