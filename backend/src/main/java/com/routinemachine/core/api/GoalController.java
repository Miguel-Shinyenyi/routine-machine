package com.routinemachine.core.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.routinemachine.core.security.TokenUser;
import com.routinemachine.core.domain.Goal;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.GoalRepository;
import com.routinemachine.core.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/goals")
public class GoalController {

    private final GoalRepository goalRepository;
    private final UserRepository userRepository;

    public GoalController(GoalRepository goalRepository, UserRepository userRepository) {
        this.goalRepository = goalRepository;
        this.userRepository = userRepository;
    }

    // The signed-in person's own goals. Who that is comes from the token, never from a parameter
    // (docs/backend.md, access control).
    @Operation(summary = "List goals")
    @GetMapping
    public List<GoalResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return goalRepository.findByUserId(TokenUser.idOf(jwt)).stream().map(GoalResponse::from).toList();
    }

    @Operation(summary = "Create a goal")
    @ApiResponse(responseCode = "400", description = "A goal with that name already exists. A required field, parameter or path value is missing or cannot be read.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GoalResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody GoalRequest request) {
        Long userId = TokenUser.idOf(jwt);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("No user with id " + userId));
        if (goalRepository.existsByUserIdAndName(userId, request.name())) {
            throw new IllegalArgumentException(
                    "A goal named '" + request.name() + "' already exists for this user");
        }

        Goal saved = goalRepository.save(new Goal(request.name(), request.description(), user));
        return GoalResponse.from(saved);
    }
}
