package com.routinemachine.core.api;

import com.routinemachine.core.repository.UserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
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

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new NoSuchElementException("No user with id " + userId));
    }
}
