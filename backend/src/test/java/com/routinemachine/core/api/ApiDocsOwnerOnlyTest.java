package com.routinemachine.core.api;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static com.routinemachine.core.TestTokens.asUser;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Staged Prompt 4: the backend's API documentation (springdoc) is visible to the owner only. Everyone else, signed in
// or not, gets a plain 404 on every documentation address, so it is not known to exist.
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "owner.email=apidocs-owner@example.com")
class ApiDocsOwnerOnlyTest extends AbstractIntegrationTest {

    // Every address springdoc serves: the spec, its UI configuration, the UI's redirect, its page and one of its assets.
    private static final List<String> DOCS_PATHS = List.of(
            "/v3/api-docs",
            "/v3/api-docs/swagger-config",
            "/swagger-ui.html",
            "/swagger-ui/index.html",
            "/swagger-ui/swagger-ui.css");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;

    private User owner;
    private User someoneElse;

    @BeforeEach
    void accounts() {
        owner = account("apidocs-owner@example.com");
        someoneElse = account("apidocs-other@example.com");
    }

    private User account(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> userRepository.save(new User(email, "irrelevant-hash", null, null)));
    }

    @Test
    void theOwnerGetsTheOpenApiDocumentWithTheOperationsPageRoutesInIt() throws Exception {
        mockMvc.perform(get("/v3/api-docs").with(asUser(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value(startsWith("3.")))
                .andExpect(jsonPath("$.paths['/api/watchtower/services']").exists());
    }

    @Test
    void theOwnerGetsTheSwaggerUiPage() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html").with(asUser(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Swagger UI")));
    }

    @Test
    void aPersonWhoIsNotTheOwnerGetsA404OnEveryDocumentationAddress() throws Exception {
        for (String path : DOCS_PATHS) {
            mockMvc.perform(get(path).with(asUser(someoneElse.getId())))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void aSignedOutVisitorGetsA404OnEveryDocumentationAddress() throws Exception {
        for (String path : DOCS_PATHS) {
            mockMvc.perform(get(path))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void a404CarriesNoBodyThatNamesTheDocumentation() throws Exception {
        mockMvc.perform(get("/v3/api-docs").with(asUser(someoneElse.getId())))
                .andExpect(status().isNotFound())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("openapi"))));
    }

    @Test
    void aMalformedTokenIsRefusedWith401LikeTheOtherOwnerOnlyAddresses() throws Exception {
        mockMvc.perform(get("/v3/api-docs").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }
}
