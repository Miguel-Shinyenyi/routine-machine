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

// With no OWNER_EMAIL configured there is no owner, so nobody may open the page: failing closed.
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "owner.email=")
class WatchtowerWithNoOwnerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;

    @Test
    void withNoOwnerConfiguredEveryoneGetsA404() throws Exception {
        User anyone = userRepository.findByEmailIgnoreCase("watchtower-anyone@example.com")
                .orElseGet(() -> userRepository.save(new User("watchtower-anyone@example.com", "irrelevant-hash", null, null)));

        mockMvc.perform(get("/api/watchtower/services").with(asUser(anyone.getId())))
                .andExpect(status().isNotFound());
    }
}
