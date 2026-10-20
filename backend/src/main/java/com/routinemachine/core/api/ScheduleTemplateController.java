package com.routinemachine.core.api;

import com.routinemachine.core.domain.RoutineItem;
import com.routinemachine.core.domain.ScheduleTemplate;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.RoutineItemRepository;
import com.routinemachine.core.repository.ScheduleTemplateRepository;
import com.routinemachine.core.repository.UserRepository;
import com.routinemachine.core.service.Ownership;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/schedule-templates")
public class ScheduleTemplateController {

    private final ScheduleTemplateRepository scheduleTemplateRepository;
    private final RoutineItemRepository routineItemRepository;
    private final UserRepository userRepository;

    public ScheduleTemplateController(
            ScheduleTemplateRepository scheduleTemplateRepository, RoutineItemRepository routineItemRepository,
            UserRepository userRepository) {
        this.scheduleTemplateRepository = scheduleTemplateRepository;
        this.routineItemRepository = routineItemRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<ScheduleTemplateResponse> list(
            @RequestParam Optional<DayOfWeek> dayOfWeek, @RequestParam Optional<Long> userId) {
        // Per-user scoping (docs/backend.md): userId given = that user's templates; omitted =
        // only the unowned (pre-account) ones, never another user's.
        List<ScheduleTemplate> templates;
        if (userId.isPresent()) {
            templates = dayOfWeek.map(day -> scheduleTemplateRepository.findByDayOfWeekAndUserId(day, userId.get()))
                    .orElseGet(() -> scheduleTemplateRepository.findByUserId(userId.get()));
        } else {
            templates = dayOfWeek.map(scheduleTemplateRepository::findByDayOfWeekAndUserIsNull)
                    .orElseGet(scheduleTemplateRepository::findByUserIsNull);
        }
        return templates.stream().map(ScheduleTemplateResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduleTemplateResponse create(@Valid @RequestBody ScheduleTemplateRequest request) {
        User owner = request.userId() == null ? null : userRepository.findById(request.userId())
                .orElseThrow(() -> new IllegalArgumentException("No user with id " + request.userId()));
        ScheduleTemplate template = switch (request.targetType()) {
            case ROUTINE_ITEM -> {
                if (request.routineItemId() == null) {
                    throw new IllegalArgumentException("routineItemId is required when targetType is ROUTINE_ITEM");
                }
                RoutineItem item = findRoutineItem(request.routineItemId(), owner);
                yield ScheduleTemplate.forRoutineItem(
                        request.dayOfWeek(), item, request.targetDurationMinutes(), request.sortOrder(), request.label());
            }
            case LEARNING_SLOT -> {
                if (request.routineItemId() != null) {
                    throw new IllegalArgumentException("routineItemId must not be set when targetType is LEARNING_SLOT");
                }
                yield ScheduleTemplate.forLearningSlot(
                        request.dayOfWeek(), request.targetDurationMinutes(), request.sortOrder(), request.label());
            }
        };
        template.assignUser(owner);
        return ScheduleTemplateResponse.from(scheduleTemplateRepository.save(template));
    }

    // Someone else's routine item answers exactly like one that does not exist.
    private RoutineItem findRoutineItem(Long id, User owner) {
        return routineItemRepository.findById(id)
                .filter(found -> Ownership.sameOwner(found.getUser(), owner))
                .orElseThrow(() -> new IllegalArgumentException("No routine item with id " + id));
    }
}
