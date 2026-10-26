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

// With no OWNER_EMAIL configured there is no owner, so nobody may open the documentation: failing closed.
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "owner.email=")
class ApiDocsWithNoOwnerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;

    @Test
    void withNoOwnerConfiguredEveryoneGetsA404() throws Exception {
        User anyone = userRepository.findByEmailIgnoreCase("apidocs-anyone@example.com")
                .orElseGet(() -> userRepository.save(new User("apidocs-anyone@example.com", "irrelevant-hash", null, null)));

        mockMvc.perform(get("/v3/api-docs").with(asUser(anyone.getId())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/swagger-ui/index.html").with(asUser(anyone.getId())))
                .andExpect(status().isNotFound());
    }
}
