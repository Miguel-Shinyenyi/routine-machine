package com.routinemachine.core.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.routinemachine.core.security.TokenUser;
import com.routinemachine.core.domain.DailyLog;
import com.routinemachine.core.domain.DailyLogSource;
import com.routinemachine.core.domain.LearningTopic;
import com.routinemachine.core.domain.RoutineItem;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.DailyLogRepository;
import com.routinemachine.core.repository.LearningTopicRepository;
import com.routinemachine.core.repository.RoutineItemRepository;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/daily-logs")
public class DailyLogController {

    private final DailyLogRepository dailyLogRepository;
    private final RoutineItemRepository routineItemRepository;
    private final LearningTopicRepository learningTopicRepository;
    private final UserRepository userRepository;

    public DailyLogController(
            DailyLogRepository dailyLogRepository,
            RoutineItemRepository routineItemRepository,
            LearningTopicRepository learningTopicRepository,
            UserRepository userRepository) {
        this.dailyLogRepository = dailyLogRepository;
        this.routineItemRepository = routineItemRepository;
        this.learningTopicRepository = learningTopicRepository;
        this.userRepository = userRepository;
    }

    // The signed-in person's own logs, for one date when date is given. Who that is comes from the token, never from a
    // parameter (docs/backend.md, access control).
    @Operation(summary = "List daily logs")
    @ApiResponse(responseCode = "400", description = "A required field, parameter or path value is missing or cannot be read.")
    @GetMapping
    public List<DailyLogResponse> list(@AuthenticationPrincipal Jwt jwt, @RequestParam Optional<LocalDate> date) {
        Long userId = TokenUser.idOf(jwt);
        List<DailyLog> logs = date.map(d -> dailyLogRepository.findByUserIdAndLogDate(userId, d))
                .orElseGet(() -> dailyLogRepository.findByUserId(userId));
        return logs.stream().map(DailyLogResponse::from).toList();
    }

    @Operation(summary = "Record a day's log")
    @ApiResponse(responseCode = "400", description = "Exactly one of routineItemId or learningTopicId must be set. There is no learning topic with that id, or it belongs to someone else. There is no routine item with that id, or it belongs to someone else. Unknown daily log source. A required field, parameter or path value is missing or cannot be read.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DailyLogResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DailyLogRequest request) {
        if ((request.routineItemId() == null) == (request.learningTopicId() == null)) {
            throw new IllegalArgumentException(
                    "Exactly one of routineItemId or learningTopicId must be set");
        }

        Long userId = TokenUser.idOf(jwt);
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("No user with id " + userId));
        DailyLogSource source = request.source() == null
                ? DailyLogSource.MANUAL
                : DailyLogSource.fromValue(request.source());

        DailyLog log = request.routineItemId() != null
                ? DailyLog.forRoutineItem(
                        request.logDate(), findRoutineItem(request.routineItemId(), owner), source,
                        request.measuredMinutes())
                : DailyLog.forLearningTopic(
                        request.logDate(), findLearningTopic(request.learningTopicId(), owner), source,
                        request.measuredMinutes());

        log.assignUser(owner);

        return DailyLogResponse.from(dailyLogRepository.save(log));
    }

    // A day can only be logged against the caller's own routine items and topics; anyone else's
    // answers exactly like one that does not exist.
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
