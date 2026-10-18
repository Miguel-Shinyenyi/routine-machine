package com.routinemachine.core.service;

import java.util.List;

public record HillsideResult(
        int totalParticipants,
        List<Integer> histogram,
        Integer yourDaysShownUp,
        Integer yourRank,
        String band,
        List<NeighborRow> neighbors) {
}
