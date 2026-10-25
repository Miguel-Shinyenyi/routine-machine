package com.routinemachine.core.api;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.UserRepository;
import com.routinemachine.core.service.WatchtowerMetricsService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.routinemachine.core.TestTokens.asUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The owner-only page's charts (docs/observability.md, part 5, piece 2): the backend reads Prometheus with a
// fixed list of queries. A request chooses only the time range; no query text from a request reaches Prometheus.
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "owner.email=watchtower-metrics-owner@example.com")
class WatchtowerMetricsControllerTest extends AbstractIntegrationTest {

    // A stand-in Prometheus. It records every query_range request and answers per query.
    private static final List<Map<String, String>> RECEIVED = new CopyOnWriteArrayList<>();
    private static final HttpServer PROMETHEUS = startPrometheus();

    private static HttpServer startPrometheus() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/v1/query_range", exchange -> {
                Map<String, String> params = Stream.of(exchange.getRequestURI().getRawQuery().split("&"))
                        .map(pair -> pair.split("=", 2))
                        .collect(Collectors.toMap(p -> p[0], p -> URLDecoder.decode(p[1], StandardCharsets.UTF_8)));
                RECEIVED.add(params);
                String query = params.get("query");
                String body;
                if (query.contains("assessment_duration_seconds_bucket")) {
                    body = "{\"status\":\"error\",\"errorType\":\"bad_data\",\"error\":\"boom\"}";
                } else if (query.contains("assessment_tokens_total")) {
                    body = matrix("{\"direction\":\"input\"}", "[1792916600,\"NaN\"],[1792916660,\"12.5\"],[1792916720,\"+Inf\"]");
                } else if (query.contains("credits_events_total") && !query.contains("rule_version")) {
                    body = matrix("{\"status\":\"accepted\"}", "[1792916600,\"3\"],[1792916660,\"4\"]");
                } else if (query.contains("sync_runs_total")) {
                    String many = Stream.iterate(0, i -> i + 1).limit(40)
                            .map(i -> "{\"metric\":{\"source\":\"s" + i + "\",\"outcome\":\"success\"},\"values\":[[1792916600,\"1\"]]}")
                            .collect(Collectors.joining(","));
                    body = "{\"status\":\"success\",\"data\":{\"resultType\":\"matrix\",\"result\":[" + many + "]}}";
                } else {
                    body = matrix("{}", "[1792916600,\"1\"]");
                }
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String matrix(String metric, String values) {
        return "{\"status\":\"success\",\"data\":{\"resultType\":\"matrix\",\"result\":[{\"metric\":" + metric
                + ",\"values\":[" + values + "]}]}}";
    }

    @DynamicPropertySource
    static void prometheus(DynamicPropertyRegistry registry) {
        registry.add("watchtower.prometheus-base-url", () -> "http://127.0.0.1:" + PROMETHEUS.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        PROMETHEUS.stop(0);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;

    private User owner;
    private User someoneElse;

    @BeforeEach
    void accounts() {
        RECEIVED.clear();
        owner = account("watchtower-metrics-owner@example.com");
        someoneElse = account("watchtower-metrics-other@example.com");
    }

    private User account(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> userRepository.save(new User(email, "irrelevant-hash", null, null)));
    }

    @Test
    void theOwnerGetsEachChartWithItsSeriesLabelledAndItsPointsParsed() throws Exception {
        mockMvc.perform(get("/api/watchtower/metrics").with(asUser(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.range").value("6h"))
                .andExpect(jsonPath("$.charts[?(@.id=='credits-by-status')].series[0].label").value("accepted"))
                .andExpect(jsonPath("$.charts[?(@.id=='credits-by-status')].series[0].points[0][0]").value(1792916600))
                .andExpect(jsonPath("$.charts[?(@.id=='credits-by-status')].series[0].points[1][1]").value(4.0));
    }

    @Test
    void everyQuerySentToPrometheusIsOneFromTheFixedList_evenWhenTheRequestTriesToSendItsOwn() throws Exception {
        mockMvc.perform(get("/api/watchtower/metrics").param("query", "up{job=\"x\"}").param("expr", "up")
                        .with(asUser(owner.getId())))
                .andExpect(status().isOk());

        List<String> allowed = WatchtowerMetricsService.QUERIES.stream().map(WatchtowerMetricsService.Query::promql).toList();
        assertThat(RECEIVED).hasSize(allowed.size());
        assertThat(RECEIVED.stream().map(r -> r.get("query"))).containsExactlyInAnyOrderElementsOf(allowed);
    }

    @Test
    void theRangeChoosesTheWindowAndStepAndAnythingElseIsRefused() throws Exception {
        mockMvc.perform(get("/api/watchtower/metrics").param("range", "24h").with(asUser(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.range").value("24h"));
        Map<String, String> first = RECEIVED.get(0);
        assertThat(Long.parseLong(first.get("end")) - Long.parseLong(first.get("start"))).isBetween(86_390L, 86_410L);

        mockMvc.perform(get("/api/watchtower/metrics").param("range", "9999d").with(asUser(owner.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aPersonWhoIsNotTheOwnerGetsA404AndPrometheusIsNeverCalled() throws Exception {
        mockMvc.perform(get("/api/watchtower/metrics").with(asUser(someoneElse.getId())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/watchtower/metrics"))
                .andExpect(status().isNotFound());

        assertThat(RECEIVED).isEmpty();
    }

    @Test
    void aFailingQueryLeavesOnlyItsOwnChartEmpty() throws Exception {
        mockMvc.perform(get("/api/watchtower/metrics").with(asUser(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.charts[?(@.id=='assessment-p95-latency')].series[*]").isEmpty())
                .andExpect(jsonPath("$.charts[?(@.id=='credits-by-status')].series[0].label").value("accepted"));
    }

    @Test
    void notANumberAndInfinityPointsAreDroppedAndTheSeriesPerChartAreCapped() throws Exception {
        mockMvc.perform(get("/api/watchtower/metrics").with(asUser(owner.getId())))
                .andExpect(jsonPath("$.charts[?(@.id=='assessment-tokens')].series[0].points[*]").value(hasSize(1)))
                .andExpect(jsonPath("$.charts[?(@.id=='assessment-tokens')].series[0].points[0][1]").value(12.5))
                .andExpect(jsonPath("$.charts[?(@.id=='sync-runs')].series[*].label").value(hasSize(WatchtowerMetricsService.MAX_SERIES)));
    }
}
