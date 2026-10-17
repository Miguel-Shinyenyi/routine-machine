package com.routinemachine.core.api;

import jakarta.validation.constraints.NotBlank;

public record GithubLoginRequest(
        @NotBlank String githubId,
        @NotBlank String email,
        String displayName) {
}
