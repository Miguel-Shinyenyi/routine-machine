package com.routinemachine.core.github;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

// Found on 25 Oct 2026 once the failure cause was logged: the first call after a container is created sometimes fails with
// ConnectException <- UnresolvedAddressException (the JVM could not resolve api.github.com for a moment, although the
// container's own resolver could). A connection that failed is tried once more after a short pause. Anything GitHub actually
// answered, including a 403, and any other kind of failure, is not retried.
class GitHubSendTest {

    private static final HttpRequest REQUEST = HttpRequest.newBuilder(URI.create("https://api.github.com/")).timeout(Duration.ofSeconds(1)).GET().build();
    @SuppressWarnings("unchecked")
    private final HttpResponse<String> response = mock(HttpResponse.class);

    @Test
    void aCallThatWorksIsMadeOnce() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpResponse<String> result = GitHubRequests.sendWithOneRetry(request -> { calls.incrementAndGet(); return response; }, REQUEST, 1);
        assertThat(result).isSameAs(response);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void aConnectionThatFailedIsTriedOnceMoreAndTheSecondAnswerIsUsed() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpResponse<String> result = GitHubRequests.sendWithOneRetry(request -> {
            if (calls.incrementAndGet() == 1) {
                throw new ConnectException("could not resolve");
            }
            return response;
        }, REQUEST, 1);
        assertThat(result).isSameAs(response);
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void aConnectionThatFailsTwiceThrowsTheSecondFailure() {
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> GitHubRequests.sendWithOneRetry(request -> { throw new ConnectException("attempt " + calls.incrementAndGet()); }, REQUEST, 1))
                .isInstanceOf(ConnectException.class).hasMessage("attempt 2");
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void anyOtherFailureIsNotRetried() {
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> GitHubRequests.sendWithOneRetry(request -> { calls.incrementAndGet(); throw new IOException("reset by peer"); }, REQUEST, 1))
                .isInstanceOf(IOException.class).hasMessage("reset by peer");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void anInterruptionIsNotRetried() {
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> GitHubRequests.sendWithOneRetry(request -> { calls.incrementAndGet(); throw new InterruptedException(); }, REQUEST, 1))
                .isInstanceOf(InterruptedException.class);
        assertThat(calls.get()).isEqualTo(1);
    }
}
