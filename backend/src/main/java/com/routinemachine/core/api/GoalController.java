package com.routinemachine.core.api;

import com.routinemachine.core.repository.GoalRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class GoalController {

    private final GoalRepository goalRepository;

    public GoalController(GoalRepository goalRepository) {
        this.goalRepository = goalRepository;
    }

    @GetMapping("/api/goals")
    public List<GoalResponse> list() {
        return goalRepository.findAll().stream().map(GoalResponse::from).toList();
    }
}
