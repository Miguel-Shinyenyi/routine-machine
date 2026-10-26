package com.routinemachine.core.api;

import com.routinemachine.core.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static com.routinemachine.core.TestTokens.asUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Own class so it gets its own fresh Postgres container, independent of BookApiIntegrationTest
// writing books in the same test run (see that class's own comment on why).
@SpringBootTest
@AutoConfigureMockMvc
class BookApiNoBooksYetTest extends AbstractIntegrationTest {

    @Autowired
    private com.routinemachine.core.repository.UserRepository meRepository;

    private com.routinemachine.core.domain.User me;

    @org.junit.jupiter.api.BeforeEach
    void signInAsANewUser() {
        me = meRepository.save(new com.routinemachine.core.domain.User(
                "BookApiNoBooksYetTest-" + System.nanoTime() + "@example.com", "irrelevant-hash", null, null));
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returns404WhenNoBookHasBeenLoggedYet() throws Exception {
        mockMvc.perform(get("/api/books/current").with(asUser(me.getId())))
                .andExpect(status().isNotFound());
    }
}
