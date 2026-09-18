package com.routinemachine.core.api;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.RoutineItem;
import com.routinemachine.core.repository.RoutineItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RoutineItemApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoutineItemRepository routineItemRepository;

    @Test
    void createsARoutineItemAndReturnsIt() throws Exception {
        mockMvc.perform(post("/api/routine-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "journaling", "description": "Daily journal entry"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name", is("journaling")))
                .andExpect(jsonPath("$.description", is("Daily journal entry")))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void rejectsARoutineItemWithNoName() throws Exception {
        mockMvc.perform(post("/api/routine-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": "missing a name"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listsAllRoutineItems() throws Exception {
        routineItemRepository.save(new RoutineItem("exercise", null));

        mockMvc.perform(get("/api/routine-items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("exercise")))
                .andExpect(jsonPath("$[0].createdAt").exists());
    }
}
