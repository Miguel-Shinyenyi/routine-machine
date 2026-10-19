package com.routinemachine.core.api;

import com.routinemachine.core.domain.Cohort;

import java.time.LocalDate;

public record CohortResponse(Long id, Long organisationId, String name, LocalDate startsOn, int weeks) {
    public static CohortResponse from(Cohort cohort) {
        return new CohortResponse(
                cohort.getId(), cohort.getOrganisationId(), cohort.getName(), cohort.getStartsOn(), cohort.getWeeks());
    }
}
