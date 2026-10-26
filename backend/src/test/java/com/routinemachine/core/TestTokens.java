package com.routinemachine.core;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

// A signed-in request, as the frontend's server would send it: a token whose subject is the user id.
public final class TestTokens {

    private TestTokens() {
    }

    public static RequestPostProcessor asUser(long userId) {
        return jwt().jwt(token -> token.subject(String.valueOf(userId)));
    }
}
