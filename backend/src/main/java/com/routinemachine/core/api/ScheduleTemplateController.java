package com.routinemachine.core.api;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.routinemachine.core.security.TokenUser;
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

    @Operation(summary = "List schedule templates")
    @GetMapping
    public List<ScheduleTemplateResponse> list(
            @AuthenticationPrincipal Jwt jwt, @RequestParam Optional<DayOfWeek> dayOfWeek) {
        Long userId = TokenUser.idOf(jwt);
        List<ScheduleTemplate> templates = dayOfWeek
                .map(day -> scheduleTemplateRepository.findByDayOfWeekAndUserId(day, userId))
                .orElseGet(() -> scheduleTemplateRepository.findByUserId(userId));
        return templates.stream().map(ScheduleTemplateResponse::from).toList();
    }

    @Operation(summary = "Create a schedule template")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduleTemplateResponse create(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ScheduleTemplateRequest request) {
        Long userId = TokenUser.idOf(jwt);
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("No user with id " + userId));
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
