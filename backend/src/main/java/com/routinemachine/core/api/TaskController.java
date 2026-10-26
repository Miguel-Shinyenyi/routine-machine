package com.routinemachine.core.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.routinemachine.core.security.TokenUser;
import com.routinemachine.core.domain.LearningTopic;
import com.routinemachine.core.domain.RoutineItem;
import com.routinemachine.core.domain.ScheduleTargetType;
import com.routinemachine.core.domain.ScheduleTemplate;
import com.routinemachine.core.domain.Task;
import com.routinemachine.core.domain.TaskStatus;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.LearningTopicRepository;
import com.routinemachine.core.repository.RoutineItemRepository;
import com.routinemachine.core.repository.ScheduleTemplateRepository;
import com.routinemachine.core.repository.TaskRepository;
import com.routinemachine.core.repository.UserRepository;
import com.routinemachine.core.service.Ownership;
import com.routinemachine.core.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskRepository taskRepository;
    private final ScheduleTemplateRepository scheduleTemplateRepository;
    private final RoutineItemRepository routineItemRepository;
    private final LearningTopicRepository learningTopicRepository;
    private final TaskService taskService;
    private final UserRepository userRepository;

    public TaskController(
            TaskRepository taskRepository,
            ScheduleTemplateRepository scheduleTemplateRepository,
            RoutineItemRepository routineItemRepository,
            LearningTopicRepository learningTopicRepository,
            TaskService taskService,
            UserRepository userRepository) {
        this.taskRepository = taskRepository;
        this.scheduleTemplateRepository = scheduleTemplateRepository;
        this.routineItemRepository = routineItemRepository;
        this.learningTopicRepository = learningTopicRepository;
        this.taskService = taskService;
        this.userRepository = userRepository;
    }

    // The signed-in person's own tasks, for one date when date is given. Who that is comes from the token, never from a
    // parameter (docs/backend.md, access control).
    @Operation(summary = "List tasks")
    @ApiResponse(responseCode = "400", description = "A required field, parameter or path value is missing or cannot be read.")
    @GetMapping
    public List<TaskResponse> list(@AuthenticationPrincipal Jwt jwt, @RequestParam Optional<LocalDate> date) {
        Long userId = TokenUser.idOf(jwt);
        List<Task> tasks = date.map(d -> taskRepository.findByTaskDateAndUserId(d, userId))
                .orElseGet(() -> taskRepository.findByUserId(userId));
        return tasks.stream().map(TaskResponse::from).toList();
    }

    @Operation(summary = "Create a task")
    @ApiResponse(responseCode = "400", description = "A learning-slot template's task needs a resolved learning topic for the day. A routine-item template's task can't be given a resolved learning topic. An ad-hoc task can target at most one of routineItem or learningTopic. There is no learning topic with that id, or it belongs to someone else. There is no routine item with that id, or it belongs to someone else. There is no schedule template with that id, or it belongs to someone else. label is required for an ad-hoc task. learningTopicId is required to materialize a LEARNING_SLOT task. A required field, parameter or path value is missing or cannot be read.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TaskRequest request) {
        Long userId = TokenUser.idOf(jwt);
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("No user with id " + userId));
        Task task = request.scheduleTemplateId() != null
                ? materializeFromTemplate(request, owner)
                : createAdHoc(request, owner);
        task.assignUser(owner);
        return TaskResponse.from(taskRepository.save(task));
    }

    @Operation(summary = "Change a task's status")
    @ApiResponse(responseCode = "400", description = "Unknown task status. A required field, parameter or path value is missing or cannot be read.")
    @ApiResponse(responseCode = "404", description = "There is no task with that id, or it belongs to someone else.")
    @PatchMapping("/{id}")
    public TaskResponse updateStatus(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @Valid @RequestBody TaskStatusUpdateRequest request) {
        TaskStatus status = parseStatus(request.status());
        return TaskResponse.from(taskService.changeStatus(id, status, TokenUser.idOf(jwt)));
    }

    private Task materializeFromTemplate(TaskRequest request, User owner) {
        // Someone else's template answers exactly like one that does not exist.
        ScheduleTemplate template = scheduleTemplateRepository.findById(request.scheduleTemplateId())
                .filter(found -> Ownership.sameOwner(found.getUser(), owner))
                .orElseThrow(() -> new IllegalArgumentException("No schedule template with id " + request.scheduleTemplateId()));

        LearningTopic resolvedTopic = null;
        if (template.getTargetType() == ScheduleTargetType.LEARNING_SLOT) {
            if (request.learningTopicId() == null) {
                throw new IllegalArgumentException("learningTopicId is required to materialize a LEARNING_SLOT task");
            }
            resolvedTopic = findLearningTopic(request.learningTopicId(), owner);
        }
        return Task.fromTemplate(request.taskDate(), template, resolvedTopic);
    }

    private Task createAdHoc(TaskRequest request, User owner) {
        if (request.label() == null || request.label().isBlank()) {
            throw new IllegalArgumentException("label is required for an ad-hoc task");
        }
        RoutineItem routineItem = request.routineItemId() == null ? null : findRoutineItem(request.routineItemId(), owner);
        LearningTopic learningTopic = request.learningTopicId() == null ? null : findLearningTopic(request.learningTopicId(), owner);
        return Task.adHoc(request.taskDate(), request.label(), routineItem, learningTopic);
    }

    private TaskStatus parseStatus(String value) {
        try {
            return TaskStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown task status: " + value);
        }
    }

    // A task can only point at its own owner's routine items and topics; anyone else's answers
    // exactly like one that does not exist.
    private RoutineItem findRoutineItem(Long id, User owner) {
        return routineItemRepository.findById(id)
                .filter(found -> Ownership.sameOwner(found.getUser(), owner))
                .orElseThrow(() -> new IllegalArgumentException("No routine item with id " + id));
    }

    private LearningTopic findLearningTopic(Long id, User owner) {
        return learningTopicRepository.findWithGoalAndOwnerById(id)
                .filter(found -> Ownership.sameOwner(found.getGoal().getUser(), owner))
                .orElseThrow(() -> new IllegalArgumentException("No learning topic with id " + id));
    }
}
