package com.routinemachine.core.github;

import com.routinemachine.core.observability.FailureDescription;
import com.routinemachine.core.observability.GitHubRateLimit;
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
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Real implementation over GitHub's public events API
// (GET /users/{username}/events/public), no credential needed since UMWAYI-style hub-sync
// reads are public anyway. This is a *backup* signal, named so in Phase 6's own framing
// (PROJECT.md), not a full commit history: the events API only returns a user's most recent
// public activity (undocumented cap, in practice roughly the last 90 days or ~300 events,
// whichever is smaller), covers push events to public repos only, and counts a push's commits
// against the day the push happened, not each commit's own authored date. Good enough to
// corroborate "did coding happen that day" when WakaTime/ActivityWatch data is missing, not
// to reconstruct exact history. Never throws: a failure logs one line and returns an empty
// list, same shape as every other real client in this package group.
@Component
public class GitHubCommitClientImpl implements GitHubCommitClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubCommitClientImpl.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String DEFAULT_USERNAME = "Miguel-Shinyenyi";
    private static final String USERNAME_ENV_VAR = "GITHUB_USERNAME";

    private final String username;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GitHubCommitClientImpl() {
        this(resolveUsername());
    }

    // Package-visible: lets tests point at a different public user without touching the real
    // one, same pattern as GitHubContentClientImpl's repoSlug override.
    GitHubCommitClientImpl(String username) {
        this.username = username;
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    private static String resolveUsername() {
        String configured = System.getenv(USERNAME_ENV_VAR);
        return (configured == null || configured.isBlank()) ? DEFAULT_USERNAME : configured;
    }

    @Override
    public List<GitHubCommitDailyTotal> fetchDailyCommitCounts(LocalDate start, LocalDate end) {
        try {
            HttpResponse<String> response = get(
                    "https://api.github.com/users/" + username + "/events/public?per_page=100");
            if (response.statusCode() != 200) {
                log.info("github-sync: fetching public events returned HTTP {}{}", response.statusCode(), limitNote(response));
                SyncMetrics.failure("github");
                return List.of();
            }
            SyncMetrics.success("github");
            return countPushedCommitsByDay(response.body(), start, end);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.info("github-sync: failed to fetch public events: {}", FailureDescription.of(e));
            SyncMetrics.failure("github");
            return List.of();
        }
    }

    private List<GitHubCommitDailyTotal> countPushedCommitsByDay(String responseBody, LocalDate start, LocalDate end)
            throws IOException {
        JsonNode events = objectMapper.readTree(responseBody);
        Map<LocalDate, Integer> countsByDay = new HashMap<>();
        if (events.isArray()) {
            for (JsonNode event : events) {
                if (!"PushEvent".equals(event.path("type").asText())) {
                    continue;
                }
                LocalDate date = OffsetDateTime.parse(event.path("created_at").asText()).toLocalDate();
                if (date.isBefore(start) || date.isAfter(end)) {
                    continue;
                }
                int commits = event.path("payload").path("commits").size();
                countsByDay.merge(date, commits, Integer::sum);
            }
        }
        return countsByDay.entrySet().stream()
                .map(entry -> new GitHubCommitDailyTotal(entry.getKey(), entry.getValue()))
                .toList();
    }

    private HttpResponse<String> get(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "routine-machine-github-sync")
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    // " (rate limited until ...)" when GitHub's headers say the quota is used up, else nothing.
    private static String limitNote(HttpResponse<?> response) {
        return GitHubRateLimit.describe(response.headers()).map(note -> " (" + note + ")").orElse("");
    }
}
