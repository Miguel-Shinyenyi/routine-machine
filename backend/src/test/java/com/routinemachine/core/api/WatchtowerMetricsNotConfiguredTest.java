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

// Without the observability stack the owner's page still loads: it says metrics are not available.
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"owner.email=watchtower-nometrics-owner@example.com",
        "watchtower.prometheus-base-url=http://127.0.0.1:1"})
class WatchtowerMetricsNotConfiguredTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;

    @Test
    void whenPrometheusDoesNotAnswerTheOwnerGetsAvailableFalseAndNoChartsNotAnError() throws Exception {
        User owner = userRepository.findByEmailIgnoreCase("watchtower-nometrics-owner@example.com")
                .orElseGet(() -> userRepository.save(new User("watchtower-nometrics-owner@example.com", "irrelevant-hash", null, null)));

        mockMvc.perform(get("/api/watchtower/metrics").with(asUser(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.charts").value(hasSize(0)));
    }
}
