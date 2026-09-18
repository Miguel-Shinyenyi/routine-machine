package com.routinemachine.core.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DailyLogTest {

    private final RoutineItem routineItem = new RoutineItem("journaling", "Daily journal entry");
    private final LearningTopic learningTopic = new LearningTopic("PostHog product analytics", new Goal("target-roles", null));

    @Test
    void forRoutineItemSetsSourceToManualByDefault() {
        DailyLog log = DailyLog.forRoutineItem(LocalDate.of(2026, 9, 18), routineItem);

        assertThat(log.getSource()).isEqualTo(DailyLogSource.MANUAL);
        assertThat(log.getRoutineItem()).isEqualTo(routineItem);
        assertThat(log.getLearningTopic()).isNull();
    }

    @Test
    void forLearningTopicAcceptsAnExplicitSource() {
        DailyLog log = DailyLog.forLearningTopic(
                LocalDate.of(2026, 9, 18), learningTopic, DailyLogSource.HUB_SYNC);

        assertThat(log.getSource()).isEqualTo(DailyLogSource.HUB_SYNC);
        assertThat(log.getLearningTopic()).isEqualTo(learningTopic);
        assertThat(log.getRoutineItem()).isNull();
    }

    @Test
    void rejectsANullRoutineItem() {
        assertThatThrownBy(() -> DailyLog.forRoutineItem(LocalDate.of(2026, 9, 18), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsANullLearningTopic() {
        assertThatThrownBy(() -> DailyLog.forLearningTopic(LocalDate.of(2026, 9, 18), null, DailyLogSource.MANUAL))
                .isInstanceOf(NullPointerException.class);
    }
}
