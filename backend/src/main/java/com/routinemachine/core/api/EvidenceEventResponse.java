package com.routinemachine.core.api;

import com.routinemachine.core.domain.DailyLogSource;
import com.routinemachine.core.domain.EvidenceEvent;
import com.routinemachine.core.domain.EvidenceStatus;
import com.routinemachine.core.domain.Goal;

import java.time.Instant;
import java.time.LocalDate;

public record EvidenceEventResponse(
        Long id,
        Long userId,
        Long goalId,
        String goalName,
        DailyLogSource source,
        String sourceEventId,
        Instant occurredAt,
        LocalDate localDate,
        Integer minutes,
        EvidenceStatus status,
        int scoringRuleVersion,
        String reason,
        boolean questioned) {

    // Goal fetched eagerly by the caller's own query (see EvidenceEventRepository's ledger
    // query) — safe to read event.getGoal().getName() here.
    public static EvidenceEventResponse from(EvidenceEvent event) {
        return from(event, event.getGoal());
    }

    // Takes the goal explicitly: open-in-view is off (see application.yml), so an event
    // returned by EvidenceCreditService.ingest outside a JOIN-FETCH query would otherwise
    // throw on event.getGoal().getName() against a closed session. The ingest controller
    // already has the Goal it looked up to build the request, so it passes that instead.
    public static EvidenceEventResponse from(EvidenceEvent event, Goal goal) {
        return new EvidenceEventResponse(
                event.getId(),
                event.getUser().getId(),
                goal.getId(),
                goal.getName(),
                event.getSource(),
                event.getSourceEventId(),
                event.getOccurredAt(),
                event.getLocalDate(),
                event.getMinutes(),
                event.getStatus(),
                event.getScoringRuleVersion(),
                event.getReason(),
                event.getQuestionedAt() != null);
    }
}
