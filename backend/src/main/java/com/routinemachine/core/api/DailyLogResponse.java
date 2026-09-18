package com.routinemachine.core.api;

import com.routinemachine.core.domain.DailyLog;

import java.time.LocalDate;

public record DailyLogResponse(Long id, LocalDate logDate, Long routineItemId, Long learningTopicId, String source) {

    public static DailyLogResponse from(DailyLog log) {
        return new DailyLogResponse(
                log.getId(),
                log.getLogDate(),
                log.getRoutineItem() == null ? null : log.getRoutineItem().getId(),
                log.getLearningTopic() == null ? null : log.getLearningTopic().getId(),
                log.getSource().getValue());
    }
}
