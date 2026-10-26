package com.routinemachine.core.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static com.routinemachine.core.TestTokens.asUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

// Staged Prompt 4: the API documentation must cover the whole API. Every controller method appears in the OpenAPI
// document, and every operation has a summary. A new endpoint without a summary, or one hidden from the document,
// fails here and names itself, so the documentation cannot fall behind the code without a red test.
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "owner.email=apidocs-coverage-owner@example.com")
class ApiDocsCoverageTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    private JsonNode spec() throws Exception {
        User owner = userRepository.findByEmailIgnoreCase("apidocs-coverage-owner@example.com")
                .orElseGet(() -> userRepository.save(new User("apidocs-coverage-owner@example.com", "irrelevant-hash", null, null)));
        String body = mockMvc.perform(get("/v3/api-docs").with(asUser(owner.getId())))
                .andReturn().getResponse().getContentAsString();
        return new ObjectMapper().readTree(body);
    }

    @Test
    void everyControllerMethodOfTheApplicationAppearsInTheDocument() throws Exception {
        JsonNode paths = spec().path("paths");
        Set<String> missing = new TreeSet<>();
        int checked = 0;
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            if (!entry.getValue().getBeanType().getPackageName().startsWith("com.routinemachine")) {
                continue;
            }
            RequestMappingInfo info = entry.getKey();
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
                for (RequestMethod method : methods) {
                    checked++;
                    if (paths.path(pattern).path(method.name().toLowerCase(Locale.ROOT)).isMissingNode()) {
                        missing.add(method + " " + pattern + "  (" + entry.getValue().getShortLogMessage() + ")");
                    }
                }
            }
        }
        assertThat(checked).as("controller methods looked at").isGreaterThan(50);
        assertThat(missing).as("controller methods missing from /v3/api-docs").isEmpty();
    }

    @Test
    void everyOperationInTheDocumentHasASummary() throws Exception {
        JsonNode paths = spec().path("paths");
        List<String> withoutSummary = new ArrayList<>();
        int operations = 0;
        for (Map.Entry<String, JsonNode> path : paths.properties()) {
            for (Map.Entry<String, JsonNode> operation : path.getValue().properties()) {
                operations++;
                if (operation.getValue().path("summary").asText("").isBlank()) {
                    withoutSummary.add(operation.getKey().toUpperCase(Locale.ROOT) + " " + path.getKey());
                }
            }
        }
        assertThat(operations).as("operations in the document").isGreaterThan(50);
        assertThat(withoutSummary).as("operations with no summary").isEmpty();
    }
}
