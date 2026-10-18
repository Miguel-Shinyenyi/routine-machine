package com.routinemachine.core.api;

import com.routinemachine.core.domain.Goal;
import com.routinemachine.core.domain.RoutineItem;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.GoalRepository;
import com.routinemachine.core.repository.RoutineItemRepository;
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
@RequestMapping("/api/routine-items")
public class RoutineItemController {

    private final RoutineItemRepository routineItemRepository;
    private final GoalRepository goalRepository;
    private final UserRepository userRepository;

    public RoutineItemController(
            RoutineItemRepository routineItemRepository, GoalRepository goalRepository,
            UserRepository userRepository) {
        this.routineItemRepository = routineItemRepository;
        this.goalRepository = goalRepository;
        this.userRepository = userRepository;
    }

    // userId is optional and additive (Phase 8 groundwork, see docs/backend.md): omitted,
    // this returns every legacy, unowned item exactly as before this phase. Passed, it scopes
    // to that one user's own items instead, for testing the new per-user data alongside the
    // untouched single-user behaviour everything else still relies on.
    @GetMapping
    public List<RoutineItemResponse> list(@RequestParam Optional<Long> userId) {
        List<RoutineItem> items = userId
                .map(routineItemRepository::findAllWithGoalByUserId)
                .orElseGet(routineItemRepository::findAllWithGoal);
        return items.stream().map(RoutineItemResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoutineItemResponse create(@Valid @RequestBody RoutineItemRequest request) {
        Goal goal = request.goalId() == null ? null : goalRepository.findById(request.goalId())
                .orElseThrow(() -> new IllegalArgumentException("No goal with id " + request.goalId()));
        User user = request.userId() == null ? null : userRepository.findById(request.userId())
                .orElseThrow(() -> new IllegalArgumentException("No user with id " + request.userId()));

        RoutineItem item = new RoutineItem(
                request.name(),
                request.description(),
                request.category(),
                request.countRule(),
                request.source(),
                request.hillsideField(),
                request.privateOrDefault(),
                goal);
        item.assignUser(user);
        RoutineItem saved = routineItemRepository.save(item);

        return RoutineItemResponse.from(
                routineItemRepository.findWithGoalById(saved.getId()).orElseThrow());
    }
}
