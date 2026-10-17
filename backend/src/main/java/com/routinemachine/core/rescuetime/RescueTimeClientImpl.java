package com.routinemachine.core.rescuetime;

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
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Real implementation over RescueTime's Analytic API Data endpoint
// (https://www.rescuetime.com/apidoc/, "Analytic API Data"), restricted to the "overview"
// perspective at day granularity. That perspective returns one row per day per productivity
// level (-2 to 2), each shaped [date, seconds, people, productivity], so a day's total active
// time is the sum of every row's seconds for that date, not any single row. Reads the key
// fresh from RESCUETIME_API_KEY on every call, same no-caching choice the other watering-hole
// clients make. Never throws: a missing key, a non-200 response, or a network failure all log
// one line and return an empty list.
@Component
public class RescueTimeClientImpl implements RescueTimeClient {

    private static final Logger log = LoggerFactory.getLogger(RescueTimeClientImpl.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String API_KEY_ENV_VAR = "RESCUETIME_API_KEY";

    private final HttpClient httpClient;

    public RescueTimeClientImpl() {
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    @Override
    public List<RescueTimeDailyTotal> fetchDailyActiveTotals(LocalDate start, LocalDate end) {
        String apiKey = System.getenv(API_KEY_ENV_VAR);
        if (apiKey == null || apiKey.isBlank()) {
            log.info("rescuetime-sync: {} is not set, skipping", API_KEY_ENV_VAR);
            return List.of();
        }
        try {
            String url = "https://www.rescuetime.com/anapi/data?key=" + apiKey
                    + "&perspective=interval&restrict_kind=overview&interval=day&format=json"
                    + "&restrict_begin=" + start.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    + "&restrict_end=" + end.format(DateTimeFormatter.ISO_LOCAL_DATE);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.info("rescuetime-sync: data request returned HTTP {}", response.statusCode());
                return List.of();
            }
            return sumSecondsByDay(response.body());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.info("rescuetime-sync: failed to fetch data: {}", e.getClass().getSimpleName());
            return List.of();
        }
    }

    private List<RescueTimeDailyTotal> sumSecondsByDay(String responseBody) throws IOException {
        JsonNode rows = new ObjectMapper().readTree(responseBody).path("rows");
        Map<LocalDate, Long> secondsByDay = new HashMap<>();
        if (rows.isArray()) {
            for (JsonNode row : rows) {
                LocalDate date = LocalDate.parse(row.get(0).asText().substring(0, 10));
                long seconds = row.get(1).asLong();
                secondsByDay.merge(date, seconds, Long::sum);
            }
        }
        return secondsByDay.entrySet().stream()
                .map(entry -> new RescueTimeDailyTotal(entry.getKey(), entry.getValue()))
                .toList();
    }
}
