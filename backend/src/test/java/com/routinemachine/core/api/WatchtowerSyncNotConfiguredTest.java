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

// Without Prometheus the owner still sees each source's switch and newest data; the run state reads "unknown".
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"owner.email=watchtower-nosync-owner@example.com",
        "watchtower.prometheus-base-url=http://127.0.0.1:1",
        "hub-sync.enabled=true", "rescuetime-sync.enabled=false"})
class WatchtowerSyncNotConfiguredTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;

    @Test
    void whenPrometheusDoesNotAnswerTheOwnerStillGetsTheDatabaseFieldsAndAnUnknownResult() throws Exception {
        User owner = userRepository.findByEmailIgnoreCase("watchtower-nosync-owner@example.com")
                .orElseGet(() -> userRepository.save(new User("watchtower-nosync-owner@example.com", "irrelevant-hash", null, null)));

        mockMvc.perform(get("/api/watchtower/sync").with(asUser(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.sources").value(hasSize(6)))
                .andExpect(jsonPath("$.sources[?(@.source=='HUB')].enabled").value(true))
                .andExpect(jsonPath("$.sources[?(@.source=='HUB')].result").value("unknown"))
                .andExpect(jsonPath("$.sources[?(@.source=='RESCUETIME')].result").value("off"));
    }
}
