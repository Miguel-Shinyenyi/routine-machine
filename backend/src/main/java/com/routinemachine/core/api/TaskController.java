package com.routinemachine.core.api;

import com.routinemachine.core.domain.LearningTopic;
import com.routinemachine.core.domain.RoutineItem;
import com.routinemachine.core.domain.ScheduleTargetType;
import com.routinemachine.core.domain.ScheduleTemplate;
import com.routinemachine.core.domain.Task;
import com.routinemachine.core.domain.TaskStatus;
import com.routinemachine.core.repository.LearningTopicRepository;
import com.routinemachine.core.repository.RoutineItemRepository;
import com.routinemachine.core.repository.ScheduleTemplateRepository;
import com.routinemachine.core.repository.TaskRepository;
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

    public TaskController(
            TaskRepository taskRepository,
            ScheduleTemplateRepository scheduleTemplateRepository,
            RoutineItemRepository routineItemRepository,
            LearningTopicRepository learningTopicRepository,
            TaskService taskService) {
        this.taskRepository = taskRepository;
        this.scheduleTemplateRepository = scheduleTemplateRepository;
        this.routineItemRepository = routineItemRepository;
        this.learningTopicRepository = learningTopicRepository;
        this.taskService = taskService;
    }

    @GetMapping
    public List<TaskResponse> list(@RequestParam Optional<LocalDate> date) {
        List<Task> tasks = date.map(taskRepository::findByTaskDateWithTargets).orElseGet(taskRepository::findAllWithTargets);
        return tasks.stream().map(TaskResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(@Valid @RequestBody TaskRequest request) {
        Task task = request.scheduleTemplateId() != null
                ? materializeFromTemplate(request)
                : createAdHoc(request);
        return TaskResponse.from(taskRepository.save(task));
    }

    @PatchMapping("/{id}")
    public TaskResponse updateStatus(@PathVariable Long id, @Valid @RequestBody TaskStatusUpdateRequest request) {
        TaskStatus status = parseStatus(request.status());
        return TaskResponse.from(taskService.changeStatus(id, status));
    }

    private Task materializeFromTemplate(TaskRequest request) {
        ScheduleTemplate template = scheduleTemplateRepository.findById(request.scheduleTemplateId())
                .orElseThrow(() -> new IllegalArgumentException("No schedule template with id " + request.scheduleTemplateId()));

        LearningTopic resolvedTopic = null;
        if (template.getTargetType() == ScheduleTargetType.LEARNING_SLOT) {
            if (request.learningTopicId() == null) {
                throw new IllegalArgumentException("learningTopicId is required to materialize a LEARNING_SLOT task");
            }
            resolvedTopic = findLearningTopic(request.learningTopicId());
        }
        return Task.fromTemplate(request.taskDate(), template, resolvedTopic);
    }

    private Task createAdHoc(TaskRequest request) {
        if (request.label() == null || request.label().isBlank()) {
            throw new IllegalArgumentException("label is required for an ad-hoc task");
        }
        RoutineItem routineItem = request.routineItemId() == null ? null : findRoutineItem(request.routineItemId());
        LearningTopic learningTopic = request.learningTopicId() == null ? null : findLearningTopic(request.learningTopicId());
        return Task.adHoc(request.taskDate(), request.label(), routineItem, learningTopic);
    }

    private TaskStatus parseStatus(String value) {
        try {
            return TaskStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown task status: " + value);
        }
    }

    private RoutineItem findRoutineItem(Long id) {
        return routineItemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No routine item with id " + id));
    }

    private LearningTopic findLearningTopic(Long id) {
        return learningTopicRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No learning topic with id " + id));
    }
}
