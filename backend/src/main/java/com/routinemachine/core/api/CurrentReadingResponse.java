package com.routinemachine.core.api;

import com.routinemachine.core.domain.CurrentReadingLog;

public record CurrentReadingResponse(Long id, String title, String source) {

    public static CurrentReadingResponse from(CurrentReadingLog log) {
        return new CurrentReadingResponse(log.getId(), log.getTitle(), log.getSource().getValue());
    }
}
