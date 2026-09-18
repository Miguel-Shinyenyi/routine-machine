package com.routinemachine.core.api;

import com.routinemachine.core.domain.Goal;
import com.routinemachine.core.domain.LearningTopic;
import com.routinemachine.core.repository.GoalRepository;
import com.routinemachine.core.repository.LearningTopicRepository;
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
@RequestMapping("/api/learning-topics")
public class LearningTopicController {

    private final LearningTopicRepository learningTopicRepository;
    private final GoalRepository goalRepository;

    public LearningTopicController(LearningTopicRepository learningTopicRepository, GoalRepository goalRepository) {
        this.learningTopicRepository = learningTopicRepository;
        this.goalRepository = goalRepository;
    }

    @GetMapping
    public List<LearningTopicResponse> list(@RequestParam Optional<Long> goalId) {
        List<LearningTopic> topics = goalId
                .map(learningTopicRepository::findByGoalIdWithGoal)
                .orElseGet(learningTopicRepository::findAllWithGoal);
        return topics.stream().map(LearningTopicResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LearningTopicResponse create(@Valid @RequestBody LearningTopicRequest request) {
        Goal goal = goalRepository.findById(request.goalId())
                .orElseThrow(() -> new IllegalArgumentException("No goal with id " + request.goalId()));
        LearningTopic saved = learningTopicRepository.save(new LearningTopic(request.name(), goal));
        return LearningTopicResponse.from(saved);
    }
}
