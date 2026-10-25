package com.routinemachine.core.api;

import com.routinemachine.core.security.TokenUser;
import com.routinemachine.core.service.OwnerAccount;
import com.routinemachine.core.service.ServiceHealthService;
import com.routinemachine.core.service.WatchtowerLogsService;
import com.routinemachine.core.service.WatchtowerMetricsService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

// The owner-only operations page's data (docs/observability.md, part 5). Only the owner may read it. Anyone else,
// signed in or not, gets a plain 404, never a 401 or a login prompt, so the page is not known to exist. That is
// why this path is on the optional-token chain in SecurityConfig: a signed-out visitor must reach this check.
// With no owner configured nobody is the owner, so the page is closed.
@RestController
@RequestMapping("/api/watchtower")
public class WatchtowerController {

    private final OwnerAccount ownerAccount;
    private final ServiceHealthService serviceHealthService;
    private final WatchtowerMetricsService metricsService;
    private final WatchtowerLogsService logsService;

    public WatchtowerController(OwnerAccount ownerAccount, ServiceHealthService serviceHealthService,
                                WatchtowerMetricsService metricsService, WatchtowerLogsService logsService) {
        this.ownerAccount = ownerAccount;
        this.serviceHealthService = serviceHealthService;
        this.metricsService = metricsService;
        this.logsService = logsService;
    }

    @GetMapping("/services")
    public List<ServiceHealthService.ServiceStatus> services(@AuthenticationPrincipal Jwt jwt) {
        requireOwner(jwt);
        return serviceHealthService.statuses();
    }

    // The only thing a caller chooses is the time range (1h, 6h or 24h). The queries are a fixed list.
    @GetMapping("/metrics")
    public WatchtowerMetricsService.Metrics metrics(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = WatchtowerMetricsService.DEFAULT_RANGE) String range) {
        requireOwner(jwt);
        return metricsService.metrics(range);
    }

    // Recent logs from Loki. The caller may filter by one level from a fixed list and by an id of plain characters (a
    // request id or a trace id), and choose the range. The owner check comes first, so a non-owner never learns that a
    // bad parameter would have been refused.
    @GetMapping("/logs")
    public WatchtowerLogsService.Logs logs(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String requestId,
            @RequestParam(defaultValue = WatchtowerLogsService.DEFAULT_RANGE) String range) {
        requireOwner(jwt);
        return logsService.logs(level, requestId, range);
    }

    private void requireOwner(Jwt jwt) {
        if (jwt == null || !ownerAccount.isOwner(TokenUser.idOf(jwt))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }
}
