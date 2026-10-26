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

import static com.routinemachine.core.TestTokens.asUser;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The owner-only operations page (docs/observability.md, part 5). Only the owner may read it, and everyone
// else, signed in or not, gets a plain 404 and never a login prompt, so the page is not even known to exist.
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "owner.email=watchtower-owner@example.com")
class WatchtowerControllerTest extends AbstractIntegrationTest {

    // Stand-ins for the services the page probes: one answers 200, one answers 503.
    private static final HttpServer SERVICES = startServices();

    private static HttpServer startServices() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/up", exchange -> {
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            });
            server.createContext("/down", exchange -> {
                exchange.sendResponseHeaders(503, -1);
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
        registry.add("watchtower.intelligence-url", () -> base + "/up");
        registry.add("watchtower.prometheus-url", () -> base + "/down");
        // loki, jaeger, grafana, alertmanager are left unset on purpose
    }

    @AfterAll
    static void stopServices() {
        SERVICES.stop(0);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;

    private User owner;
    private User someoneElse;

    @BeforeEach
    void accounts() {
        owner = account("watchtower-owner@example.com");
        someoneElse = account("watchtower-other@example.com");
    }

    private User account(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> userRepository.save(new User(email, "irrelevant-hash", null, null)));
    }

    @Test
    void theOwnerSeesEachServiceAsUpDownOrNotConfigured() throws Exception {
        mockMvc.perform(get("/api/watchtower/services").with(asUser(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='backend')].status").value("UP"))
                .andExpect(jsonPath("$[?(@.name=='database')].status").value("UP"))
                .andExpect(jsonPath("$[?(@.name=='intelligence-service')].status").value("UP"))
                .andExpect(jsonPath("$[?(@.name=='prometheus')].status").value("DOWN"))
                .andExpect(jsonPath("$[?(@.name=='loki')].status").value("NOT_CONFIGURED"))
                .andExpect(jsonPath("$[?(@.name=='jaeger')].status").value("NOT_CONFIGURED"))
                .andExpect(jsonPath("$[?(@.name=='grafana')].status").value("NOT_CONFIGURED"))
                .andExpect(jsonPath("$[?(@.name=='alertmanager')].status").value("NOT_CONFIGURED"));
    }

    @Test
    void theAnswerNamesNoAddressAndNoErrorText() throws Exception {
        mockMvc.perform(get("/api/watchtower/services").with(asUser(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("127.0.0.1"))))
                .andExpect(content().string(not(containsString("http"))))
                .andExpect(content().string(not(containsString("Exception"))));
    }

    @Test
    void aSignedInPersonWhoIsNotTheOwnerGetsA404() throws Exception {
        mockMvc.perform(get("/api/watchtower/services").with(asUser(someoneElse.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void aSignedOutVisitorGetsA404AndNotALoginPrompt() throws Exception {
        mockMvc.perform(get("/api/watchtower/services"))
                .andExpect(status().isNotFound());
    }
}
