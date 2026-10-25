package com.routinemachine.core.github;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// GitHub allows an unauthenticated caller 60 requests an hour per address (docs/hub-sync.md, "GitHub's rate limit"). With a
// token in GITHUB_TOKEN it allows 5,000. The token is optional: with none set, the syncs behave exactly as before.
class GitHubRequestsTest {

    private static final URI URL = URI.create("https://api.github.com/users/someone/events/public");

    @Test
    void withNoTokenTheRequestCarriesNoAuthorization() {
        HttpRequest request = GitHubRequests.get(URL, "routine-machine-test", null, Duration.ofSeconds(5));
        assertThat(request.headers().firstValue("Authorization")).isEmpty();
        assertThat(request.headers().firstValue("Accept")).contains("application/vnd.github+json");
        assertThat(request.headers().firstValue("User-Agent")).contains("routine-machine-test");
        assertThat(request.method()).isEqualTo("GET");
        assertThat(request.timeout()).contains(Duration.ofSeconds(5));
    }

    @Test
    void withATokenTheRequestIsAuthenticated() {
        HttpRequest request = GitHubRequests.get(URL, "ua", "ghp_example", Duration.ofSeconds(5));
        assertThat(request.headers().firstValue("Authorization")).contains("Bearer ghp_example");
    }

    @Test
    void aBlankTokenIsTheSameAsNone() {
        for (String blank : new String[] {"", "   ", "\n"}) {
            assertThat(GitHubRequests.get(URL, "ua", blank, Duration.ofSeconds(5)).headers().firstValue("Authorization"))
                    .as("token %s", blank.isBlank() ? "blank" : blank).isEmpty();
        }
    }

    @Test
    void aTokenWithStrayWhitespaceIsTrimmed() {
        HttpRequest request = GitHubRequests.get(URL, "ua", "  ghp_example\n", Duration.ofSeconds(5));
        assertThat(request.headers().firstValue("Authorization")).contains("Bearer ghp_example");
    }

    @Test
    void theTokenIsReadFromGITHUB_TOKENAndFromNowhereElse() {
        assertThat(GitHubRequests.tokenFrom(Map.of("GITHUB_TOKEN", " abc ")::get)).isEqualTo("abc");
        assertThat(GitHubRequests.tokenFrom(Map.of("GITHUB_USERNAME", "x", "TOKEN", "nope")::get)).isNull();
        assertThat(GitHubRequests.tokenFrom(Map.of("GITHUB_TOKEN", "  ")::get)).isNull();
        assertThat(GitHubRequests.tokenFrom(key -> null)).isNull();
    }
}
