package com.routinemachine.core.api;

import com.routinemachine.core.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CurrentReadingApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returns404WhenNothingHasBeenLoggedYet() throws Exception {
        mockMvc.perform(get("/api/current-reading"))
                .andExpect(status().isNotFound());
    }

    @Test
    void postingANewTitleMakesItTheLatestReading() throws Exception {
        mockMvc.perform(post("/api/current-reading")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Sapiens"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title", is("Sapiens")))
                .andExpect(jsonPath("$.source", is("manual")));

        mockMvc.perform(post("/api/current-reading")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Homo Deus"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/current-reading"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Homo Deus")));
    }

    @Test
    void rejectsABlankTitle() throws Exception {
        mockMvc.perform(post("/api/current-reading")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": ""}
                                """))
                .andExpect(status().isBadRequest());
    }
}
