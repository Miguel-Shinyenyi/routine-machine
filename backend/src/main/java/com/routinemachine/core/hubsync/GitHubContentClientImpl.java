package com.routinemachine.core.hubsync;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class GitHubContentClientImpl implements GitHubContentClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubContentClientImpl.class);
    private static final String DEFAULT_REPO = "Miguel-Shinyenyi/UMWAYI";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final String repoSlug;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GitHubContentClientImpl() {
        this(DEFAULT_REPO);
    }

    // Package-visible: lets tests point at a different public repo without touching the real
    // UMWAYI remote, same pattern as HubJournalReader's constructor-injected repo URL.
    GitHubContentClientImpl(String repoSlug) {
        this.repoSlug = repoSlug;
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    @Override
    public List<GitHubFile> listDirectory(String path) {
        try {
            HttpResponse<String> response = get("https://api.github.com/repos/" + repoSlug + "/contents/" + path);
            if (response.statusCode() != 200) {
                log.info("hub-sync: listing {} returned HTTP {}", path, response.statusCode());
                return List.of();
            }
            JsonNode items = objectMapper.readTree(response.body());
            List<GitHubFile> files = new ArrayList<>();
            if (items.isArray()) {
                for (JsonNode item : items) {
                    if ("file".equals(item.path("type").asText())) {
                        files.add(new GitHubFile(item.path("path").asText(), item.path("sha").asText()));
                    }
                }
            }
            return files;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.info("hub-sync: failed to list {}: {}", path, e.getClass().getSimpleName());
            return List.of();
        }
    }

    @Override
    public Optional<LocalDate> firstCommitDate(String path) {
        try {
            HttpResponse<String> response = get(
                    "https://api.github.com/repos/" + repoSlug + "/commits?path=" + path + "&per_page=100");
            if (response.statusCode() != 200) {
                log.info("hub-sync: commit history for {} returned HTTP {}", path, response.statusCode());
                return Optional.empty();
            }
            JsonNode commits = objectMapper.readTree(response.body());
            if (!commits.isArray() || commits.isEmpty()) {
                return Optional.empty();
            }
            // GitHub returns commits newest-first; the oldest in this (first) page is the
            // closest available approximation of the file's first commit. A path with more
            // than 100 commits would need pagination this doesn't do -- unlikely for a single
            // essay file, worth documenting rather than silently handling.
            JsonNode oldest = commits.get(commits.size() - 1);
            String isoDate = oldest.path("commit").path("committer").path("date").asText();
            if (isoDate.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(OffsetDateTime.parse(isoDate).toLocalDate());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.info("hub-sync: failed to read commit history for {}: {}", path, e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private HttpResponse<String> get(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "routine-machine-hub-sync")
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
