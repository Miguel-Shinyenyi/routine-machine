package com.routinemachine.core.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.UserRepository;
import com.routinemachine.core.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.AntPathMatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.routinemachine.core.TestTokens.asUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

// Staged Prompt 4: the error codes in the API documentation. 401 is documented exactly where the security chains
// produce it, from the same patterns SecurityConfig uses; the owner-only routes document their 404; every operation
// carries the shared error response, which lists what GlobalExceptionHandler can answer (400, 403, 404).
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "owner.email=apidocs-errors-owner@example.com")
class ApiDocsErrorsTest extends AbstractIntegrationTest {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;

    private JsonNode spec() throws Exception {
        User owner = userRepository.findByEmailIgnoreCase("apidocs-errors-owner@example.com")
                .orElseGet(() -> userRepository.save(new User("apidocs-errors-owner@example.com", "irrelevant-hash", null, null)));
        String body = mockMvc.perform(get("/v3/api-docs").with(asUser(owner.getId())))
                .andReturn().getResponse().getContentAsString();
        return new ObjectMapper().readTree(body);
    }

    // Each operation of the document as "METHOD /path" with its responses.
    private Map<String, JsonNode> operations() throws Exception {
        Map<String, JsonNode> operations = new java.util.TreeMap<>();
        for (Map.Entry<String, JsonNode> path : spec().path("paths").properties()) {
            for (Map.Entry<String, JsonNode> operation : path.getValue().properties()) {
                operations.put(operation.getKey().toUpperCase(Locale.ROOT) + " " + path.getKey(), operation.getValue().path("responses"));
            }
        }
        return operations;
    }

    private static String pathOf(String operation) {
        return operation.substring(operation.indexOf(' ') + 1);
    }

    private static boolean matchesAny(String path, String[] patterns) {
        for (String pattern : patterns) {
            if (MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void everyOperationOnATokenRequiredRouteDocuments401() throws Exception {
        List<String> missing = new ArrayList<>();
        int checked = 0;
        for (Map.Entry<String, JsonNode> operation : operations().entrySet()) {
            if (matchesAny(pathOf(operation.getKey()), SecurityConfig.TOKEN_REQUIRED_PATTERNS)) {
                checked++;
                if (operation.getValue().path("401").isMissingNode()) {
                    missing.add(operation.getKey());
                }
            }
        }
        assertThat(checked).as("token-required operations looked at").isGreaterThan(30);
        assertThat(missing).as("token-required operations with no 401").isEmpty();
    }

    @Test
    void theOpenRoutesDocumentTheirOwn401OnlyWhereTheyAnswerOne() throws Exception {
        Map<String, JsonNode> operations = operations();
        // Each of these refuses with 401 in its own way: a wrong password, a wrong internal token, a wrong health token.
        for (String operation : List.of("POST /api/auth/login", "POST /api/auth/github-login", "POST /api/measurements")) {
            assertThat(operations.get(operation).path("401").isMissingNode()).as(operation + " has no 401").isFalse();
        }
        // Registering never answers 401: it is open, and a duplicate or invalid email is a 400.
        assertThat(operations.get("POST /api/auth/register").path("401").isMissingNode()).as("register has a 401").isTrue();
    }

    @Test
    void theOwnerOnlyAndOptionalTokenRoutesDocumentTheirOwn401And404() throws Exception {
        Map<String, JsonNode> operations = operations();
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, JsonNode> operation : operations.entrySet()) {
            String path = pathOf(operation.getKey());
            if (path.startsWith("/api/watchtower/") || path.startsWith("/api/hillside")) {
                if (operation.getValue().path("401").isMissingNode()) {
                    missing.add(operation.getKey() + " has no 401");
                }
            }
            if (path.startsWith("/api/watchtower/") && operation.getValue().path("404").isMissingNode()) {
                missing.add(operation.getKey() + " has no 404");
            }
        }
        assertThat(missing).as("optional-token and owner-only routes").isEmpty();
    }

    @Test
    void everyOperationCarriesTheSharedErrorResponseWithAShape() throws Exception {
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, JsonNode> operation : operations().entrySet()) {
            JsonNode shared = operation.getValue().path("default");
            if (shared.isMissingNode()
                    || shared.path("description").asText("").isBlank()
                    || shared.path("content").path("application/json").path("schema").isMissingNode()) {
                missing.add(operation.getKey());
            }
        }
        assertThat(missing).as("operations with no shared error response").isEmpty();
    }
}
