package com.routinemachine.core.api;

import com.routinemachine.core.domain.Goal;
import com.routinemachine.core.domain.RoutineItem;
import com.routinemachine.core.repository.GoalRepository;
import com.routinemachine.core.repository.RoutineItemRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/routine-items")
public class RoutineItemController {

    private final RoutineItemRepository routineItemRepository;
    private final GoalRepository goalRepository;

    public RoutineItemController(RoutineItemRepository routineItemRepository, GoalRepository goalRepository) {
        this.routineItemRepository = routineItemRepository;
        this.goalRepository = goalRepository;
    }

    @GetMapping
    public List<RoutineItemResponse> list() {
        return routineItemRepository.findAllWithGoal().stream().map(RoutineItemResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoutineItemResponse create(@Valid @RequestBody RoutineItemRequest request) {
        Goal goal = request.goalId() == null ? null : goalRepository.findById(request.goalId())
                .orElseThrow(() -> new IllegalArgumentException("No goal with id " + request.goalId()));

        RoutineItem saved = routineItemRepository.save(new RoutineItem(
                request.name(),
                request.description(),
                request.category(),
                request.countRule(),
                request.source(),
                request.hillsideField(),
                request.privateOrDefault(),
                goal));

        return RoutineItemResponse.from(
                routineItemRepository.findWithGoalById(saved.getId()).orElseThrow());
    }
}
