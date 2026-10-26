package com.routinemachine.core.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import com.routinemachine.core.security.TokenUser;
import com.routinemachine.core.service.OwnerAccount;
import com.routinemachine.core.service.ServiceHealthService;
import com.fasterxml.jackson.databind.JsonNode;
import com.routinemachine.core.service.WatchtowerIntelligenceDocsService;
import com.routinemachine.core.service.WatchtowerLogsService;
import com.routinemachine.core.service.WatchtowerMetricsService;
import com.routinemachine.core.service.WatchtowerSyncService;
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
    private final WatchtowerSyncService syncService;
    private final WatchtowerIntelligenceDocsService intelligenceDocsService;

    public WatchtowerController(OwnerAccount ownerAccount, ServiceHealthService serviceHealthService,
                                WatchtowerMetricsService metricsService, WatchtowerLogsService logsService,
                                WatchtowerSyncService syncService, WatchtowerIntelligenceDocsService intelligenceDocsService) {
        this.ownerAccount = ownerAccount;
        this.serviceHealthService = serviceHealthService;
        this.metricsService = metricsService;
        this.logsService = logsService;
        this.syncService = syncService;
        this.intelligenceDocsService = intelligenceDocsService;
    }

    @Operation(summary = "Owner only: the health of each service")
    @GetMapping("/services")
    public List<ServiceHealthService.ServiceStatus> services(@AuthenticationPrincipal Jwt jwt) {
        requireOwner(jwt);
        return serviceHealthService.statuses();
    }

    // The only thing a caller chooses is the time range (1h, 6h or 24h). The queries are a fixed list.
    @Operation(summary = "Owner only: chart data read from Prometheus")
    @ApiResponse(responseCode = "400", description = "The range must be 1h, 6h or 24h. A required field, parameter or path value is missing or cannot be read.")
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
    @Operation(summary = "Owner only: recent log lines read from Loki")
    @ApiResponse(responseCode = "400", description = "The level is not one of the allowed levels. The range must be 1h, 6h or 24h. The request id may hold only letters, digits, dot, underscore and hyphen (1 to 64). A required field, parameter or path value is missing or cannot be read.")
    @GetMapping("/logs")
    public WatchtowerLogsService.Logs logs(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String requestId,
            @RequestParam(defaultValue = WatchtowerLogsService.DEFAULT_RANGE) String range) {
        requireOwner(jwt);
        return logsService.logs(level, requestId, range);
    }

    // Each source's switch, newest data, run state and one result word. Nothing in the request reaches Prometheus.
    @Operation(summary = "Owner only: the state of each sync source")
    @GetMapping("/sync")
    public WatchtowerSyncService.Sync sync(@AuthenticationPrincipal Jwt jwt) {
        requireOwner(jwt);
        return syncService.sync();
    }

    // The intelligence service's API document (staged Prompt 4). The service serves it only on an internal port; this hands
    // it to the owner, so the documentation page can show both APIs. The address is configuration, nothing in the request.
    @Operation(summary = "Owner only: the intelligence service's OpenAPI document")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "The intelligence service's documentation is not configured or does not answer.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "What the intelligence service sent is not an OpenAPI document.")
    @GetMapping("/intelligence-api-docs")
    public JsonNode intelligenceApiDocs(@AuthenticationPrincipal Jwt jwt) {
        requireOwner(jwt);
        return intelligenceDocsService.spec();
    }

    private void requireOwner(Jwt jwt) {
        if (jwt == null || !ownerAccount.isOwner(TokenUser.idOf(jwt))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }
}
