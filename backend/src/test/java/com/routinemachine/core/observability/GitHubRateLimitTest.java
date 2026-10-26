package com.routinemachine.core.observability;

import org.junit.jupiter.api.Test;

import java.net.http.HttpHeaders;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// Found on 25 Oct 2026: the GitHub and hub syncs were failing with HTTP 403 and nothing in the log said why. GitHub allows an
// unauthenticated caller 60 requests an hour per address, and answers 403 with headers saying when the quota comes back.
class GitHubRateLimitTest {

    private static HttpHeaders headers(Map<String, String> values) {
        Map<String, List<String>> map = new java.util.HashMap<>();
        values.forEach((name, value) -> map.put(name, List.of(value)));
        return HttpHeaders.of(map, (name, value) -> true);
    }

    @Test
    void anExhaustedQuotaSaysWhenItComesBack() {
        // 1792951403 is 2026-10-25T18:03:23Z
        HttpHeaders exhausted = headers(Map.of("x-ratelimit-remaining", "0", "x-ratelimit-reset", "1792951403"));
        assertThat(GitHubRateLimit.describe(exhausted)).contains("rate limited until 2026-10-25T18:03:23Z");
    }

    @Test
    void aQuotaWithRequestsLeftIsNotRateLimiting() {
        HttpHeaders fine = headers(Map.of("x-ratelimit-remaining", "12", "x-ratelimit-reset", "1792951403"));
        assertThat(GitHubRateLimit.describe(fine)).isEmpty();
    }

    @Test
    void headersThatAreMissingOrUnreadableSayNothingInsteadOfGuessing() {
        assertThat(GitHubRateLimit.describe(headers(Map.of()))).isEmpty();
        assertThat(GitHubRateLimit.describe(headers(Map.of("x-ratelimit-remaining", "0")))).isEmpty();
        assertThat(GitHubRateLimit.describe(headers(Map.of("x-ratelimit-remaining", "0", "x-ratelimit-reset", "soon")))).isEmpty();
        assertThat(GitHubRateLimit.describe(headers(Map.of("x-ratelimit-remaining", "lots", "x-ratelimit-reset", "1792951403")))).isEmpty();
        assertThat(GitHubRateLimit.describe(null)).isEmpty();
    }
}
