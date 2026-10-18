package com.routinemachine.core.service;

import java.util.List;

public record HillsideV2Result(
        int totalParticipants,
        Integer yourScore,
        Integer yourDaysShownUp,
        Integer yourRank,
        Integer yourGoalCount,
        String band,
        int cohortReturns,
        List<NeighborCreditRow> neighbors) {
}
