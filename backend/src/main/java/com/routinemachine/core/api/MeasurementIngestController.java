package com.routinemachine.core.api;

import io.swagger.v3.oas.annotations.Operation;
import com.routinemachine.core.domain.Measurement;
import com.routinemachine.core.domain.MeasurementSource;
import com.routinemachine.core.service.MeasurementService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

// POST /api/measurements: the one HTTP-reachable way health data gets into this system (the
// companion app that would call it is out of scope for this phase, see PROJECT.md). Every
// other watering hole is read by the backend pulling from a cloud or local API; health data
// has no such API to pull from, so this is the push side instead, same role a companion app
// would otherwise fill.
@RestController
@RequestMapping("/api/measurements")
public class MeasurementIngestController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final MeasurementService measurementService;
    private final String configuredToken;

    public MeasurementIngestController(
            MeasurementService measurementService,
            @Value("${health-ingest.token:}") String configuredToken) {
        this.measurementService = measurementService;
        this.configuredToken = configuredToken;
    }

    @Operation(summary = "Push a health measurement (health-ingest token only)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "The health-ingest token is missing, wrong or not configured.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MeasurementResponse ingest(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody MeasurementIngestRequest request) {
        requireValidToken(authorizationHeader);
        Measurement measurement = measurementService.recordMeasurement(
                MeasurementSource.HEALTH, request.metric(), request.date(), request.value());
        return MeasurementResponse.from(measurement);
    }

    // Fails closed: no configured token means every request is rejected, not accepted. A
    // blank HEALTH_INGEST_TOKEN should mean "nothing can push health data in", not "anyone can".
    private void requireValidToken(String authorizationHeader) {
        if (configuredToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "health ingest token is not configured");
        }
        String presentedToken = Optional.ofNullable(authorizationHeader)
                .filter(header -> header.startsWith(BEARER_PREFIX))
                .map(header -> header.substring(BEARER_PREFIX.length()))
                .orElse(null);
        if (!configuredToken.equals(presentedToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid or missing token");
        }
    }
}
