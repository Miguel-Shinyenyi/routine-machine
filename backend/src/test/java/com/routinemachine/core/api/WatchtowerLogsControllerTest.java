package com.routinemachine.core.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.UserRepository;
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
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The owner-only page's logs (docs/observability.md, part 5, piece 3): the backend reads Loki. The only text from a
// request that reaches the LogQL query is a level from a fixed list and an id of plain characters.
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"owner.email=watchtower-logs-owner@example.com",
        "watchtower.jaeger-ui-url=http://trace.example.test:16686/"})
class WatchtowerLogsControllerTest extends AbstractIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String TRACE = "0af7651916cd43dd8448eb211c80319c";
    private static final List<Map<String, String>> RECEIVED = new CopyOnWriteArrayList<>();
    private static final HttpServer LOKI = startLoki();

    private static HttpServer startLoki() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/loki/api/v1/query_range", exchange -> {
                RECEIVED.add(Stream.of(exchange.getRequestURI().getRawQuery().split("&"))
                        .map(pair -> pair.split("=", 2))
                        .collect(Collectors.toMap(p -> p[0], p -> URLDecoder.decode(p[1], StandardCharsets.UTF_8))));
                byte[] bytes = lokiAnswer().getBytes(StandardCharsets.UTF_8);
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

    // Two streams, as Loki returns them, with entries out of order across streams: the page must merge them newest first.
    private static String lokiAnswer() {
        try {
            String backendLine = JSON.writeValueAsString(Map.of("@timestamp", "t", "message", "request completed", "level", "INFO",
                    "logger_name", "com.example.Secretive", "thread_name", "http-nio-8080-exec-1", "requestId", "req-1", "trace_id", TRACE));
            String intelligenceLine = JSON.writeValueAsString(Map.of("message", "request completed", "level", "ERROR",
                    "logger", "request", "requestId", "req-2", "traceId", "not-a-trace-id"));
            String longLine = JSON.writeValueAsString(Map.of("message", "x".repeat(5000), "level", "WARN"));
            Map<String, Object> answer = Map.of("status", "success", "data", Map.of("resultType", "streams", "result", List.of(
                    Map.of("stream", Map.of("service", "backend", "level", "INFO"), "values", List.of(
                            List.of("1792916603000000000", backendLine),
                            List.of("1792916601000000000", "Picked up JAVA_TOOL_OPTIONS: not json"))),
                    Map.of("stream", Map.of("service", "intelligence-service", "level", "ERROR"), "values", List.of(
                            List.of("1792916602000000000", intelligenceLine),
                            List.of("1792916600000000000", longLine))))));
            return JSON.writeValueAsString(answer);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void loki(DynamicPropertyRegistry registry) {
        registry.add("watchtower.loki-base-url", () -> "http://127.0.0.1:" + LOKI.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        LOKI.stop(0);
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
        owner = account("watchtower-logs-owner@example.com");
        someoneElse = account("watchtower-logs-other@example.com");
    }

    private User account(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> userRepository.save(new User(email, "irrelevant-hash", null, null)));
    }

    private String lastQuery() {
        return RECEIVED.get(RECEIVED.size() - 1).get("query");
    }

    @Test
    void theOwnerGetsEntriesMergedNewestFirstWithOnlyTheFieldsThePageNeeds() throws Exception {
        mockMvc.perform(get("/api/watchtower/logs").with(asUser(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.entries[*].message").value(
                        org.hamcrest.Matchers.contains("request completed", "request completed", "Picked up JAVA_TOOL_OPTIONS: not json", "x".repeat(1000) + "…")))
                .andExpect(jsonPath("$.entries[0].service").value("backend"))
                .andExpect(jsonPath("$.entries[0].level").value("INFO"))
                .andExpect(jsonPath("$.entries[0].requestId").value("req-1"))
                .andExpect(jsonPath("$.entries[0].traceId").value(TRACE))
                .andExpect(jsonPath("$.entries[0].time").value("2026-10-25T08:23:23Z"))
                .andExpect(jsonPath("$.entries[1].service").value("intelligence-service"))
                .andExpect(content().string(not(containsString("Secretive"))))
                .andExpect(content().string(not(containsString("thread_name"))));
    }

    @Test
    void aTraceLinkIsOfferedOnlyForARealTraceIdAndOnlyWhenAJaegerAddressIsConfigured() throws Exception {
        mockMvc.perform(get("/api/watchtower/logs").with(asUser(owner.getId())))
                .andExpect(jsonPath("$.entries[0].traceUrl").value("http://trace.example.test:16686/trace/" + TRACE))
                .andExpect(jsonPath("$.entries[1].traceUrl").doesNotExist());
    }

    @Test
    void theLogQlIsBuiltFromTheFixedServicesAndOnlyTheTwoCheckedValues() throws Exception {
        mockMvc.perform(get("/api/watchtower/logs").with(asUser(owner.getId()))).andExpect(status().isOk());
        assertThat(lastQuery()).isEqualTo("{service=~\"backend|intelligence-service\"}");

        mockMvc.perform(get("/api/watchtower/logs").param("level", "ERROR").with(asUser(owner.getId()))).andExpect(status().isOk());
        assertThat(lastQuery()).isEqualTo("{service=~\"backend|intelligence-service\",level=\"ERROR\"}");

        mockMvc.perform(get("/api/watchtower/logs").param("requestId", "req-1.a_b").with(asUser(owner.getId()))).andExpect(status().isOk());
        assertThat(lastQuery()).isEqualTo("{service=~\"backend|intelligence-service\"} |= \"req-1.a_b\"");

        mockMvc.perform(get("/api/watchtower/logs").param("level", "WARNING").param("requestId", TRACE).with(asUser(owner.getId())))
                .andExpect(status().isOk());
        assertThat(lastQuery()).isEqualTo("{service=~\"backend|intelligence-service\",level=\"WARNING\"} |= \"" + TRACE + "\"");
        assertThat(RECEIVED.get(RECEIVED.size() - 1).get("limit")).isEqualTo("100");
        assertThat(RECEIVED.get(RECEIVED.size() - 1).get("direction")).isEqualTo("backward");
    }

    @Test
    void anythingThatCouldChangeTheQueryIsRefusedBeforeLokiIsAsked() throws Exception {
        String[] badIds = {"a\"b", "a} |~ \".*", "a\\b", "a b", ".*", "a|b", "a\nb", "{service=~\".+\"}", "x".repeat(65), ""};
        for (String id : badIds) {
            mockMvc.perform(get("/api/watchtower/logs").param("requestId", id).with(asUser(owner.getId())))
                    .andExpect(status().isBadRequest());
        }
        for (String level : new String[] {"error", "ERROR\"}", "ALL", "INFO,ERROR", ".+"}) {
            mockMvc.perform(get("/api/watchtower/logs").param("level", level).with(asUser(owner.getId())))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/api/watchtower/logs").param("range", "9999d").with(asUser(owner.getId())))
                .andExpect(status().isBadRequest());

        assertThat(RECEIVED).isEmpty();
    }

    @Test
    void aRequestCannotChangeWhichServicesAreRead() throws Exception {
        mockMvc.perform(get("/api/watchtower/logs").param("service", "postgres").param("query", "{job=\"x\"}")
                .with(asUser(owner.getId()))).andExpect(status().isOk());

        assertThat(lastQuery()).isEqualTo("{service=~\"backend|intelligence-service\"}");
    }

    @Test
    void aPersonWhoIsNotTheOwnerGetsA404EvenWithBadParametersAndLokiIsNeverCalled() throws Exception {
        mockMvc.perform(get("/api/watchtower/logs").with(asUser(someoneElse.getId()))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/watchtower/logs").param("requestId", "a\"b").with(asUser(someoneElse.getId())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/watchtower/logs")).andExpect(status().isNotFound());

        assertThat(RECEIVED).isEmpty();
    }

    @Test
    void theWindowFollowsTheRange() throws Exception {
        mockMvc.perform(get("/api/watchtower/logs").param("range", "24h").with(asUser(owner.getId())))
                .andExpect(jsonPath("$.range").value("24h"));
        Map<String, String> sent = RECEIVED.get(0);
        long seconds = (Long.parseLong(sent.get("end")) - Long.parseLong(sent.get("start"))) / 1_000_000_000L;
        assertThat(seconds).isBetween(86_390L, 86_410L);
    }

    @Test
    void entriesAreCappedAtOneHundredEvenIfLokiSendsMore() throws Exception {
        // The stand-in sends 4 entries; the cap itself is proved on the constant the query and the trim both use.
        assertThat(com.routinemachine.core.service.WatchtowerLogsService.MAX_ENTRIES).isEqualTo(100);
        mockMvc.perform(get("/api/watchtower/logs").with(asUser(owner.getId())))
                .andExpect(jsonPath("$.entries").value(hasSize(4)));
    }
}
