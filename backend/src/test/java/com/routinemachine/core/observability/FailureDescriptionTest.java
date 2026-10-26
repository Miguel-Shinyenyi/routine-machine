package com.routinemachine.core.observability;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;

// The GitHub and hub clients used to log only the exception's class name ("ConnectException"), which is why the cause of
// their failures could not be found (docs/observability.md, Backlog item I). This is what they log now.
class FailureDescriptionTest {

    @Test
    void anExceptionWithNoMessageIsJustItsClassName() {
        assertThat(FailureDescription.of(new ConnectException())).isEqualTo("ConnectException");
    }

    @Test
    void aMessageFollowsTheClassName() {
        assertThat(FailureDescription.of(new ConnectException("Connection refused"))).isEqualTo("ConnectException: Connection refused");
    }

    @Test
    void theCauseChainIsShownSoTheRealReasonIsNotLost() {
        IOException failure = new IOException("outer", new UnknownHostException("api.github.com"));
        assertThat(FailureDescription.of(failure)).isEqualTo("IOException: outer <- UnknownHostException: api.github.com");
    }

    @Test
    void aCauseThatPointsBackDoesNotLoopForever() {
        IOException first = new IOException("a");
        IOException second = new IOException("b");
        first.initCause(second);
        second.initCause(first);
        assertThat(FailureDescription.of(first)).isEqualTo("IOException: a <- IOException: b");
    }

    @Test
    void aLongChainIsCutAtFourLinksAndALongMessageIsShortened() {
        Throwable chain = new RuntimeException("e0", new RuntimeException("e1", new RuntimeException("e2",
                new RuntimeException("e3", new RuntimeException("e4", new RuntimeException("e5"))))));
        assertThat(FailureDescription.of(chain)).isEqualTo(
                "RuntimeException: e0 <- RuntimeException: e1 <- RuntimeException: e2 <- RuntimeException: e3");

        String shortened = FailureDescription.of(new IOException("x".repeat(500)));
        assertThat(shortened.length()).isLessThanOrEqualTo(200);
        assertThat(shortened).endsWith("...");
    }

    @Test
    void nothingIsReportedAsUnknown() {
        assertThat(FailureDescription.of(null)).isEqualTo("unknown");
    }
}
