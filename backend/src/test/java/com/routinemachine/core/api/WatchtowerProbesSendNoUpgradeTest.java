package com.routinemachine.core.api;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.UserRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.routinemachine.core.TestTokens.asUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

// Found in the logs (25 Oct 2026): the intelligence service's server (uvicorn) logged "Unsupported upgrade request." at
// WARNING every time the owner page loaded. Java's HTTP client offers an HTTP/2 upgrade (Upgrade: h2c) on plain http
// calls unless told to speak HTTP/1.1, and the page's probes go to the services over plain http. None of the owner page's
// outgoing calls may send an upgrade header.
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "owner.email=noupgrade-owner@example.com")
class WatchtowerProbesSendNoUpgradeTest extends AbstractIntegrationTest {

    private static final List<String> UPGRADING = new CopyOnWriteArrayList<>();
    private static final List<String> SEEN = new CopyOnWriteArrayList<>();
    private static final HttpServer SERVICES = startServices();

    private static HttpServer startServices() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                SEEN.add(path);
                if (exchange.getRequestHeaders().containsKey("Upgrade") || exchange.getRequestHeaders().containsKey("Http2-settings")) {
                    UPGRADING.add(path);
                }
                byte[] body = "{\"status\":\"success\",\"data\":{\"result\":[]},\"openapi\":\"3.1.0\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void everyAddressPointsAtTheStandIn(DynamicPropertyRegistry registry) {
        String base = "http://127.0.0.1:" + SERVICES.getAddress().getPort();
        for (String name : List.of("intelligence-url", "prometheus-url", "alertmanager-url", "loki-url", "jaeger-url", "grafana-url")) {
            registry.add("watchtower." + name, () -> base + "/probe/" + name);
        }
        registry.add("watchtower.prometheus-base-url", () -> base + "/prometheus");
        registry.add("watchtower.loki-base-url", () -> base + "/loki");
        registry.add("watchtower.intelligence-docs-url", () -> base + "/docs/openapi.json");
    }

    @AfterAll
    static void stop() {
        SERVICES.stop(0);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;

    @Test
    void noOutgoingCallOfTheOwnerPageOffersAnUpgrade() throws Exception {
        User owner = userRepository.findByEmailIgnoreCase("noupgrade-owner@example.com")
                .orElseGet(() -> userRepository.save(new User("noupgrade-owner@example.com", "irrelevant-hash", null, null)));

        for (String address : List.of("/api/watchtower/services", "/api/watchtower/metrics", "/api/watchtower/logs",
                "/api/watchtower/sync", "/api/watchtower/intelligence-api-docs")) {
            mockMvc.perform(get(address).with(asUser(owner.getId())));
        }

        // Each of the five reached the stand-in (so the check is not vacuous): the probes, Prometheus, Loki and the docs.
        assertThat(SEEN).anyMatch(p -> p.startsWith("/probe/intelligence-url"));
        assertThat(SEEN).anyMatch(p -> p.startsWith("/prometheus"));
        assertThat(SEEN).anyMatch(p -> p.startsWith("/loki"));
        assertThat(SEEN).anyMatch(p -> p.startsWith("/docs"));
        assertThat(UPGRADING).as("requests that offered an HTTP upgrade").isEmpty();
    }
}
