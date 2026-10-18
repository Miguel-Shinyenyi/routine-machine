package com.routinemachine.core.api;

import com.routinemachine.core.domain.PauseDecision;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.PauseDecisionRepository;
import com.routinemachine.core.repository.UserRepository;
import com.routinemachine.core.service.GranaryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

// Phase 11: the granary (docs/product-direction.md). Never holds or moves money — every
// endpoint here only records that a saving or a decision happened.
@RestController
@RequestMapping("/api/granary")
public class GranaryController {

    private final GranaryService granaryService;
    private final UserRepository userRepository;
    private final PauseDecisionRepository pauseDecisionRepository;

    public GranaryController(
            GranaryService granaryService, UserRepository userRepository,
            PauseDecisionRepository pauseDecisionRepository) {
        this.granaryService = granaryService;
        this.userRepository = userRepository;
        this.pauseDecisionRepository = pauseDecisionRepository;
    }

    @PostMapping("/savings")
    public GranaryMonthSummaryResponse storeSaving(@Valid @RequestBody StoreSavingRequest request) {
        User user = findUser(request.userId());
        LocalDate today = LocalDate.now(ZoneId.of(user.getTimeZone()));
        granaryService.storeSaving(user, today);
        return summaryResponse(user, YearMonth.from(today), today);
    }

    @PutMapping("/saving-target-amount")
    public GranaryMonthSummaryResponse setSavingTargetAmount(@Valid @RequestBody SetSavingTargetAmountRequest request) {
        User user = findUser(request.userId());
        user.changeSavingTargetAmount(request.amount());
        userRepository.save(user);
        LocalDate today = LocalDate.now(ZoneId.of(user.getTimeZone()));
        return summaryResponse(user, YearMonth.from(today), today);
    }

    @GetMapping("/summary")
    public GranaryMonthSummaryResponse summary(@RequestParam Long userId, @RequestParam Optional<YearMonth> month) {
        User user = findUser(userId);
        LocalDate today = LocalDate.now(ZoneId.of(user.getTimeZone()));
        return summaryResponse(user, month.orElse(YearMonth.from(today)), today);
    }

    private GranaryMonthSummaryResponse summaryResponse(User user, YearMonth month, LocalDate today) {
        return GranaryMonthSummaryResponse.from(
                granaryService.monthSummary(user, month, today), user.getSavingTargetAmount(), user.getCurrency());
    }

    @PostMapping("/pauses")
    public PauseDecisionResponse createPause(@Valid @RequestBody CreatePauseRequest request) {
        User user = findUser(request.userId());
        Instant now = Instant.now();
        PauseDecision pause = granaryService.createPause(
                user, request.what(), request.amount(), request.why(), request.heardFrom(), now);
        return PauseDecisionResponse.from(pause, now);
    }

    // Every pause decision this user has ever logged, newest first — the granary page's own
    // "the pause" table. GET .../pauses/due below is the narrower list Today prompts about.
    @GetMapping("/pauses")
    public List<PauseDecisionResponse> pauses(@RequestParam Long userId) {
        Instant now = Instant.now();
        return pauseDecisionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(p -> PauseDecisionResponse.from(p, now))
                .toList();
    }

    @GetMapping("/pauses/due")
    public List<PauseDecisionResponse> duePauses(@RequestParam Long userId) {
        User user = findUser(userId);
        Instant now = Instant.now();
        return granaryService.pausesDueForFollowUp(user, now).stream()
                .map(p -> PauseDecisionResponse.from(p, now))
                .toList();
    }

    @PatchMapping("/pauses/{id}/decide")
    public PauseDecisionResponse decide(@PathVariable Long id, @Valid @RequestBody DecidePauseRequest request) {
        PauseDecision pause = findPause(id);
        Instant now = Instant.now();
        granaryService.decidePause(pause, request.outcome(), now);
        return PauseDecisionResponse.from(pause, now);
    }

    @PatchMapping("/pauses/{id}/reflect")
    public PauseDecisionResponse reflect(@PathVariable Long id, @Valid @RequestBody ReflectPauseRequest request) {
        PauseDecision pause = findPause(id);
        granaryService.reflectPause(pause, request.reflection());
        return PauseDecisionResponse.from(pause, Instant.now());
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("No user with id " + userId));
    }

    private PauseDecision findPause(Long id) {
        return pauseDecisionRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("No pause decision with id " + id));
    }
}
