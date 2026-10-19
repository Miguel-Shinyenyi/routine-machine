package com.routinemachine.core.api;

import com.routinemachine.core.domain.User;
import com.routinemachine.core.service.AuthService;
import com.routinemachine.core.service.OrganisationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// Phase 8 groundwork: account creation and credential verification only. Next.js is the one
// that issues the actual session and the signed token used against the rest of this API (see
// docs/backend.md's decisions log) — these endpoints just answer "is this a valid account,"
// nothing here sets a cookie or mints a token itself.
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;
    private final OrganisationService organisationService;
    private final String internalToken;

    public AuthController(
            AuthService authService, OrganisationService organisationService,
            @Value("${auth.internal-token:}") String internalToken) {
        this.authService = authService;
        this.organisationService = organisationService;
        this.internalToken = internalToken;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request.email(), request.password());
        organisationService.fulfillInvitesForNewUser(user);
        return UserResponse.from(user);
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password())
                .map(UserResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid email or password"));
    }

    // Only Next.js's own server should ever call this: it trusts the caller already verified
    // the GitHub identity through a real OAuth flow, so it needs its own proof, not a user's
    // password, that the caller is allowed to assert "this GitHub account belongs to this
    // email." Fails closed with no internal token configured, same rule
    // MeasurementIngestController applies to HEALTH_INGEST_TOKEN.
    @PostMapping("/github-login")
    public UserResponse githubLogin(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody GithubLoginRequest request) {
        requireValidInternalToken(authorizationHeader);
        User user = authService.findOrCreateFromGithub(request.githubId(), request.email(), request.displayName());
        organisationService.fulfillInvitesForNewUser(user);
        return UserResponse.from(user);
    }

    private void requireValidInternalToken(String authorizationHeader) {
        if (internalToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "internal service token is not configured");
        }
        String presentedToken = authorizationHeader != null && authorizationHeader.startsWith(BEARER_PREFIX)
                ? authorizationHeader.substring(BEARER_PREFIX.length())
                : null;
        if (!internalToken.equals(presentedToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid or missing internal service token");
        }
    }
}
