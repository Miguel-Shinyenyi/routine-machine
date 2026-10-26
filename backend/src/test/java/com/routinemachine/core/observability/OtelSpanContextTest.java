package com.routinemachine.core.observability;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// Metric exemplars: a latency observation made inside a trace carries that trace's id, so a slow bucket on a
// chart leads to its trace. With no trace running (tracing is off unless TRACING_ENABLED=true) it carries nothing.
class OtelSpanContextTest {

    private static final String TRACE_ID = "0af7651916cd43dd8448eb211c80319c";
    private static final String SPAN_ID = "b7ad6b7169203331";

    private final OtelSpanContext spanContext = new OtelSpanContext();

    private static Scope enter(byte traceFlags) {
        return Span.wrap(SpanContext.create(TRACE_ID, SPAN_ID, TraceFlags.fromByte(traceFlags), TraceState.getDefault()))
                .makeCurrent();
    }

    @Test
    void insideASampledTraceItReportsTheTraceAndSpanIds() {
        try (Scope ignored = enter(TraceFlags.getSampled().asByte())) {
            assertThat(spanContext.getCurrentTraceId()).isEqualTo(TRACE_ID);
            assertThat(spanContext.getCurrentSpanId()).isEqualTo(SPAN_ID);
            assertThat(spanContext.isCurrentSpanSampled()).isTrue();
        }
    }

    @Test
    void anUnsampledTraceIsReportedAsNotSampledSoNoExemplarIsKept() {
        try (Scope ignored = enter(TraceFlags.getDefault().asByte())) {
            assertThat(spanContext.isCurrentSpanSampled()).isFalse();
        }
    }

    @Test
    void outsideAnyTraceItReportsNothing() {
        assertThat(spanContext.getCurrentTraceId()).isNull();
        assertThat(spanContext.getCurrentSpanId()).isNull();
        assertThat(spanContext.isCurrentSpanSampled()).isFalse();
    }
}
