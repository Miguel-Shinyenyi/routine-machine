package com.routinemachine.core.api;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.routinemachine.core.security.TokenUser;
import com.routinemachine.core.domain.Goal;
import com.routinemachine.core.domain.LearningTopic;
import com.routinemachine.core.repository.GoalRepository;
import com.routinemachine.core.service.Ownership;
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

    @Operation(summary = "List learning topics")
    @GetMapping
    public List<LearningTopicResponse> list(@AuthenticationPrincipal Jwt jwt, @RequestParam Optional<Long> goalId) {
        // A topic belongs to whoever owns its goal.
        Long userId = TokenUser.idOf(jwt);
        List<LearningTopic> topics = goalId.map(id -> learningTopicRepository.findByGoalIdAndOwnerWithGoal(id, userId))
                .orElseGet(() -> learningTopicRepository.findByOwnerWithGoal(userId));
        return topics.stream().map(LearningTopicResponse::from).toList();
    }

    @Operation(summary = "Create a learning topic")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LearningTopicResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody LearningTopicRequest request) {
        // Someone else's goal answers exactly like one that does not exist.
        Goal goal = goalRepository.findById(request.goalId())
                .filter(found -> Ownership.ownedBy(found.getUser(), TokenUser.idOf(jwt)))
                .orElseThrow(() -> new IllegalArgumentException("No goal with id " + request.goalId()));
        LearningTopic saved = learningTopicRepository.save(new LearningTopic(request.name(), goal));
        return LearningTopicResponse.from(saved);
    }
}
