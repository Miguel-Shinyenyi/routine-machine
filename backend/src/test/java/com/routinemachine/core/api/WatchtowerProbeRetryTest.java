package com.routinemachine.core.api;

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
import java.util.concurrent.atomic.AtomicInteger;

import static com.routinemachine.core.TestTokens.asUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Loki's /ready was seen answering 503 for a moment, and the owner page then showed Loki as DOWN for that refresh,
// although it was up again by the next one (docs/observability.md). The cause was never found. So a probe that fails is
// tried once more after a short pause: a flap shorter than the pause is not shown, a real outage still is, and a healthy
// service is asked once.
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"owner.email=probe-retry-owner@example.com", "watchtower.probe-retry-pause-ms=50"})
class WatchtowerProbeRetryTest extends AbstractIntegrationTest {

    private static final AtomicInteger FLAP = new AtomicInteger();
    private static final AtomicInteger DOWN = new AtomicInteger();
    private static final AtomicInteger UP = new AtomicInteger();
    private static final HttpServer SERVICES = startServices();

    private static HttpServer startServices() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            // Answers 503 the first time it is asked after a reset, 200 after that.
            server.createContext("/flap", exchange -> {
                exchange.sendResponseHeaders(FLAP.incrementAndGet() == 1 ? 503 : 200, -1);
                exchange.close();
            });
            server.createContext("/down", exchange -> {
                DOWN.incrementAndGet();
                exchange.sendResponseHeaders(503, -1);
                exchange.close();
            });
            server.createContext("/up", exchange -> {
                UP.incrementAndGet();
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void probes(DynamicPropertyRegistry registry) {
        String base = "http://127.0.0.1:" + SERVICES.getAddress().getPort();
        registry.add("watchtower.loki-url", () -> base + "/flap");
        registry.add("watchtower.prometheus-url", () -> base + "/down");
        registry.add("watchtower.intelligence-url", () -> base + "/up");
    }

    @AfterAll
    static void stop() {
        SERVICES.stop(0);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void reset() {
        FLAP.set(0);
        DOWN.set(0);
        UP.set(0);
    }

    private User owner() {
        return userRepository.findByEmailIgnoreCase("probe-retry-owner@example.com")
                .orElseGet(() -> userRepository.save(new User("probe-retry-owner@example.com", "irrelevant-hash", null, null)));
    }

    @Test
    void aServiceThatFlapsForAMomentIsStillShownAsUp() throws Exception {
        mockMvc.perform(get("/api/watchtower/services").with(asUser(owner().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='loki')].status").value("UP"));
        assertThat(FLAP.get()).isEqualTo(2);
    }

    @Test
    void aServiceThatStaysDownIsShownAsDownAfterOneSecondTry() throws Exception {
        mockMvc.perform(get("/api/watchtower/services").with(asUser(owner().getId())))
                .andExpect(jsonPath("$[?(@.name=='prometheus')].status").value("DOWN"));
        assertThat(DOWN.get()).isEqualTo(2);
    }

    @Test
    void aHealthyServiceIsAskedOnce() throws Exception {
        mockMvc.perform(get("/api/watchtower/services").with(asUser(owner().getId())))
                .andExpect(jsonPath("$[?(@.name=='intelligence-service')].status").value("UP"));
        assertThat(UP.get()).isEqualTo(1);
    }
}
