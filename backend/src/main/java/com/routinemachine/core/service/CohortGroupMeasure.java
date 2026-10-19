package com.routinemachine.core.service;

// One row of the "by study group" breakdown. When hidden is true (fewer than 5 members —
// "small groups stay hidden," docs/product-direction.md), checkedInThisWeek and
// returnedAfterAMissedDay are null: the member count is still shown, but no one can be picked
// out from a redacted rate.
public record CohortGroupMeasure(
        String studyGroup, int memberCount, boolean hidden, CohortMeasure checkedInThisWeek,
        CohortMeasure returnedAfterAMissedDay) {
}
