package com.routinemachine.core.api;

import com.routinemachine.core.domain.User;

public record UserResponse(Long id, String email, String displayName, boolean hillsideOptIn) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.isHillsideOptIn());
    }
}
