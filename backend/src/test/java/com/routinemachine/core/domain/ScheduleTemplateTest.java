package com.routinemachine.core.domain;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScheduleTemplateTest {

    private final RoutineItem routineItem = new RoutineItem("exercise", null);

    @Test
    void forRoutineItemSetsTheTargetType() {
        ScheduleTemplate template = ScheduleTemplate.forRoutineItem(
                DayOfWeek.MONDAY, routineItem, 60, 1, "Weight lifting");

        assertThat(template.getTargetType()).isEqualTo(ScheduleTargetType.ROUTINE_ITEM);
        assertThat(template.getRoutineItem()).isEqualTo(routineItem);
        assertThat(template.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(template.getLabel()).isEqualTo("Weight lifting");
    }

    @Test
    void forLearningSlotHasNoRoutineItem() {
        ScheduleTemplate template = ScheduleTemplate.forLearningSlot(
                DayOfWeek.TUESDAY, 45, 2, "Goal-suggested topic");

        assertThat(template.getTargetType()).isEqualTo(ScheduleTargetType.LEARNING_SLOT);
        assertThat(template.getRoutineItem()).isNull();
    }

    @Test
    void rejectsANullRoutineItemForARoutineItemTemplate() {
        assertThatThrownBy(() -> ScheduleTemplate.forRoutineItem(DayOfWeek.MONDAY, null, 60, 1, "x"))
                .isInstanceOf(NullPointerException.class);
    }
}
