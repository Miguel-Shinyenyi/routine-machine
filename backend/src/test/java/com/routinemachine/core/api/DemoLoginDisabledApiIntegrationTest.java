package com.routinemachine.core.api;

import com.routinemachine.core.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The demo profile is off unless DEMO_PROFILE_ENABLED is set (docs/backend.md, "Demo profile").
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "auth.internal-token=the-real-internal-token")
class DemoLoginDisabledApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void theDemoLoginDoesNotExistByDefault_evenWithAValidInternalToken() throws Exception {
        mockMvc.perform(post("/api/auth/demo-login")
                        .header("Authorization", "Bearer the-real-internal-token"))
                .andExpect(status().isNotFound());
    }
}
