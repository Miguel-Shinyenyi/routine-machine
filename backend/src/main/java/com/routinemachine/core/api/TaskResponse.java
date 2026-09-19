package com.routinemachine.core.api;

import com.routinemachine.core.domain.Task;

import java.time.LocalDate;

public record TaskResponse(
        Long id,
        Long scheduleTemplateId,
        LocalDate taskDate,
        String label,
        Long routineItemId,
        Long learningTopicId,
        String status,
        Long dailyLogId) {

    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getScheduleTemplate() == null ? null : task.getScheduleTemplate().getId(),
                task.getTaskDate(),
                task.getLabel(),
                task.getRoutineItem() == null ? null : task.getRoutineItem().getId(),
                task.getLearningTopic() == null ? null : task.getLearningTopic().getId(),
                task.getStatus().name(),
                task.getDailyLog() == null ? null : task.getDailyLog().getId());
    }
}
