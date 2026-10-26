package com.routinemachine.core.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.routinemachine.core.security.TokenUser;
import com.routinemachine.core.service.HillsideV2Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;

// Phase 9: the credit-based hillside. "You" comes only from a valid token: a visitor with none sees the ranking without a
// band, and no parameter can name another person. seasonStart, when omitted, defaults to the most recent Monday in UTC,
// a stand-in for "the cohort's time zone" that docs/backend.md records as an open question.
@RestController
@RequestMapping("/api/hillside/v2")
public class HillsideV2Controller {

    private final HillsideV2Service hillsideV2Service;

    public HillsideV2Controller(HillsideV2Service hillsideV2Service) {
        this.hillsideV2Service = hillsideV2Service;
    }

    @Operation(summary = "Get the credit-based hillside (version 2)")
    @ApiResponse(responseCode = "400", description = "A required field, parameter or path value is missing or cannot be read.")
    @GetMapping
    public HillsideV2Response get(
            @RequestParam Optional<Long> goalId, @RequestParam Optional<LocalDate> seasonStart,
            @AuthenticationPrincipal Jwt jwt) {
        // "You" comes only from a valid token; a visitor with none sees the ranking without a band.
        Long you = jwt == null ? null : TokenUser.idOf(jwt);
        LocalDate start = seasonStart.orElseGet(() -> LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
        LocalDate end = start.plusDays(6);
        return HillsideV2Response.from(hillsideV2Service.compute(goalId.orElse(null), start, end, you));
    }
}
