package com.routinemachine.core.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import com.routinemachine.core.service.OwnerAccount;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

// Phase 8 groundwork. Next.js owns the actual login session (password form + GitHub OAuth,
// see docs/backend.md's decisions log); this backend only verifies the short-lived, signed
// token Next.js's server attaches to its own calls, using a secret shared by both
// (AUTH_JWT_SECRET). Deliberately permissive for now: every existing endpoint stays
// permitAll, exactly as before this phase, since no table but `users` is scoped to a user
// yet. Only the one proof-of-concept protected endpoint (GET /api/users/me) requires a
// valid token, to prove the verification wiring actually works end to end.
//
// Two separate filter chains, not one: several existing endpoints
// (MeasurementIngestController, AuthController's github-login) already read their own
// "Authorization: Bearer <app-level token>" header. A single chain with
// oauth2ResourceServer().jwt() intercepts *any* Bearer header globally and 401s on one that
// isn't a valid JWT, even on a permitAll path, since presenting a bearer token is itself
// treated as an authentication attempt that must succeed. Scoping the JWT filter to only
// /api/users/** keeps it out of every other endpoint's own Bearer-token handling.
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // Every route that needs a valid token, and the routes where a token is optional. The API documentation reads these
    // (ApiDocsConfig) so the 401 it documents is on exactly the routes the chains refuse, and a test compares them.
    public static final String[] TOKEN_REQUIRED_PATTERNS = {
            "/api/users/**", "/api/granary/**", "/api/goals/**", "/api/routine-items/**",
            "/api/learning-topics/**", "/api/daily-logs/**", "/api/tasks/**", "/api/schedule-templates/**", "/api/books/**", "/api/reading-notes/**",
            "/api/current-reading/**", "/api/watering-holes/**", "/api/evidence-events/**",
            "/api/organisations/**"};
    public static final String[] OPTIONAL_TOKEN_PATTERNS = {"/api/hillside/**", "/api/watchtower/**"};


    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtDecoder jwtDecoder(@Value("${auth.jwt-secret}") String secret) {
        SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).build();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain jwtProtectedChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(TOKEN_REQUIRED_PATTERNS)
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));
        return http.build();
    }

    // The hillside is a public ranking anyone may read. A valid token only adds "you" to it, and a
    // bad one is refused (401) rather than quietly treated as a visitor. Nothing here is user-scoped
    // without that token.
    // The owner-only operations page (/api/watchtower) shares this chain on purpose: a signed-out visitor must
    // reach the controller, which answers 404 to everyone but the owner. On the token-required chain they
    // would get 401, which would tell them the page exists. A bad token is still refused (401), as above.
    // The API documentation (springdoc's /v3/api-docs and Swagger UI, staged Prompt 4) shares it for the same reason:
    // OwnerOnlyDocsFilter answers 404 to everyone but the owner.
    @Bean
    @Order(2)
    public SecurityFilterChain optionalTokenChain(HttpSecurity http, OwnerAccount ownerAccount) throws Exception {
        http
                .securityMatcher(concat(OPTIONAL_TOKEN_PATTERNS, OwnerOnlyDocsFilter.PATTERNS))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
                .addFilterAfter(new OwnerOnlyDocsFilter(ownerAccount), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    private static String[] concat(String[] first, String[] second) {
        String[] all = java.util.Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, all, first.length, second.length);
        return all;
    }

    @Bean
    @Order(3)
    public SecurityFilterChain defaultChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
