package com.routinemachine.core.observability;

import java.net.http.HttpHeaders;
import java.time.Instant;
import java.util.Optional;

// GitHub allows an unauthenticated caller 60 requests an hour per address. When that is used up it answers 403 and says,
// in X-RateLimit-Remaining and X-RateLimit-Reset (seconds since 1970), when the quota comes back. The GitHub and hub syncs
// cost 4 requests a run and run every 15 minutes, so a development machine that restarts the backend often can use the
// quota up (docs/observability.md, Backlog item I). This turns those headers into a few words for the log line, or says
// nothing when the headers are missing or unreadable: a guess about the quota would be worse than no sentence.
public final class GitHubRateLimit {

    private GitHubRateLimit() {
    }

    public static Optional<String> describe(HttpHeaders headers) {
        if (headers == null) {
            return Optional.empty();
        }
        Optional<String> remaining = headers.firstValue("x-ratelimit-remaining");
        Optional<String> reset = headers.firstValue("x-ratelimit-reset");
        if (remaining.isEmpty() || reset.isEmpty()) {
            return Optional.empty();
        }
        try {
            if (Long.parseLong(remaining.get().trim()) != 0) {
                return Optional.empty();
            }
            return Optional.of("rate limited until " + Instant.ofEpochSecond(Long.parseLong(reset.get().trim())));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
