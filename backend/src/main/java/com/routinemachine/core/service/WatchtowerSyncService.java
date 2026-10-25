package com.routinemachine.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.routinemachine.core.api.WateringHoleStatusResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

// The owner-only operations page's sync status (docs/observability.md, part 5, piece 4). Each source's switch and
// newest data come from the database (WateringHoleStatusService); its run state comes from Prometheus, which keeps
// it across backend restarts. Prometheus is asked THREE fixed questions, never one from a request. Without
// Prometheus the answer is "available: false" and every enabled pull source reads "unknown".
@Service
public class WatchtowerSyncService {

    public static final String FAILURES_QUERY = "sync_consecutive_failures";
    // Seven days back, Prometheus's retention, because the gauge lives in the backend process and starts empty after
    // a restart; the history still holds the last success from before it.
    public static final String LAST_SUCCESS_QUERY = "max by (source) (max_over_time(sync_last_success_timestamp_seconds[7d]))";
    public static final String RUNS_24H_QUERY = "sum by (source, outcome) (increase(sync_runs_total[24h]))";
    public static final List<String> QUERIES = List.of(FAILURES_QUERY, LAST_SUCCESS_QUERY, RUNS_24H_QUERY);

    // Health data is pushed to the backend; the backend never fetches it, so it has no run state.
    private static final String PUSH_ONLY_SOURCE = "HEALTH";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    public record Source(String source, boolean enabled, OffsetDateTime lastActivityAt, String result,
                         Integer consecutiveFailures, Instant lastSuccessAt, long successes24h, long failures24h) {
    }

    public record Sync(boolean available, List<Source> sources) {
    }

    private final WateringHoleStatusService statusService;
    private final String baseUrl;
    private final ObjectMapper mapper = new ObjectMapper();
    // HTTP/1.1 on purpose: by default Java offers an HTTP/2 upgrade (Upgrade: h2c) on plain http calls, which uvicorn logs as
    // "Unsupported upgrade request." at WARNING each time (WatchtowerProbesSendNoUpgradeTest).
    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public WatchtowerSyncService(WateringHoleStatusService statusService,
                                 @Value("${watchtower.prometheus-base-url:}") String baseUrl) {
        this.statusService = statusService;
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
    }

    public Sync sync() {
        List<WateringHoleStatusResponse> statuses = statusService.statuses();
        Optional<Map<String, Double>> failures = Optional.empty();
        Optional<Map<String, Double>> lastSuccess = Optional.empty();
        Optional<Map<String, Double>> runs = Optional.empty();
        if (!baseUrl.isEmpty()) {
            // Asked in parallel, so a slow Prometheus costs one timeout and not three.
            CompletableFuture<Optional<Map<String, Double>>> f = CompletableFuture.supplyAsync(() -> bySource(FAILURES_QUERY, null));
            CompletableFuture<Optional<Map<String, Double>>> s = CompletableFuture.supplyAsync(() -> bySource(LAST_SUCCESS_QUERY, null));
            CompletableFuture<Optional<Map<String, Double>>> r = CompletableFuture.supplyAsync(() -> bySource(RUNS_24H_QUERY, "outcome"));
            failures = f.join();
            lastSuccess = s.join();
            runs = r.join();
        }
        // One answer is enough to call Prometheus available; a source's missing series then means "never ran".
        boolean available = failures.isPresent() && lastSuccess.isPresent() && runs.isPresent();
        Map<String, Double> failureValues = failures.orElse(Map.of());
        Map<String, Double> successValues = lastSuccess.orElse(Map.of());
        Map<String, Double> runValues = runs.orElse(Map.of());
        List<Source> sources = statuses.stream().map(status -> {
            String key = status.source().toLowerCase(Locale.ROOT);
            Integer inARow = failureValues.containsKey(key) ? (int) Math.round(failureValues.get(key)) : null;
            Instant last = successValues.containsKey(key) ? Instant.ofEpochSecond(Math.round(successValues.get(key))) : null;
            return new Source(status.source(), status.enabled(), status.lastActivityAt(),
                    result(status, available, inARow, last), inARow, last,
                    Math.round(runValues.getOrDefault(key + "|success", 0.0)),
                    Math.round(runValues.getOrDefault(key + "|failure", 0.0)));
        }).toList();
        return new Sync(available, sources);
    }

    private static String result(WateringHoleStatusResponse status, boolean available, Integer inARow, Instant last) {
        if (!status.enabled()) {
            return "off";
        }
        if (PUSH_ONLY_SOURCE.equals(status.source())) {
            return "push only";
        }
        if (!available) {
            return "unknown";
        }
        if (inARow != null && inARow > 0) {
            return "failing";
        }
        return last != null ? "ok" : "never succeeded";
    }

    // Instant query, keyed by source (and by "|outcome" when a second label is asked for). Empty means Prometheus did
    // not answer; a present, empty map means it answered with an error or no data.
    private Optional<Map<String, Double>> bySource(String promql, String secondLabel) {
        try {
            URI uri = URI.create(baseUrl + "/api/v1/query?query=" + URLEncoder.encode(promql, StandardCharsets.UTF_8));
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build(), HttpResponse.BodyHandlers.ofString());
            JsonNode body = mapper.readTree(response.body());
            Map<String, Double> values = new HashMap<>();
            if (!"success".equals(body.path("status").asText())) {
                return Optional.of(values);
            }
            for (JsonNode result : body.path("data").path("result")) {
                String source = result.path("metric").path("source").asText("");
                if (source.isEmpty()) {
                    continue;
                }
                String key = secondLabel == null ? source : source + "|" + result.path("metric").path(secondLabel).asText("");
                try {
                    double value = Double.parseDouble(result.path("value").get(1).asText());
                    if (Double.isFinite(value)) {
                        values.put(key, value);
                    }
                } catch (NumberFormatException e) {
                    // "+Inf" and "-Inf": skipped like NaN, so the source reads as having no value.
                }
            }
            return Optional.of(values);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
