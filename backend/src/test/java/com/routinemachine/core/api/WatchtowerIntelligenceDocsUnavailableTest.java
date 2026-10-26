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

// The owner asks, but the intelligence service's spec cannot be had: the answer is a plain 503, not a hang and not a
// stack trace, so the documentation page shows one entry as unavailable and the other still works.
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"owner.email=intdocs-down-owner@example.com",
        "watchtower.intelligence-docs-url=http://127.0.0.1:1/openapi.json"})
class WatchtowerIntelligenceDocsUnavailableTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;

    @Test
    void whenTheServiceDoesNotAnswerTheOwnerGetsA503() throws Exception {
        User owner = userRepository.findByEmailIgnoreCase("intdocs-down-owner@example.com")
                .orElseGet(() -> userRepository.save(new User("intdocs-down-owner@example.com", "irrelevant-hash", null, null)));

        mockMvc.perform(get("/api/watchtower/intelligence-api-docs").with(asUser(owner.getId())))
                .andExpect(status().isServiceUnavailable());
    }
}
