package com.routinemachine.core.observability;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

// Observability, part 1: every request carries an id that appears on every log line it causes
// (through the MDC) and comes back on the response, so one request can be followed across logs and,
// later, across services.
class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    private String run(MockHttpServletRequest request, MockHttpServletResponse response, AtomicReference<String> seenInside)
            throws Exception {
        FilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seenInside.set(MDC.get(RequestIdFilter.MDC_KEY));
            }
        };
        filter.doFilter(request, response, chain);
        return response.getHeader(RequestIdFilter.HEADER);
    }

    @Test
    void generatesAnIdWhenTheRequestHasNone() throws Exception {
        AtomicReference<String> inside = new AtomicReference<>();

        String echoed = run(new MockHttpServletRequest(), new MockHttpServletResponse(), inside);

        assertThat(echoed).isNotBlank();
        assertThat(inside.get()).isEqualTo(echoed);
    }

    @Test
    void reusesAnIncomingIdSoOneRequestCanBeFollowedAcrossServices() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "abc-123_DEF.456");
        AtomicReference<String> inside = new AtomicReference<>();

        String echoed = run(request, new MockHttpServletResponse(), inside);

        assertThat(echoed).isEqualTo("abc-123_DEF.456");
        assertThat(inside.get()).isEqualTo("abc-123_DEF.456");
    }

    @Test
    void replacesAnIncomingIdThatCouldForgeALogLineOrIsTooLong() throws Exception {
        for (String hostile : new String[] {"id\nINFO fake line", "id with spaces", "<script>", "x".repeat(65), ""}) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader(RequestIdFilter.HEADER, hostile);
            AtomicReference<String> inside = new AtomicReference<>();

            String echoed = run(request, new MockHttpServletResponse(), inside);

            assertThat(echoed).as("for %s", hostile).isNotEqualTo(hostile).matches("[A-Za-z0-9._-]{1,64}");
            assertThat(inside.get()).isEqualTo(echoed);
        }
    }

    @Test
    void theIdIsRemovedFromTheMdcWhenTheRequestEnds() throws Exception {
        run(new MockHttpServletRequest(), new MockHttpServletResponse(), new AtomicReference<>());

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void theIdIsRemovedEvenWhenTheRequestFails() {
        FilterChain failing = (req, res) -> {
            throw new IllegalStateException("boom");
        };

        try {
            filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), failing);
        } catch (Exception expected) {
            // the failure is the point of the test
        }

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    // One line per request when it ends: what was asked (method and path, never the query string or a
    // body), how it went, how long it took, with the request id on it. Nothing about the person.
    private ListAppender<ILoggingEvent> captureLog() {
        Logger logger = (Logger) LoggerFactory.getLogger(RequestIdFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    @Test
    void logsOneLinePerRequestWithTheIdMethodPathStatusAndDurationButNoQueryString() throws Exception {
        ListAppender<ILoggingEvent> log = captureLog();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/granary/summary");
        request.setQueryString("note=secret-words&amount=5000");
        request.addHeader(RequestIdFilter.HEADER, "log-check-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        run(request, response, new AtomicReference<>());

        assertThat(log.list).hasSize(1);
        ILoggingEvent event = log.list.get(0);
        assertThat(event.getFormattedMessage()).isEqualTo("request completed");
        assertThat(event.getMDCPropertyMap()).containsEntry(RequestIdFilter.MDC_KEY, "log-check-1");
        assertThat(event.getKeyValuePairs()).extracting(kv -> kv.key + "=" + kv.value)
                .contains("method=GET", "path=/api/granary/summary", "status=200")
                .anySatisfy(kv -> assertThat(kv).startsWith("durationMs="))
                .noneSatisfy(kv -> assertThat(kv).contains("secret").contains("5000"));
    }

    @Test
    void doesNotLogTheMonitoringEndpointsThemselves() throws Exception {
        ListAppender<ILoggingEvent> log = captureLog();

        run(new MockHttpServletRequest("GET", "/actuator/prometheus"), new MockHttpServletResponse(), new AtomicReference<>());

        assertThat(log.list).isEmpty();
    }
}
