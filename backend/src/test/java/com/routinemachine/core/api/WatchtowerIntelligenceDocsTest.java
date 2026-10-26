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
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static com.routinemachine.core.TestTokens.asUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Staged Prompt 4: the intelligence service's API document, handed to the owner by the backend. The service serves it only
// on an internal port (docs/intelligence-service.md); the backend checks the owner, reads it, and returns it, so the owner
// page can show both APIs. Anyone else gets a plain 404 and the service is never asked.
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "owner.email=intdocs-owner@example.com")
class WatchtowerIntelligenceDocsTest extends AbstractIntegrationTest {

    // A stand-in for the intelligence service's docs port: counts the requests, answers a small spec.
    private static final AtomicInteger ASKED = new AtomicInteger();
    private static final HttpServer DOCS = startDocs();

    private static HttpServer startDocs() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/openapi.json", exchange -> {
                ASKED.incrementAndGet();
                byte[] body = "{\"openapi\":\"3.1.0\",\"info\":{\"title\":\"Stand-in\"},\"paths\":{\"/suggestion\":{}}}"
                        .getBytes(StandardCharsets.UTF_8);
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
    static void docs(DynamicPropertyRegistry registry) {
        registry.add("watchtower.intelligence-docs-url", () -> "http://127.0.0.1:" + DOCS.getAddress().getPort() + "/openapi.json");
    }

    @AfterAll
    static void stop() {
        DOCS.stop(0);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;

    private User owner;
    private User someoneElse;

    @BeforeEach
    void accounts() {
        ASKED.set(0);
        owner = account("intdocs-owner@example.com");
        someoneElse = account("intdocs-other@example.com");
    }

    private User account(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> userRepository.save(new User(email, "irrelevant-hash", null, null)));
    }

    @Test
    void theOwnerGetsTheIntelligenceServicesSpec() throws Exception {
        mockMvc.perform(get("/api/watchtower/intelligence-api-docs").with(asUser(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value("3.1.0"))
                .andExpect(jsonPath("$.paths['/suggestion']").exists());
        assertThat(ASKED.get()).isEqualTo(1);
    }

    @Test
    void aPersonWhoIsNotTheOwnerGetsA404AndTheServiceIsNeverAsked() throws Exception {
        mockMvc.perform(get("/api/watchtower/intelligence-api-docs").with(asUser(someoneElse.getId())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/watchtower/intelligence-api-docs"))
                .andExpect(status().isNotFound());
        assertThat(ASKED.get()).isZero();
    }
}
