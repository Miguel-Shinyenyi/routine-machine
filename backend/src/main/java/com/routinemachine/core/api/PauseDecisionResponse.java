package com.routinemachine.core.api;

import com.routinemachine.core.domain.PauseDecision;
import com.routinemachine.core.domain.PauseOutcome;

import java.time.Instant;

public record PauseDecisionResponse(
        Long id,
        String what,
        String amount,
        String why,
        String heardFrom,
        PauseOutcome outcome,
        Instant decidedAt,
        Instant followUp24hAt,
        Instant followUp30dAt,
        String reflectionThirtyDay,
        boolean dueForTwentyFourHourFollowUp,
        boolean dueForThirtyDayFollowUp) {

    public static PauseDecisionResponse from(PauseDecision pause, Instant now) {
        return new PauseDecisionResponse(
                pause.getId(), pause.getWhat(), pause.getAmount(), pause.getWhy(), pause.getHeardFrom(),
                pause.getOutcome(), pause.getDecidedAt(), pause.getFollowUp24hAt(), pause.getFollowUp30dAt(),
                pause.getReflectionThirtyDay(), pause.isDueForTwentyFourHourFollowUp(now),
                pause.isDueForThirtyDayFollowUp(now));
    }
}
