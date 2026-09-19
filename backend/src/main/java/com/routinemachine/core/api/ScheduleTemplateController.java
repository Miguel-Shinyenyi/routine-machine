package com.routinemachine.core.api;

import com.routinemachine.core.domain.RoutineItem;
import com.routinemachine.core.domain.ScheduleTemplate;
import com.routinemachine.core.repository.RoutineItemRepository;
import com.routinemachine.core.repository.ScheduleTemplateRepository;
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

    public ScheduleTemplateController(
            ScheduleTemplateRepository scheduleTemplateRepository, RoutineItemRepository routineItemRepository) {
        this.scheduleTemplateRepository = scheduleTemplateRepository;
        this.routineItemRepository = routineItemRepository;
    }

    @GetMapping
    public List<ScheduleTemplateResponse> list(@RequestParam Optional<DayOfWeek> dayOfWeek) {
        List<ScheduleTemplate> templates = dayOfWeek
                .map(scheduleTemplateRepository::findByDayOfWeekWithRoutineItem)
                .orElseGet(scheduleTemplateRepository::findAllWithRoutineItem);
        return templates.stream().map(ScheduleTemplateResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduleTemplateResponse create(@Valid @RequestBody ScheduleTemplateRequest request) {
        ScheduleTemplate template = switch (request.targetType()) {
            case ROUTINE_ITEM -> {
                if (request.routineItemId() == null) {
                    throw new IllegalArgumentException("routineItemId is required when targetType is ROUTINE_ITEM");
                }
                RoutineItem item = findRoutineItem(request.routineItemId());
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
        return ScheduleTemplateResponse.from(scheduleTemplateRepository.save(template));
    }

    private RoutineItem findRoutineItem(Long id) {
        return routineItemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No routine item with id " + id));
    }
}
