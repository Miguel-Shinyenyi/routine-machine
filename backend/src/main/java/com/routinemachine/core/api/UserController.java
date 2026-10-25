package com.routinemachine.core.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.UserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

// GET /api/users/me is the one endpoint this phase actually requires a valid token for (see
// SecurityConfig) — proof the JWT verification wiring works end to end, not itself a feature
// anything calls yet.
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Operation(summary = "Get the signed-in account")
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return currentUser(jwt);
    }

    // The hillside should never enroll anyone who hasn't explicitly agreed, even if they
    // already have non-private routine items (see DailyLogRepository.findForHillside).
    @Operation(summary = "Join or leave the hillside")
    @ApiResponse(responseCode = "400", description = "A required field, parameter or path value is missing or cannot be read.")
    @PutMapping("/me/hillside-opt-in")
    public UserResponse setHillsideOptIn(@AuthenticationPrincipal Jwt jwt, @RequestBody HillsideOptInRequest request) {
        User user = findUser(jwt);
        if (request.optIn()) {
            user.optIntoHillside();
        } else {
            user.optOutOfHillside();
        }
        return UserResponse.from(userRepository.save(user));
    }

    // Phase 10: interests, thinkers, learning preferences, currency, time zone, day-end hour,
    // and the analogies switch — see docs/product-direction.md's Profile section. Its own
    // endpoint, not folded into GET/PUT .../me, so nothing cohort- or admin-facing can ever
    // read this data just by calling the same endpoint something else already calls.
    @Operation(summary = "Get the signed-in person's profile")
    @GetMapping("/me/profile")
    public ProfileResponse profile(@AuthenticationPrincipal Jwt jwt) {
        return ProfileResponse.from(findUser(jwt));
    }

    @Operation(summary = "Update the signed-in person's profile")
    @ApiResponse(responseCode = "400", description = "A required field, parameter or path value is missing or cannot be read.")
    @PutMapping("/me/profile")
    public ProfileResponse updateProfile(@AuthenticationPrincipal Jwt jwt, @RequestBody ProfileRequest request) {
        User user = findUser(jwt);
        user.updateProfile(
                request.interests(), request.thinkers(), request.learningPreferences(), request.currency(),
                request.dayEndHour(), request.timeZone(), request.analogiesEnabled());
        return ProfileResponse.from(userRepository.save(user));
    }

    private UserResponse currentUser(Jwt jwt) {
        return UserResponse.from(findUser(jwt));
    }

    private User findUser(Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        return userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("No user with id " + userId));
    }
}
