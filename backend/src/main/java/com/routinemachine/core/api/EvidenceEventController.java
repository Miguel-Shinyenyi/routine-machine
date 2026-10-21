package com.routinemachine.core.api;

import com.routinemachine.core.domain.EvidenceEvent;
import com.routinemachine.core.domain.Goal;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.EvidenceEventRepository;
import com.routinemachine.core.repository.GoalRepository;
import com.routinemachine.core.repository.UserRepository;
import com.routinemachine.core.service.EvidenceCreditService;
import com.routinemachine.core.service.Ownership;
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

    public EvidenceEventController(
            EvidenceEventRepository evidenceEventRepository, UserRepository userRepository,
            GoalRepository goalRepository, EvidenceCreditService evidenceCreditService) {
        this.evidenceEventRepository = evidenceEventRepository;
        this.userRepository = userRepository;
        this.goalRepository = goalRepository;
        this.evidenceCreditService = evidenceCreditService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EvidenceEventResponse ingest(@Valid @RequestBody EvidenceEventRequest request) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new IllegalArgumentException("No user with id " + request.userId()));
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
    @GetMapping
    public List<EvidenceEventResponse> ledger(
            @RequestParam Long userId, @RequestParam LocalDate seasonStart, @RequestParam LocalDate seasonEnd) {
        return evidenceEventRepository.findLedgerForUser(userId, seasonStart, seasonEnd).stream()
                .map(EvidenceEventResponse::from)
                .toList();
    }

    // "Question a credit": records the request, nothing more — see
    // docs/product-direction.md's Hillside v2 transparency bullet.
    @PatchMapping("/{id}/question")
    public EvidenceEventResponse question(@PathVariable Long id, @RequestParam Long userId) {
        EvidenceEvent event = evidenceEventRepository.findWithGoalById(id)
                .filter(found -> Ownership.ownedBy(found.getUser(), userId))
                .orElseThrow(() -> new NoSuchElementException("No evidence event with id " + id));
        Goal goal = event.getGoal();
        event.question(Instant.now());
        return EvidenceEventResponse.from(evidenceEventRepository.save(event), goal);
    }
}
