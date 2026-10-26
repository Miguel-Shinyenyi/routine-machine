package com.routinemachine.core.observability;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// Observability, part 3: each integration's fetches are counted as success or failure, with a running
// count of failures in a row (what the "a source failing three runs in a row" alert reads) and the time
// of the last success. The sources use the global registry, which Spring Boot binds its own to.
class SyncMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @BeforeEach
    void addRegistry() {
        Metrics.addRegistry(registry);
    }

    @AfterEach
    void removeRegistry() {
        Metrics.removeRegistry(registry);
    }

    private double runs(String source, String outcome) {
        var counter = registry.find("sync.runs").tag("source", source).tag("outcome", outcome).counter();
        return counter == null ? 0 : counter.count();
    }

    private double consecutiveFailures(String source) {
        var gauge = registry.find("sync.consecutive.failures").tag("source", source).gauge();
        return gauge == null ? -1 : gauge.value();
    }

    @Test
    void countsSuccessesAndFailuresPerSource() {
        SyncMetrics.success("t-count-a");
        SyncMetrics.success("t-count-a");
        SyncMetrics.failure("t-count-a");
        SyncMetrics.failure("t-count-b");

        assertThat(runs("t-count-a", "success")).isEqualTo(2);
        assertThat(runs("t-count-a", "failure")).isEqualTo(1);
        assertThat(runs("t-count-b", "failure")).isEqualTo(1);
        assertThat(runs("t-count-b", "success")).isZero();
    }

    @Test
    void consecutiveFailuresCountUpAndResetOnASuccess() {
        SyncMetrics.failure("t-streak");
        SyncMetrics.failure("t-streak");
        SyncMetrics.failure("t-streak");
        assertThat(consecutiveFailures("t-streak")).isEqualTo(3);

        SyncMetrics.success("t-streak");
        assertThat(consecutiveFailures("t-streak")).isZero();
    }

    @Test
    void recordsWhenTheLastSuccessHappened() {
        long before = System.currentTimeMillis() / 1000;

        SyncMetrics.success("t-last");

        var gauge = registry.find("sync.last.success.timestamp.seconds").tag("source", "t-last").gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isBetween((double) before, (double) before + 5);
    }

    @Test
    void theOnlyTagsAreTheSourceAndTheOutcome() {
        SyncMetrics.success("t-tags");
        SyncMetrics.failure("t-tags");

        registry.getMeters().stream().filter(m -> m.getId().getName().startsWith("sync."))
                .forEach(meter -> assertThat(meter.getId().getTags()).extracting(tag -> tag.getKey())
                        .isSubsetOf("source", "outcome"));
    }
}
