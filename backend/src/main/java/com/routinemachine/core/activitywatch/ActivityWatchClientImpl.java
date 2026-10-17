package com.routinemachine.core.activitywatch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Real HTTP implementation over aw-server's local REST API (https://docs.activitywatch.net,
// "REST API"). Unlike WakaTime and GitHub, ActivityWatch has no cloud component: aw-server
// runs on the same machine as whatever is using it, so this assumes the backend runs on that
// same machine too, reachable at ACTIVITYWATCH_BASE_URL (default http://localhost:5600) --
// flagged and agreed with the user rather than decided silently, see docs/backend.md's
// decisions log. Never throws: an unreachable server, no afk bucket, or any other failure logs
// one line and returns an empty list, same shape as GitHubContentClientImpl/WakaTimeClientImpl.
@Component
public class ActivityWatchClientImpl implements ActivityWatchClient {

    private static final Logger log = LoggerFactory.getLogger(ActivityWatchClientImpl.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String BASE_URL_ENV_VAR = "ACTIVITYWATCH_BASE_URL";
    private static final String DEFAULT_BASE_URL = "http://localhost:5600";

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ActivityWatchClientImpl() {
        this(resolveBaseUrl());
    }

    // Package-visible: lets tests point at a fake server instead of the real local one.
    ActivityWatchClientImpl(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    private static String resolveBaseUrl() {
        String configured = System.getenv(BASE_URL_ENV_VAR);
        return (configured == null || configured.isBlank()) ? DEFAULT_BASE_URL : configured;
    }

    @Override
    public List<ActivityWatchDailyTotal> fetchDailyActiveTotals(LocalDate start, LocalDate end) {
        Optional<String> afkBucketId = findAfkBucketId();
        if (afkBucketId.isEmpty()) {
            log.info("activitywatch-sync: no afk bucket found, skipping");
            return List.of();
        }
        return fetchNotAfkTotalsByDay(afkBucketId.get(), start, end);
    }

    private Optional<String> findAfkBucketId() {
        try {
            HttpResponse<String> response = get(baseUrl + "/api/0/buckets/");
            if (response.statusCode() != 200) {
                log.info("activitywatch-sync: listing buckets returned HTTP {}", response.statusCode());
                return Optional.empty();
            }
            JsonNode buckets = objectMapper.readTree(response.body());
            Iterator<String> bucketIds = buckets.fieldNames();
            while (bucketIds.hasNext()) {
                String id = bucketIds.next();
                if (id.contains("afk")) {
                    return Optional.of(id);
                }
            }
            return Optional.empty();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.info("activitywatch-sync: failed to list buckets: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private List<ActivityWatchDailyTotal> fetchNotAfkTotalsByDay(String bucketId, LocalDate start, LocalDate end) {
        try {
            String startIso = OffsetDateTime.of(LocalDateTime.of(start, LocalTime.MIN), ZoneOffset.UTC).toString();
            String endIso = OffsetDateTime.of(LocalDateTime.of(end.plusDays(1), LocalTime.MIN), ZoneOffset.UTC).toString();
            String url = baseUrl + "/api/0/buckets/" + bucketId + "/events?start="
                    + URLEncoder.encode(startIso, StandardCharsets.UTF_8) + "&end=" + URLEncoder.encode(endIso, StandardCharsets.UTF_8);
            HttpResponse<String> response = get(url);
            if (response.statusCode() != 200) {
                log.info("activitywatch-sync: fetching events for {} returned HTTP {}", bucketId, response.statusCode());
                return List.of();
            }
            return sumNotAfkSecondsByDay(response.body());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.info("activitywatch-sync: failed to fetch events for {}: {}", bucketId, e.getClass().getSimpleName());
            return List.of();
        }
    }

    private List<ActivityWatchDailyTotal> sumNotAfkSecondsByDay(String responseBody) throws IOException {
        JsonNode events = objectMapper.readTree(responseBody);
        Map<LocalDate, Long> secondsByDay = new HashMap<>();
        if (events.isArray()) {
            for (JsonNode event : events) {
                if (!"not-afk".equals(event.path("data").path("status").asText())) {
                    continue;
                }
                LocalDate date = OffsetDateTime.parse(event.path("timestamp").asText()).toLocalDate();
                long seconds = (long) event.path("duration").asDouble();
                secondsByDay.merge(date, seconds, Long::sum);
            }
        }
        return secondsByDay.entrySet().stream()
                .map(entry -> new ActivityWatchDailyTotal(entry.getKey(), entry.getValue()))
                .toList();
    }

    private HttpResponse<String> get(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
