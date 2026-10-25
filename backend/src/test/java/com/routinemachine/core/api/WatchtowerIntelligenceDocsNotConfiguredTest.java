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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// With no address configured there is nothing to ask: 503, and no request is made.
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"owner.email=intdocs-none-owner@example.com", "watchtower.intelligence-docs-url="})
class WatchtowerIntelligenceDocsNotConfiguredTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;

    @Test
    void withNoAddressConfiguredTheOwnerGetsA503() throws Exception {
        User owner = userRepository.findByEmailIgnoreCase("intdocs-none-owner@example.com")
                .orElseGet(() -> userRepository.save(new User("intdocs-none-owner@example.com", "irrelevant-hash", null, null)));

        mockMvc.perform(get("/api/watchtower/intelligence-api-docs").with(asUser(owner.getId())))
                .andExpect(status().isServiceUnavailable());
    }
}
