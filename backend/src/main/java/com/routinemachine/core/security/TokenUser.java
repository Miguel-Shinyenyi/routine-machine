package com.routinemachine.core.security;

import org.springframework.security.oauth2.jwt.Jwt;

// Who a signed token is for. The subject is the user id, set by the frontend's server when the
// person signs in. This is the only place a route should learn who is asking: never from a
// request body or query parameter, which the caller controls.
public final class TokenUser {

    private TokenUser() {
    }

    public static Long idOf(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
