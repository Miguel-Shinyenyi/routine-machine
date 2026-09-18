package com.routinemachine.core.api;

import com.routinemachine.core.domain.DailyLog;
import com.routinemachine.core.domain.DailyLogSource;
import com.routinemachine.core.domain.LearningTopic;
import com.routinemachine.core.domain.RoutineItem;
import com.routinemachine.core.repository.DailyLogRepository;
import com.routinemachine.core.repository.LearningTopicRepository;
import com.routinemachine.core.repository.RoutineItemRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping("/api/daily-logs")
public class DailyLogController {

    private final DailyLogRepository dailyLogRepository;
    private final RoutineItemRepository routineItemRepository;
    private final LearningTopicRepository learningTopicRepository;

    public DailyLogController(
            DailyLogRepository dailyLogRepository,
            RoutineItemRepository routineItemRepository,
            LearningTopicRepository learningTopicRepository) {
        this.dailyLogRepository = dailyLogRepository;
        this.routineItemRepository = routineItemRepository;
        this.learningTopicRepository = learningTopicRepository;
    }

    @GetMapping
    public List<DailyLogResponse> list(@RequestParam Optional<LocalDate> date) {
        List<DailyLog> logs = date.map(dailyLogRepository::findByLogDate).orElseGet(dailyLogRepository::findAll);
        return logs.stream().map(DailyLogResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DailyLogResponse create(@Valid @RequestBody DailyLogRequest request) {
        if ((request.routineItemId() == null) == (request.learningTopicId() == null)) {
            throw new IllegalArgumentException(
                    "Exactly one of routineItemId or learningTopicId must be set");
        }

        DailyLogSource source = request.source() == null
                ? DailyLogSource.MANUAL
                : DailyLogSource.fromValue(request.source());

        DailyLog log = request.routineItemId() != null
                ? DailyLog.forRoutineItem(request.logDate(), findRoutineItem(request.routineItemId()), source)
                : DailyLog.forLearningTopic(request.logDate(), findLearningTopic(request.learningTopicId()), source);

        return DailyLogResponse.from(dailyLogRepository.save(log));
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
