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

import static com.routinemachine.core.TestTokens.asUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// What the intelligence service sends back is checked, not passed on blindly: a reply that is not an OpenAPI document
// is a 502, and a reply that is not a 200 is a 503, so the owner never sees something that only looks like the spec.
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "owner.email=intdocs-bad-owner@example.com")
class WatchtowerIntelligenceDocsBadDocumentTest extends AbstractIntegrationTest {

    private static volatile int statusCode = 200;
    private static volatile String body = "";
    private static final HttpServer DOCS = startDocs();

    private static HttpServer startDocs() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/openapi.json", exchange -> {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(statusCode, bytes.length);
                exchange.getResponseBody().write(bytes);
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

    private User owner() {
        return userRepository.findByEmailIgnoreCase("intdocs-bad-owner@example.com")
                .orElseGet(() -> userRepository.save(new User("intdocs-bad-owner@example.com", "irrelevant-hash", null, null)));
    }

    @Test
    void textThatIsNotJsonIsA502() throws Exception {
        statusCode = 200;
        body = "<html>not json</html>";
        mockMvc.perform(get("/api/watchtower/intelligence-api-docs").with(asUser(owner().getId())))
                .andExpect(status().isBadGateway());
    }

    @Test
    void jsonThatIsNotAnOpenApiDocumentIsA502() throws Exception {
        statusCode = 200;
        body = "{\"hello\":\"world\"}";
        mockMvc.perform(get("/api/watchtower/intelligence-api-docs").with(asUser(owner().getId())))
                .andExpect(status().isBadGateway());
    }

    @Test
    void aReplyThatIsNotA200IsA503() throws Exception {
        statusCode = 500;
        body = "{\"openapi\":\"3.1.0\"}";
        mockMvc.perform(get("/api/watchtower/intelligence-api-docs").with(asUser(owner().getId())))
                .andExpect(status().isServiceUnavailable());
    }
}
