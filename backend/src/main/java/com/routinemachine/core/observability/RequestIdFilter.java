package com.routinemachine.core.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

// Gives every request an id, puts it in the MDC so every log line it causes carries it, and echoes it
// on the response. An incoming X-Request-Id is reused (so a request can be followed from the frontend
// through the backend and the intelligence service) only if it is short and plain; anything else could
// forge or break a log line and is replaced. Runs before Spring Security, so refused requests get an
// id too.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String requestId = incoming != null && SAFE_ID.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        long startedAt = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            logCompleted(request, response, startedAt);
            MDC.remove(MDC_KEY);
        }
    }

    // One line when the request ends, with the id from the MDC. Method and path only: never the query
    // string, a header or a body, so no note text, amount or address can reach the logs through here.
    // The monitoring endpoints are left out so a scrape every few seconds does not drown real lines.
    private void logCompleted(HttpServletRequest request, HttpServletResponse response, long startedAt) {
        String path = request.getRequestURI();
        if (path.startsWith("/actuator/")) {
            return;
        }
        log.atInfo()
                .addKeyValue("method", request.getMethod())
                .addKeyValue("path", path)
                .addKeyValue("status", response.getStatus())
                .addKeyValue("durationMs", (System.nanoTime() - startedAt) / 1_000_000)
                .log("request completed");
    }
}
