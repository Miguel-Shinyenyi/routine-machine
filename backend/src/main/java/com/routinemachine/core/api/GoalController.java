package com.routinemachine.core.api;

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

    // userId is optional and additive (Phase 8 groundwork, see docs/backend.md): omitted,
    // this returns every goal exactly as before this phase. Passed, it scopes to that one
    // user's own goals, matching RoutineItemController/DailyLogController's pattern.
    @GetMapping
    public List<GoalResponse> list(@RequestParam Optional<Long> userId) {
        // Per-user scoping (docs/backend.md): userId omitted = only unowned goals, not every account's.
        List<Goal> goals = userId.map(goalRepository::findByUserId).orElseGet(goalRepository::findByUserIsNull);
        return goals.stream().map(GoalResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GoalResponse create(@Valid @RequestBody GoalRequest request) {
        User user = request.userId() == null ? null : userRepository.findById(request.userId())
                .orElseThrow(() -> new IllegalArgumentException("No user with id " + request.userId()));
        if (goalRepository.existsByUserIdAndName(request.userId(), request.name())) {
            throw new IllegalArgumentException(
                    "A goal named '" + request.name() + "' already exists for this user");
        }

        Goal saved = goalRepository.save(new Goal(request.name(), request.description(), user));
        return GoalResponse.from(saved);
    }
}
