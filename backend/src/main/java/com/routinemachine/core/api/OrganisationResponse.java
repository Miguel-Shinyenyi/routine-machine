package com.routinemachine.core.api;

import com.routinemachine.core.domain.Organisation;

public record OrganisationResponse(Long id, String name) {
    public static OrganisationResponse from(Organisation organisation) {
        return new OrganisationResponse(organisation.getId(), organisation.getName());
    }
}
