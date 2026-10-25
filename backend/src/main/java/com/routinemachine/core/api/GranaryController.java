package com.routinemachine.core.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import com.routinemachine.core.domain.PauseDecision;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.PauseDecisionRepository;
import com.routinemachine.core.repository.UserRepository;
import com.routinemachine.core.service.GranaryService;
import com.routinemachine.core.security.TokenUser;
import com.routinemachine.core.service.Ownership;
import com.routinemachine.core.service.MpesaCategory;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
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

    @Operation(summary = "Store a saving for today")
    @ApiResponse(responseCode = "400", description = "Set a daily saving amount before storing one.")
    @PostMapping("/savings")
    public GranaryMonthSummaryResponse storeSaving(@AuthenticationPrincipal Jwt jwt) {
        User user = findUser(TokenUser.idOf(jwt));
        LocalDate today = LocalDate.now(ZoneId.of(user.getTimeZone()));
        granaryService.storeSaving(user, today);
        return summaryResponse(user, YearMonth.from(today), today);
    }

    @Operation(summary = "Set the daily amount to store")
    @ApiResponse(responseCode = "400", description = "A required field, parameter or path value is missing or cannot be read.")
    @PutMapping("/saving-target-amount")
    public GranaryMonthSummaryResponse setSavingTargetAmount(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SetSavingTargetAmountRequest request) {
        User user = findUser(TokenUser.idOf(jwt));
        user.changeSavingTargetAmount(request.amount());
        userRepository.save(user);
        LocalDate today = LocalDate.now(ZoneId.of(user.getTimeZone()));
        return summaryResponse(user, YearMonth.from(today), today);
    }

    @Operation(summary = "Get the granary summary for this month")
    @ApiResponse(responseCode = "400", description = "A required field, parameter or path value is missing or cannot be read.")
    @GetMapping("/summary")
    public GranaryMonthSummaryResponse summary(@AuthenticationPrincipal Jwt jwt, @RequestParam Optional<YearMonth> month) {
        User user = findUser(TokenUser.idOf(jwt));
        LocalDate today = LocalDate.now(ZoneId.of(user.getTimeZone()));
        return summaryResponse(user, month.orElse(YearMonth.from(today)), today);
    }

    private GranaryMonthSummaryResponse summaryResponse(User user, YearMonth month, LocalDate today) {
        return GranaryMonthSummaryResponse.from(
                granaryService.monthSummary(user, month, today), user.getSavingTargetAmount(), user.getCurrency());
    }

    @Operation(summary = "Log a pause before a tempting decision")
    @ApiResponse(responseCode = "400", description = "A required field, parameter or path value is missing or cannot be read.")
    @PostMapping("/pauses")
    public PauseDecisionResponse createPause(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreatePauseRequest request) {
        User user = findUser(TokenUser.idOf(jwt));
        Instant now = Instant.now();
        PauseDecision pause = granaryService.createPause(
                user, request.what(), request.amount(), request.why(), request.heardFrom(), now);
        return PauseDecisionResponse.from(pause, now);
    }

    // Every pause decision this user has ever logged, newest first — the granary page's own
    // "the pause" table. GET .../pauses/due below is the narrower list Today prompts about.
    @Operation(summary = "List every pause logged, newest first")
    @GetMapping("/pauses")
    public List<PauseDecisionResponse> pauses(@AuthenticationPrincipal Jwt jwt) {
        Instant now = Instant.now();
        return pauseDecisionRepository.findByUserIdOrderByCreatedAtDesc(TokenUser.idOf(jwt)).stream()
                .map(p -> PauseDecisionResponse.from(p, now))
                .toList();
    }

    @Operation(summary = "List the pauses due for a follow-up")
    @GetMapping("/pauses/due")
    public List<PauseDecisionResponse> duePauses(@AuthenticationPrincipal Jwt jwt) {
        User user = findUser(TokenUser.idOf(jwt));
        Instant now = Instant.now();
        return granaryService.pausesDueForFollowUp(user, now).stream()
                .map(p -> PauseDecisionResponse.from(p, now))
                .toList();
    }

    @Operation(summary = "Record the decision on a pause (decline or proceed)")
    @ApiResponse(responseCode = "400", description = "A required field, parameter or path value is missing or cannot be read.")
    @ApiResponse(responseCode = "404", description = "There is no pause decision with that id, or it belongs to someone else.")
    @PatchMapping("/pauses/{id}/decide")
    public PauseDecisionResponse decide(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @Valid @RequestBody DecidePauseRequest request) {
        PauseDecision pause = findPause(id, TokenUser.idOf(jwt));
        Instant now = Instant.now();
        granaryService.decidePause(pause, request.outcome(), now);
        return PauseDecisionResponse.from(pause, now);
    }

    @Operation(summary = "Add a reflection to a pause")
    @ApiResponse(responseCode = "400", description = "A required field, parameter or path value is missing or cannot be read.")
    @ApiResponse(responseCode = "404", description = "There is no pause decision with that id, or it belongs to someone else.")
    @PatchMapping("/pauses/{id}/reflect")
    public PauseDecisionResponse reflect(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @Valid @RequestBody ReflectPauseRequest request) {
        PauseDecision pause = findPause(id, TokenUser.idOf(jwt));
        granaryService.reflectPause(pause, request.reflection());
        return PauseDecisionResponse.from(pause, Instant.now());
    }

    // Multipart, not JSON: the uploaded PDF's bytes are read straight into memory and handed
    // to GranaryService — never written to disk, so "delete the file after parsing" is true by
    // construction. password is optional form data, used once for this one call and never
    // stored (see docs/product-direction.md's M-Pesa section and MpesaPdfReader).
    @Operation(summary = "Import an M-Pesa statement from a PDF")
    @ApiResponse(responseCode = "400", description = "Could not read this file as a PDF. No recognisable transaction rows found. The statement's format may have changed. The statement is password-protected, or the password given is wrong. A required field, parameter or path value is missing or cannot be read.")
    @PostMapping("/mpesa-statement")
    public MpesaImportResponse importMpesaStatement(
            @AuthenticationPrincipal Jwt jwt, @RequestParam MultipartFile file, @RequestParam Optional<String> password) {
        User user = findUser(TokenUser.idOf(jwt));
        byte[] pdfBytes;
        try {
            pdfBytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded file", e);
        }
        return MpesaImportResponse.from(granaryService.importMpesaStatement(user, pdfBytes, password.orElse(null)));
    }

    @Operation(summary = "List the M-Pesa spending categories")
    @ApiResponse(responseCode = "400", description = "A required field, parameter or path value is missing or cannot be read.")
    @GetMapping("/mpesa-categories")
    public Map<MpesaCategory, Integer> mpesaCategories(@AuthenticationPrincipal Jwt jwt, @RequestParam Optional<YearMonth> month) {
        User user = findUser(TokenUser.idOf(jwt));
        YearMonth target = month.orElseGet(() -> YearMonth.now(ZoneId.of(user.getTimeZone())).minusMonths(1));
        return granaryService.mpesaCategoryTotals(user, target);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("No user with id " + userId));
    }

    // Someone else's pause answers exactly like one that does not exist.
    private PauseDecision findPause(Long id, Long userId) {
        return pauseDecisionRepository.findById(id)
                .filter(pause -> Ownership.ownedBy(pause.getUser(), userId))
                .orElseThrow(() -> new NoSuchElementException("No pause decision with id " + id));
    }
}
