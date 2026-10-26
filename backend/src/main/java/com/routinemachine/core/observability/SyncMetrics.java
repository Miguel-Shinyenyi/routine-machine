package com.routinemachine.core.observability;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

// What the integrations (hub, WakaTime, ActivityWatch, GitHub, RescueTime) report about themselves:
// each fetch counted as success or failure, how many failures in a row, and when the last success was.
// Tags are the source and the outcome only, never a user. Uses Micrometer's global registry, which Spring
// Boot binds its own registry to, so the clients (which are built without Spring wiring) can call it.
public final class SyncMetrics {

    private static final ConcurrentMap<String, AtomicInteger> CONSECUTIVE_FAILURES = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, AtomicLong> LAST_SUCCESS_SECONDS = new ConcurrentHashMap<>();

    private SyncMetrics() {
    }

    public static void success(String source) {
        Metrics.counter("sync.runs", "source", source, "outcome", "success").increment();
        consecutiveFailures(source).set(0);
        lastSuccess(source).set(System.currentTimeMillis() / 1000);
    }

    public static void failure(String source) {
        Metrics.counter("sync.runs", "source", source, "outcome", "failure").increment();
        consecutiveFailures(source).incrementAndGet();
    }

    // Gauges hold their value weakly, so the holders live in these maps for the life of the process.
    // computeIfAbsent registers the gauge once per source; later calls reuse the same holder.
    private static AtomicInteger consecutiveFailures(String source) {
        return CONSECUTIVE_FAILURES.computeIfAbsent(source, s -> {
            AtomicInteger holder = new AtomicInteger();
            Metrics.gauge("sync.consecutive.failures", Tags.of("source", s), holder);
            return holder;
        });
    }

    private static AtomicLong lastSuccess(String source) {
        return LAST_SUCCESS_SECONDS.computeIfAbsent(source, s -> {
            AtomicLong holder = new AtomicLong();
            Metrics.gauge("sync.last.success.timestamp.seconds", Tags.of("source", s), holder);
            return holder;
        });
    }
}
