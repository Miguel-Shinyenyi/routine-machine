package com.routinemachine.core.api;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.UserRepository;
import com.routinemachine.core.service.WatchtowerSyncService;
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
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.routinemachine.core.TestTokens.asUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The owner-only page's sync status (docs/observability.md, part 5, piece 4): each source's switch and newest data
// from the database, its run state from Prometheus, and one result word. Prometheus is asked three fixed questions.
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"owner.email=watchtower-sync-owner@example.com",
        "hub-sync.enabled=true", "wakatime-sync.enabled=true", "activitywatch-sync.enabled=true",
        "github-sync.enabled=true", "rescuetime-sync.enabled=false", "health-ingest.token=test-only-value"})
class WatchtowerSyncControllerTest extends AbstractIntegrationTest {

    private static final long HUB_SUCCESS = 1792916600L;
    private static final List<String> RECEIVED = new CopyOnWriteArrayList<>();
    private static final HttpServer PROMETHEUS = startPrometheus();

    private static HttpServer startPrometheus() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/v1/query", exchange -> {
                String query = URLDecoder.decode(exchange.getRequestURI().getRawQuery().substring("query=".length()),
                        StandardCharsets.UTF_8);
                RECEIVED.add(query);
                String result;
                if (query.contains("sync_consecutive_failures")) {
                    result = vector(sample("hub", "0"), sample("wakatime", "3"), sample("github", "0"));
                } else if (query.contains("sync_last_success_timestamp_seconds")) {
                    result = vector(sample("hub", HUB_SUCCESS + ""), sample("github", "1792916000"));
                } else {
                    result = vector(
                            "{\"metric\":{\"source\":\"hub\",\"outcome\":\"success\"},\"value\":[1,\"12.4\"]}",
                            "{\"metric\":{\"source\":\"wakatime\",\"outcome\":\"failure\"},\"value\":[1,\"7.6\"]}",
                            "{\"metric\":{\"source\":\"github\",\"outcome\":\"success\"},\"value\":[1,\"4\"]}");
                }
                byte[] bytes = result.getBytes(StandardCharsets.UTF_8);
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

    private static String sample(String source, String value) {
        return "{\"metric\":{\"source\":\"" + source + "\"},\"value\":[1,\"" + value + "\"]}";
    }

    private static String vector(String... samples) {
        return "{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":[" + String.join(",", samples) + "]}}";
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
        owner = account("watchtower-sync-owner@example.com");
        someoneElse = account("watchtower-sync-other@example.com");
    }

    private User account(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> userRepository.save(new User(email, "irrelevant-hash", null, null)));
    }

    @Test
    void theOwnerGetsEverySourceWithItsRunStateFromPrometheus() throws Exception {
        mockMvc.perform(get("/api/watchtower/sync").with(asUser(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.sources").value(hasSize(6)))
                .andExpect(jsonPath("$.sources[?(@.source=='HUB')].enabled").value(true))
                .andExpect(jsonPath("$.sources[?(@.source=='HUB')].consecutiveFailures").value(0))
                .andExpect(jsonPath("$.sources[?(@.source=='HUB')].lastSuccessAt").value(Instant.ofEpochSecond(HUB_SUCCESS).toString()))
                .andExpect(jsonPath("$.sources[?(@.source=='HUB')].successes24h").value(12))
                .andExpect(jsonPath("$.sources[?(@.source=='WAKATIME')].failures24h").value(8))
                .andExpect(jsonPath("$.sources[?(@.source=='WAKATIME')].successes24h").value(0));
    }

    @Test
    void eachSourceGetsOneResultWord() throws Exception {
        mockMvc.perform(get("/api/watchtower/sync").with(asUser(owner.getId())))
                .andExpect(jsonPath("$.sources[?(@.source=='HUB')].result").value("ok"))
                .andExpect(jsonPath("$.sources[?(@.source=='GITHUB')].result").value("ok"))
                .andExpect(jsonPath("$.sources[?(@.source=='WAKATIME')].result").value("failing"))
                .andExpect(jsonPath("$.sources[?(@.source=='ACTIVITYWATCH')].result").value("never succeeded"))
                .andExpect(jsonPath("$.sources[?(@.source=='RESCUETIME')].result").value("off"))
                .andExpect(jsonPath("$.sources[?(@.source=='HEALTH')].result").value("push only"));
    }

    @Test
    void aSourceThatHasNeverRunHasNoRunStateRatherThanAZero() throws Exception {
        mockMvc.perform(get("/api/watchtower/sync").with(asUser(owner.getId())))
                .andExpect(jsonPath("$.sources[?(@.source=='ACTIVITYWATCH')].consecutiveFailures").value(org.hamcrest.Matchers.contains((Object) null)))
                .andExpect(jsonPath("$.sources[?(@.source=='ACTIVITYWATCH')].lastSuccessAt").value(org.hamcrest.Matchers.contains((Object) null)));
    }

    @Test
    void prometheusIsAskedOnlyTheThreeFixedQuestions_evenWhenTheRequestTriesToSendItsOwn() throws Exception {
        mockMvc.perform(get("/api/watchtower/sync").param("query", "up").with(asUser(owner.getId())))
                .andExpect(status().isOk());

        assertThat(RECEIVED).containsExactlyInAnyOrderElementsOf(WatchtowerSyncService.QUERIES);
    }

    @Test
    void aPersonWhoIsNotTheOwnerGetsA404AndPrometheusIsNeverCalled() throws Exception {
        mockMvc.perform(get("/api/watchtower/sync").with(asUser(someoneElse.getId())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/watchtower/sync"))
                .andExpect(status().isNotFound());

        assertThat(RECEIVED).isEmpty();
    }
}
