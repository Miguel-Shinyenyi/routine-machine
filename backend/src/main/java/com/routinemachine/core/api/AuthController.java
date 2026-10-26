package com.routinemachine.core.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.service.AuthService;
import com.routinemachine.core.service.DemoProfileService;
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
    private final DemoProfileService demoProfileService;
    private final String internalToken;
    private final boolean demoEnabled;

    public AuthController(
            AuthService authService, OrganisationService organisationService, DemoProfileService demoProfileService,
            @Value("${auth.internal-token:}") String internalToken,
            @Value("${demo.enabled:false}") boolean demoEnabled) {
        this.authService = authService;
        this.organisationService = organisationService;
        this.demoProfileService = demoProfileService;
        this.internalToken = internalToken;
        this.demoEnabled = demoEnabled;
    }

    @Operation(summary = "Create an account with an email and a password")
    @ApiResponse(responseCode = "400", description = "Email is already registered. A required field, parameter or path value is missing or cannot be read.")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request.email(), request.password());
        organisationService.fulfillInvitesForNewUser(user);
        return UserResponse.from(user);
    }

    @Operation(summary = "Check an email and a password")
    @ApiResponse(responseCode = "400", description = "A required field, parameter or path value is missing or cannot be read.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "The email or the password is not right. The same answer for every kind of failure.")
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
    @Operation(summary = "Find or create the account for a GitHub sign-in (internal token only)")
    @ApiResponse(responseCode = "400", description = "A required field, parameter or path value is missing or cannot be read.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "The internal service token is missing, wrong or not configured.")
    @PostMapping("/github-login")
    public UserResponse githubLogin(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody GithubLoginRequest request) {
        requireValidInternalToken(authorizationHeader);
        User user = authService.findOrCreateFromGithub(request.githubId(), request.email(), request.displayName());
        organisationService.fulfillInvitesForNewUser(user);
        return UserResponse.from(user);
    }

    // Off unless DEMO_PROFILE_ENABLED is set: then it does not exist at all (404), not "forbidden". Like github-login,
    // only Next.js's own server may call it, with the internal token. See docs/backend.md, "Demo profile".
    @Operation(summary = "Open the sample demo account (only when the demo profile is switched on; internal token only)")
    @ApiResponse(responseCode = "404", description = "The demo profile is not switched on.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "The internal service token is missing, wrong or not configured.")
    @PostMapping("/demo-login")
    public UserResponse demoLogin(@RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        if (!demoEnabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "the demo profile is not switched on");
        }
        requireValidInternalToken(authorizationHeader);
        return UserResponse.from(demoProfileService.open());
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
