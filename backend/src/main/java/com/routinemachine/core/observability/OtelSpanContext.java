package com.routinemachine.core.observability;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import org.springframework.stereotype.Component;

// Metric exemplars (docs/observability.md): tells Micrometer's Prometheus registry which trace the current
// request belongs to, so a latency observation can carry its trace id. Spring Boot's own version of this needs
// Micrometer Tracing; this service is traced by the OpenTelemetry Java agent instead, so it reads the
// OpenTelemetry API, which the agent backs. With no agent (TRACING_ENABLED unset) the current span is invalid,
// every method reports "no trace", and no exemplar is attached.
@Component
public class OtelSpanContext implements io.prometheus.metrics.tracer.common.SpanContext {

    private static SpanContext current() {
        return Span.current().getSpanContext();
    }

    @Override
    public String getCurrentTraceId() {
        SpanContext context = current();
        return context.isValid() ? context.getTraceId() : null;
    }

    @Override
    public String getCurrentSpanId() {
        SpanContext context = current();
        return context.isValid() ? context.getSpanId() : null;
    }

    @Override
    public boolean isCurrentSpanSampled() {
        SpanContext context = current();
        return context.isValid() && context.isSampled();
    }

    @Override
    public void markCurrentSpanAsExemplar() {
        // Nothing to mark: sampling of exemplars is decided by Micrometer and the span's own sampled flag.
    }
}
