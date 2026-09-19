package com.routinemachine.core.domain;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskTest {

    private final RoutineItem routineItem = new RoutineItem("exercise", null);
    private final LearningTopic learningTopic = new LearningTopic("PostHog analytics", new Goal("target-roles", null));
    private final LocalDate today = LocalDate.of(2026, 9, 21);

    @Test
    void fromTemplateForARoutineItemCopiesTheTemplatesRoutineItemAndLabel() {
        ScheduleTemplate template = ScheduleTemplate.forRoutineItem(DayOfWeek.MONDAY, routineItem, 60, 1, "Weight lifting");

        Task task = Task.fromTemplate(today, template, null);

        assertThat(task.getRoutineItem()).isEqualTo(routineItem);
        assertThat(task.getLearningTopic()).isNull();
        assertThat(task.getLabel()).isEqualTo("Weight lifting");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(task.getScheduleTemplate()).isEqualTo(template);
    }

    @Test
    void fromTemplateForARoutineItemRejectsAResolvedLearningTopic() {
        ScheduleTemplate template = ScheduleTemplate.forRoutineItem(DayOfWeek.MONDAY, routineItem, 60, 1, "Weight lifting");

        assertThatThrownBy(() -> Task.fromTemplate(today, template, learningTopic))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromTemplateForALearningSlotRequiresAResolvedLearningTopic() {
        ScheduleTemplate template = ScheduleTemplate.forLearningSlot(DayOfWeek.TUESDAY, 45, 1, "Goal-suggested topic");

        assertThatThrownBy(() -> Task.fromTemplate(today, template, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromTemplateForALearningSlotUsesTheResolvedTopic() {
        ScheduleTemplate template = ScheduleTemplate.forLearningSlot(DayOfWeek.TUESDAY, 45, 1, "Goal-suggested topic");

        Task task = Task.fromTemplate(today, template, learningTopic);

        assertThat(task.getLearningTopic()).isEqualTo(learningTopic);
        assertThat(task.getRoutineItem()).isNull();
    }

    @Test
    void adHocAllowsNoTargetAtAll() {
        Task task = Task.adHoc(today, "call the dentist", null, null);

        assertThat(task.getRoutineItem()).isNull();
        assertThat(task.getLearningTopic()).isNull();
        assertThat(task.getScheduleTemplate()).isNull();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);
    }

    @Test
    void adHocRejectsBothTargetsAtOnce() {
        assertThatThrownBy(() -> Task.adHoc(today, "x", routineItem, learningTopic))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateStatusChangesStatus() {
        Task task = Task.adHoc(today, "x", null, null);

        task.updateStatus(TaskStatus.IN_PROGRESS);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
    }
}
