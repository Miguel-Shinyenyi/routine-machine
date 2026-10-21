package com.routinemachine.core.api;

import com.routinemachine.core.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static com.routinemachine.core.TestTokens.asUser;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CurrentReadingApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private com.routinemachine.core.repository.UserRepository meRepository;

    private com.routinemachine.core.domain.User me;

    @org.junit.jupiter.api.BeforeEach
    void signInAsANewUser() {
        me = meRepository.save(new com.routinemachine.core.domain.User(
                "CurrentReadingApiIntegrationTest-" + System.nanoTime() + "@example.com", "irrelevant-hash", null, null));
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returns404WhenNothingHasBeenLoggedYet() throws Exception {
        mockMvc.perform(get("/api/current-reading").with(asUser(me.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void postingANewTitleMakesItTheLatestReading() throws Exception {
        mockMvc.perform(post("/api/current-reading").with(asUser(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Sapiens"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title", is("Sapiens")))
                .andExpect(jsonPath("$.source", is("manual")));

        mockMvc.perform(post("/api/current-reading").with(asUser(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Homo Deus"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/current-reading").with(asUser(me.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Homo Deus")));
    }

    @Test
    void rejectsABlankTitle() throws Exception {
        mockMvc.perform(post("/api/current-reading").with(asUser(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": ""}
                                """))
                .andExpect(status().isBadRequest());
    }
}
