package com.routinemachine.core.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.routinemachine.core.security.TokenUser;
import com.routinemachine.core.domain.EvidenceEvent;
import com.routinemachine.core.domain.Goal;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.EvidenceEventRepository;
import com.routinemachine.core.repository.GoalRepository;
import com.routinemachine.core.repository.UserRepository;
import com.routinemachine.core.service.EvidenceCreditService;
import com.routinemachine.core.service.Ownership;
import jakarta.validation.Valid;
import io.micrometer.core.instrument.MeterRegistry;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

// Phase 9: the evidence-to-credit pipeline's ingest and ledger-read endpoints. See
// docs/product-direction.md and docs/backend.md's decisions log.
@RestController
@RequestMapping("/api/evidence-events")
public class EvidenceEventController {

    private final EvidenceEventRepository evidenceEventRepository;
    private final UserRepository userRepository;
    private final GoalRepository goalRepository;
    private final EvidenceCreditService evidenceCreditService;
    private final MeterRegistry meterRegistry;

    public EvidenceEventController(
            EvidenceEventRepository evidenceEventRepository, UserRepository userRepository,
            GoalRepository goalRepository, EvidenceCreditService evidenceCreditService, MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.evidenceEventRepository = evidenceEventRepository;
        this.userRepository = userRepository;
        this.goalRepository = goalRepository;
        this.evidenceCreditService = evidenceCreditService;
    }

    @Operation(summary = "Record a piece of evidence and decide its credit")
    @ApiResponse(responseCode = "400", description = "There is no goal with that id, or it belongs to someone else. A required field, parameter or path value is missing or cannot be read.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EvidenceEventResponse ingest(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody EvidenceEventRequest request) {
        Long userId = TokenUser.idOf(jwt);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("No user with id " + userId));
        Goal goal = goalRepository.findById(request.goalId())
                .filter(found -> Ownership.sameOwner(found.getUser(), user))
                .orElseThrow(() -> new IllegalArgumentException("No goal with id " + request.goalId()));

        EvidenceEvent event = evidenceCreditService.ingest(
                user, goal, request.source(), request.sourceEventId(), request.occurredAt(), request.minutes());

        return EvidenceEventResponse.from(event, goal);
    }

    // The ledger table (hillside.html's "How your credits were counted"): every evidence event
    // for this user in the window, whatever it was decided — accepted, rejected, pending, or
    // self-reported.
    @Operation(summary = "List the credit ledger: every evidence event and how it was decided")
    @ApiResponse(responseCode = "400", description = "A required field, parameter or path value is missing or cannot be read.")
    @GetMapping
    public List<EvidenceEventResponse> ledger(
            @AuthenticationPrincipal Jwt jwt, @RequestParam LocalDate seasonStart, @RequestParam LocalDate seasonEnd) {
        return evidenceEventRepository.findLedgerForUser(TokenUser.idOf(jwt), seasonStart, seasonEnd).stream()
                .map(EvidenceEventResponse::from)
                .toList();
    }

    // "Question a credit": records the request, nothing more — see
    // docs/product-direction.md's Hillside v2 transparency bullet.
    @Operation(summary = "Question a credit")
    @ApiResponse(responseCode = "400", description = "A required field, parameter or path value is missing or cannot be read.")
    @ApiResponse(responseCode = "404", description = "There is no evidence event with that id, or it belongs to someone else.")
    @PatchMapping("/{id}/question")
    public EvidenceEventResponse question(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        EvidenceEvent event = evidenceEventRepository.findWithGoalById(id)
                .filter(found -> Ownership.ownedBy(found.getUser(), TokenUser.idOf(jwt)))
                .orElseThrow(() -> new NoSuchElementException("No evidence event with id " + id));
        Goal goal = event.getGoal();
        boolean firstTime = event.getQuestionedAt() == null;
        event.question(Instant.now());
        if (firstTime) {
            meterRegistry.counter("credits.questioned").increment();
        }
        return EvidenceEventResponse.from(evidenceEventRepository.save(event), goal);
    }
}
