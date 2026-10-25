package com.routinemachine.core.api;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static com.routinemachine.core.TestTokens.asUser;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Staged Prompt 4, part 4: how the Swagger UI is set up for the owner page. It lists both APIs, at the addresses the
// frontend's documentation route serves them from (the browser cannot send a bearer token itself, so it never talks to
// the backend directly), and it is read-only: no "Try it out", so nothing the owner opens here can send a request.
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "owner.email=apidocs-config-owner@example.com")
class ApiDocsSwaggerConfigTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;

    private User owner() {
        return userRepository.findByEmailIgnoreCase("apidocs-config-owner@example.com")
                .orElseGet(() -> userRepository.save(new User("apidocs-config-owner@example.com", "irrelevant-hash", null, null)));
    }

    @Test
    void theUiListsBothApisAtTheAddressesTheFrontendServes() throws Exception {
        mockMvc.perform(get("/v3/api-docs/swagger-config").with(asUser(owner().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.urls").value(hasSize(2)))
                .andExpect(jsonPath("$.urls[0].name").value("Backend"))
                .andExpect(jsonPath("$.urls[0].url").value("/watchtower/docs/spec/backend"))
                .andExpect(jsonPath("$.urls[1].name").value("Intelligence service"))
                .andExpect(jsonPath("$.urls[1].url").value("/watchtower/docs/spec/intelligence"));
    }

    // Found in a real browser, not by curl: the UI reads this configuration and then reads configUrl again. If it named the
    // backend's own address, the browser would ask the frontend for it, get a 404 and show "Failed to load remote configuration".
    @Test
    void theConfigurationPointsBackAtTheAddressTheFrontendServesItFrom() throws Exception {
        mockMvc.perform(get("/v3/api-docs/swagger-config").with(asUser(owner().getId())))
                .andExpect(jsonPath("$.configUrl").value("/watchtower/docs/spec/config"));
    }

    // Found in Chrome: springdoc fills "servers" from the address the request came in on, which behind the frontend is the
    // internal name backend:8080, and the UI then showed "http://backend:8080 - Generated server url". A relative server
    // names no host at all.
    @Test
    void theDocumentNamesNoInternalServer() throws Exception {
        mockMvc.perform(get("/v3/api-docs").with(request -> {
                    request.setServerName("backend");
                    request.setServerPort(8080);
                    return request;
                }).with(asUser(owner().getId())))
                .andExpect(jsonPath("$.servers").value(hasSize(1)))
                .andExpect(jsonPath("$.servers[0].url").value("/"));
    }

    @Test
    void theUiIsReadOnlyWithNoTryItOut() throws Exception {
        mockMvc.perform(get("/v3/api-docs/swagger-config").with(asUser(owner().getId())))
                .andExpect(jsonPath("$.supportedSubmitMethods").isArray())
                .andExpect(jsonPath("$.supportedSubmitMethods").value(hasSize(0)));
    }

    @Test
    void theUiDoesNotSendTheSpecAddressesToAnOnlineValidator() throws Exception {
        String config = mockMvc.perform(get("/v3/api-docs/swagger-config").with(asUser(owner().getId())))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(config).doesNotContain("validator.swagger.io");
    }
}
