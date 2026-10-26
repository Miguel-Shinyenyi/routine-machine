package com.routinemachine.core.github;

import com.routinemachine.core.observability.FailureDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Function;

// How the hub-sync and GitHub watering-hole clients build their requests to GitHub's public API. The token is OPTIONAL
// (GITHUB_TOKEN): without one GitHub allows 60 requests an hour per IP address, with one 5,000 (docs/hub-sync.md, "GitHub's
// rate limit"). Both clients call hard-coded https://api.github.com addresses, so the token only ever goes there; it is
// never logged, put in a metric or shown on the owner page. A blank token is the same as none.
public final class GitHubRequests {

    private static final Logger log = LoggerFactory.getLogger(GitHubRequests.class);

    public static final String TOKEN_ENV_VAR = "GITHUB_TOKEN";

    private GitHubRequests() {
    }

    public static HttpRequest get(URI uri, String userAgent, String token, Duration timeout) {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", userAgent);
        if (token != null && !token.isBlank()) {
            request.header("Authorization", "Bearer " + token.strip());
        }
        return request.GET().build();
    }

    // The token from the environment, trimmed, or null when it is unset or blank. Takes the lookup as a function so a test can
    // supply its own; production passes System::getenv.
    public static String tokenFrom(Function<String, String> environment) {
        String value = environment.apply(TOKEN_ENV_VAR);
        return value == null || value.isBlank() ? null : value.strip();
    }

    // One send, as a function, so the retry below can be tested without the network.
    @FunctionalInterface
    public interface Sender {
        HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException;
    }

    // How long the clients wait before the second try.
    public static final long RETRY_PAUSE_MILLIS = 5000;

    // Sends the request. If the CONNECTION failed (ConnectException, which is how the JDK reports a lookup or connect that
    // failed: ConnectException <- UnresolvedAddressException was seen in the first seconds after a container was created, while
    // the container's own resolver worked), it waits and tries once more. Anything GitHub actually answered, including a 403,
    // and any other failure, is returned or thrown at once: retrying a rate-limited call would only waste the quota.
    public static HttpResponse<String> sendWithOneRetry(Sender sender, HttpRequest request, long pauseMillis)
            throws IOException, InterruptedException {
        try {
            return sender.send(request);
        } catch (ConnectException first) {
            log.info("github: the connection failed ({}); trying once more in {} ms", FailureDescription.of(first), pauseMillis);
            Thread.sleep(pauseMillis);
            return sender.send(request);
        }
    }
}
