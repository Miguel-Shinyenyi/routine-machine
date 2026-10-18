package com.routinemachine.core.api;

import com.routinemachine.core.service.HillsideResult;
import com.routinemachine.core.service.NeighborRow;

import java.util.List;

public record HillsideResponse(
        int totalParticipants,
        List<Integer> histogram,
        Integer yourDaysShownUp,
        Integer yourRank,
        String band,
        List<NeighborRowResponse> neighbors) {

    public record NeighborRowResponse(int rank, String pseudonym, int daysShownUp, boolean isYou) {
        static NeighborRowResponse from(NeighborRow row) {
            return new NeighborRowResponse(row.rank(), row.pseudonym(), row.daysShownUp(), row.isYou());
        }
    }

    public static HillsideResponse from(HillsideResult result) {
        return new HillsideResponse(
                result.totalParticipants(),
                result.histogram(),
                result.yourDaysShownUp(),
                result.yourRank(),
                result.band(),
                result.neighbors().stream().map(NeighborRowResponse::from).toList());
    }
}
