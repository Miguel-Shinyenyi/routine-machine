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
class BookApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private com.routinemachine.core.repository.UserRepository meRepository;

    private com.routinemachine.core.domain.User me;

    @org.junit.jupiter.api.BeforeEach
    void signInAsANewUser() {
        me = meRepository.save(new com.routinemachine.core.domain.User(
                "BookApiIntegrationTest-" + System.nanoTime() + "@example.com", "irrelevant-hash", null, null));
    }

    @Autowired
    private MockMvc mockMvc;

    // One test, not several, since @SpringBootTest methods in this codebase share one
    // Postgres instance per class with no per-method rollback (see testing.md's decisions
    // log): a second test method asserting on "the" current book or "the" book list would be
    // order-dependent on what this one already wrote.
    @Test
    void postingBooksMakesTheLatestOneCurrentAndListsThemMostRecentFirst() throws Exception {
        mockMvc.perform(post("/api/books").with(asUser(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Sapiens", "author": "Yuval Noah Harari"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title", is("Sapiens")))
                .andExpect(jsonPath("$.source", is("manual")));

        mockMvc.perform(post("/api/books").with(asUser(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Homo Deus", "currentChapter": 2, "totalChapters": 11}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentChapter", is(2)))
                .andExpect(jsonPath("$.totalChapters", is(11)));

        mockMvc.perform(get("/api/books/current").with(asUser(me.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Homo Deus")));

        mockMvc.perform(get("/api/books").with(asUser(me.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title", is("Homo Deus")))
                .andExpect(jsonPath("$[1].title", is("Sapiens")));
    }

    @Test
    void rejectsABlankTitle() throws Exception {
        mockMvc.perform(post("/api/books").with(asUser(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": ""}
                                """))
                .andExpect(status().isBadRequest());
    }
}
