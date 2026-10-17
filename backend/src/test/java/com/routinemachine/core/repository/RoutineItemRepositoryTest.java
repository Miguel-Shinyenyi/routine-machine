package com.routinemachine.core.repository;

import com.routinemachine.core.domain.Goal;
import com.routinemachine.core.domain.HillsideField;
import com.routinemachine.core.domain.RoutineItem;
import com.routinemachine.core.domain.RoutineItemCategory;
import com.routinemachine.core.domain.RoutineItemSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RoutineItemRepositoryTest extends com.routinemachine.core.AbstractIntegrationTest {

    @Autowired
    private RoutineItemRepository routineItemRepository;

    @Autowired
    private GoalRepository goalRepository;

    @Test
    void savesAndReloadsARoutineItem() {
        RoutineItem saved = routineItemRepository.save(new RoutineItem("journaling", "Daily journal entry"));

        RoutineItem found = routineItemRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getName()).isEqualTo("journaling");
        assertThat(found.getDescription()).isEqualTo("Daily journal entry");
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void defaultsSourceToManualAndPrivateToTrueWhenNotGiven() {
        RoutineItem saved = routineItemRepository.save(new RoutineItem("exercise", null));

        RoutineItem found = routineItemRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getSource()).isEqualTo(RoutineItemSource.MANUAL);
        assertThat(found.isPrivate()).isTrue();
        assertThat(found.getCategory()).isNull();
        assertThat(found.getCountRule()).isNull();
        assertThat(found.getHillsideField()).isNull();
        assertThat(found.getGoal()).isNull();
    }

    @Test
    void savesAndReloadsAFullyCategorisedRoutineItem() {
        Goal goal = goalRepository.save(new Goal("categorisation-test-goal", null));
        RoutineItem item = new RoutineItem(
                "deep work", "Routine Machine build", RoutineItemCategory.WORK, "30 active minutes",
                RoutineItemSource.WAKATIME, HillsideField.DEEP_WORK, false, goal);

        RoutineItem saved = routineItemRepository.save(item);
        RoutineItem found = routineItemRepository.findWithGoalById(saved.getId()).orElseThrow();

        assertThat(found.getCategory()).isEqualTo(RoutineItemCategory.WORK);
        assertThat(found.getCountRule()).isEqualTo("30 active minutes");
        assertThat(found.getSource()).isEqualTo(RoutineItemSource.WAKATIME);
        assertThat(found.getHillsideField()).isEqualTo(HillsideField.DEEP_WORK);
        assertThat(found.isPrivate()).isFalse();
        assertThat(found.getGoal().getName()).isEqualTo("categorisation-test-goal");
    }
}
