package com.routinemachine.core.wakatime;

import com.routinemachine.core.observability.SyncMetrics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

// Real HTTP implementation over WakaTime's public summaries endpoint
// (https://wakatime.com/developers#summaries). Reads the API key fresh from
// WAKATIME_API_KEY on every call, same "no caching, nothing to restart on rotation" choice
// HubJournalReader makes for UMWAYI_JOURNAL_KEY_B64. Never throws: a missing key, a non-200
// response, or a network failure all log one line and return an empty list, same shape as
// GitHubContentClientImpl.
@Component
public class WakaTimeClientImpl implements WakaTimeClient {

    private static final Logger log = LoggerFactory.getLogger(WakaTimeClientImpl.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String API_KEY_ENV_VAR = "WAKATIME_API_KEY";

    private final HttpClient httpClient;

    public WakaTimeClientImpl() {
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    @Override
    public List<WakaTimeDailyTotal> fetchDailyTotals(LocalDate start, LocalDate end) {
        String apiKey = System.getenv(API_KEY_ENV_VAR);
        if (apiKey == null || apiKey.isBlank()) {
            log.info("wakatime-sync: {} is not set, skipping", API_KEY_ENV_VAR);
            return List.of();
        }
        try {
            String url = "https://wakatime.com/api/v1/users/current/summaries?start="
                    + start.format(DateTimeFormatter.ISO_LOCAL_DATE) + "&end=" + end.format(DateTimeFormatter.ISO_LOCAL_DATE);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                            apiKey.getBytes(StandardCharsets.UTF_8)))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.info("wakatime-sync: summaries request returned HTTP {}", response.statusCode());
                SyncMetrics.failure("wakatime");
                return List.of();
            }
            SyncMetrics.success("wakatime");
            return parseDailyTotals(response.body());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.info("wakatime-sync: failed to fetch summaries: {}", e.getClass().getSimpleName());
            SyncMetrics.failure("wakatime");
            return List.of();
        }
    }

    private List<WakaTimeDailyTotal> parseDailyTotals(String responseBody) throws IOException {
        JsonNode data = new ObjectMapper().readTree(responseBody).path("data");
        List<WakaTimeDailyTotal> totals = new ArrayList<>();
        if (data.isArray()) {
            for (JsonNode day : data) {
                LocalDate date = LocalDate.parse(day.path("range").path("date").asText());
                long totalSeconds = day.path("grand_total").path("total_seconds").asLong();
                totals.add(new WakaTimeDailyTotal(date, totalSeconds));
            }
        }
        return totals;
    }
}
