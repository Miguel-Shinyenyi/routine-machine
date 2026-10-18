package com.routinemachine.core.api;

import com.routinemachine.core.service.HillsideV2Result;
import com.routinemachine.core.service.NeighborCreditRow;

import java.util.List;

public record HillsideV2Response(
        int totalParticipants,
        Integer yourScore,
        Integer yourDaysShownUp,
        Integer yourRank,
        Integer yourGoalCount,
        String band,
        int cohortReturns,
        List<NeighborCreditRowResponse> neighbors) {

    public record NeighborCreditRowResponse(int rank, String pseudonym, int credits, int daysShownUp, boolean isYou) {
        static NeighborCreditRowResponse from(NeighborCreditRow row) {
            return new NeighborCreditRowResponse(row.rank(), row.pseudonym(), row.credits(), row.daysShownUp(), row.isYou());
        }
    }

    public static HillsideV2Response from(HillsideV2Result result) {
        return new HillsideV2Response(
                result.totalParticipants(),
                result.yourScore(),
                result.yourDaysShownUp(),
                result.yourRank(),
                result.yourGoalCount(),
                result.band(),
                result.cohortReturns(),
                result.neighbors().stream().map(NeighborCreditRowResponse::from).toList());
    }
}
