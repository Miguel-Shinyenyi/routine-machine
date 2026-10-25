package com.routinemachine.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

// The owner-only operations page's charts (docs/observability.md, part 5, piece 2). The backend reads Prometheus
// with a FIXED list of queries (the same expressions as the Grafana dashboard, which a test keeps in step). A
// caller chooses only the time range, from three values; no query text from a request ever reaches Prometheus.
// Prometheus's address is configuration (watchtower.prometheus-base-url). If Prometheus is not running the answer
// is "available: false", not an error, and one failing query empties only its own chart.
@Service
public class WatchtowerMetricsService {

    public record Query(String id, String title, String unit, String promql, List<String> labels) {
    }

    public record Series(String label, List<List<Number>> points) {
    }

    // totalSeries is how many series Prometheus returned; truncated is true when only the largest were kept.
    public record Chart(String id, String title, String unit, List<Series> series, int totalSeries, boolean truncated) {
    }

    private record Found(List<Series> series, int total) {
    }

    public record Metrics(boolean available, String range, List<Chart> charts) {
    }

    public static final String DEFAULT_RANGE = "6h";
    // A chart never carries more series than this, so a metric that grows a label cannot flood the page. It is eight
    // because the page's palette has eight colours that stay apart for colour-blind readers; a ninth would need a
    // colour that is not distinct. The series kept are the eight with the largest peaks, and the answer says if it cut.
    public static final int MAX_SERIES = 8;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    public static final List<Query> QUERIES = List.of(
            new Query("backend-p95-latency", "Backend p95 latency by route", "s",
                    "histogram_quantile(0.95, sum by (le, uri) (rate(http_server_requests_seconds_bucket{job=\"backend\"}[5m])))",
                    List.of("uri")),
            new Query("intelligence-p95-latency", "Intelligence service p95 latency by route", "s",
                    "histogram_quantile(0.95, sum by (le, route) (rate(http_request_duration_seconds_bucket[5m])))",
                    List.of("route")),
            new Query("credits-by-status", "Credits decided per hour, by status", "per hour",
                    "sum by (status) (increase(credits_events_total[1h]))", List.of("status")),
            new Query("credits-by-rule-version", "Credits decided per hour, by rule version", "per hour",
                    "sum by (rule_version) (increase(credits_events_total[1h]))", List.of("rule_version")),
            new Query("questioned-credits", "Questioned credits (total)", "credits",
                    "sum(credits_questioned_total)", List.of()),
            new Query("sync-runs", "Fetches per hour, by source and outcome", "per hour",
                    "sum by (source, outcome) (increase(sync_runs_total[1h]))", List.of("source", "outcome")),
            new Query("sync-consecutive-failures", "Failures in a row", "failures",
                    "sync_consecutive_failures", List.of("source")),
            new Query("sync-seconds-since-success", "Time since last success", "s",
                    "time() - sync_last_success_timestamp_seconds", List.of("source")),
            new Query("assessments-by-outcome", "Assessments per hour, by outcome", "per hour",
                    "sum by (outcome) (increase(assessment_requests_total[1h]))", List.of("outcome")),
            new Query("assessment-p95-latency", "Assessment p95 latency", "s",
                    "histogram_quantile(0.95, sum by (le) (rate(assessment_duration_seconds_bucket[15m])))", List.of()),
            new Query("assessment-tokens", "Model tokens per hour", "per hour",
                    "sum by (direction) (increase(assessment_tokens_total[1h]))", List.of("direction")),
            new Query("assessment-cost-24h", "Estimated cost, last 24 h (USD; 0 until prices are set)", "USD",
                    "sum(increase(assessment_estimated_cost_usd_total[24h]))", List.of()));

    private final String baseUrl;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public WatchtowerMetricsService(@Value("${watchtower.prometheus-base-url:}") String baseUrl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
    }

    public Metrics metrics(String rangeLabel) {
        WatchtowerRange range = WatchtowerRange.of(rangeLabel);
        if (baseUrl.isEmpty()) {
            return new Metrics(false, range.label, List.of());
        }
        long end = Instant.now().getEpochSecond();
        long start = end - range.seconds;
        // Queried in parallel, so a slow Prometheus costs one timeout and not one per chart.
        List<CompletableFuture<Optional<Found>>> pending = QUERIES.stream()
                .map(query -> CompletableFuture.supplyAsync(() -> fetch(query, start, end, range.step)))
                .toList();
        List<Chart> charts = new ArrayList<>();
        boolean answered = false;
        for (int i = 0; i < QUERIES.size(); i++) {
            Optional<Found> result = pending.get(i).join();
            answered |= result.isPresent();
            Query query = QUERIES.get(i);
            Found found = result.orElse(new Found(List.of(), 0));
            charts.add(new Chart(query.id(), query.title(), query.unit(), found.series(), found.total(),
                    found.total() > found.series().size()));
        }
        return answered ? new Metrics(true, range.label, charts) : new Metrics(false, range.label, List.of());
    }

    // Empty means Prometheus did not answer at all. A Found with no series means it answered with an error or no data.
    private Optional<Found> fetch(Query query, long start, long end, long step) {
        try {
            URI uri = URI.create(baseUrl + "/api/v1/query_range?query=" + URLEncoder.encode(query.promql(), StandardCharsets.UTF_8)
                    + "&start=" + start + "&end=" + end + "&step=" + step);
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build(), HttpResponse.BodyHandlers.ofString());
            JsonNode body = mapper.readTree(response.body());
            if (!"success".equals(body.path("status").asText())) {
                return Optional.of(new Found(List.of(), 0));
            }
            List<Series> series = new ArrayList<>();
            for (JsonNode result : body.path("data").path("result")) {
                series.add(new Series(label(query, result.path("metric")), points(result.path("values"))));
            }
            // Largest peak first, so a cut keeps the series that matter (the slowest routes, the busiest sources).
            // The sort is stable, so equal peaks keep Prometheus's order.
            List<Series> kept = series.stream()
                    .sorted(Comparator.comparingDouble(WatchtowerMetricsService::peak).reversed())
                    .limit(MAX_SERIES).toList();
            return Optional.of(new Found(kept, series.size()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static double peak(Series series) {
        return series.points().stream().mapToDouble(point -> point.get(1).doubleValue()).max().orElse(Double.NEGATIVE_INFINITY);
    }

    private static String label(Query query, JsonNode metric) {
        List<String> parts = new ArrayList<>();
        for (String name : query.labels()) {
            String value = metric.path(name).asText("");
            if (!value.isEmpty()) {
                parts.add(value);
            }
        }
        return parts.isEmpty() ? query.title() : String.join(" ", parts);
    }

    // A point is [seconds, value]. Prometheus sends the value as text, and "NaN", "+Inf" or "-Inf" when there is nothing
    // to say (for example a quantile of no traffic). Java reads "NaN" but not "+Inf", and either way such a point is
    // dropped, so a chart shows a gap and not a wrong number. One unreadable point never empties the whole chart.
    private static List<List<Number>> points(JsonNode values) {
        List<List<Number>> points = new ArrayList<>();
        for (JsonNode pair : values) {
            try {
                double value = Double.parseDouble(pair.get(1).asText());
                if (Double.isFinite(value)) {
                    points.add(List.of(pair.get(0).asLong(), value));
                }
            } catch (NumberFormatException e) {
                // "+Inf" and "-Inf": skipped like NaN.
            }
        }
        return points;
    }
}
