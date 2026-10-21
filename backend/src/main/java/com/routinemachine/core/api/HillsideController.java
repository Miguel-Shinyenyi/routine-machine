package com.routinemachine.core.api;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.routinemachine.core.security.TokenUser;
import com.routinemachine.core.domain.HillsideField;
import com.routinemachine.core.service.HillsideService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Optional;

// Phase 8 groundwork: asUserId is a demo-mode parameter, not a real logged-in identity — the
// Next.js session this would normally come from isn't built yet. See docs/backend.md's
// decisions log. field omitted means "general" (3+ distinct non-private routines tended that
// day, same threshold intelligence-service's tally.py uses for its own tally stick).
@RestController
@RequestMapping("/api/hillside")
public class HillsideController {

    private final HillsideService hillsideService;

    public HillsideController(HillsideService hillsideService) {
        this.hillsideService = hillsideService;
    }

    @GetMapping
    public HillsideResponse get(
            @RequestParam Optional<HillsideField> field,
            @RequestParam(defaultValue = "7") int windowDays,
            @AuthenticationPrincipal Jwt jwt) {
        // "You" comes only from a valid token; a visitor with none sees the ranking without a band.
        Long you = jwt == null ? null : TokenUser.idOf(jwt);
        return HillsideResponse.from(hillsideService.compute(field.orElse(null), windowDays, you, LocalDate.now()));
    }
}
