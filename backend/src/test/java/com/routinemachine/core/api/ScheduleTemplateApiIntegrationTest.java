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

import static com.routinemachine.core.TestTokens.asUser;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ScheduleTemplateApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private com.routinemachine.core.repository.UserRepository userRepository;

    private com.routinemachine.core.domain.User me;

    @org.junit.jupiter.api.BeforeEach
    void signInAsANewUser() {
        me = userRepository.save(new com.routinemachine.core.domain.User(
                "ScheduleTemplateApiIntegrationTest-" + System.nanoTime() + "@example.com", "irrelevant-hash", null, null));
    }

    // A goal this test's user owns. The seeded goals belong to nobody and the API never shows those.

    private com.routinemachine.core.domain.RoutineItem myItem(String name) {
        com.routinemachine.core.domain.RoutineItem item = new com.routinemachine.core.domain.RoutineItem(name, null);
        item.assignUser(me);
        return routineItemRepository.save(item);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoutineItemRepository routineItemRepository;

    @Test
    void createsARoutineItemTemplate() throws Exception {
        RoutineItem item = myItem("weight lifting");

        mockMvc.perform(post("/api/schedule-templates").with(asUser(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dayOfWeek": "MONDAY", "targetType": "ROUTINE_ITEM", "routineItemId": %d,
                                 "targetDurationMinutes": 60, "sortOrder": 1, "label": "Weight lifting"}
                                """.formatted(item.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dayOfWeek", is("MONDAY")))
                .andExpect(jsonPath("$.targetType", is("ROUTINE_ITEM")))
                .andExpect(jsonPath("$.routineItemId", is(item.getId().intValue())))
                .andExpect(jsonPath("$.label", is("Weight lifting")));
    }

    @Test
    void createsALearningSlotTemplateWithNoRoutineItem() throws Exception {
        mockMvc.perform(post("/api/schedule-templates").with(asUser(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dayOfWeek": "TUESDAY", "targetType": "LEARNING_SLOT",
                                 "targetDurationMinutes": 45, "sortOrder": 1, "label": "Goal-suggested topic"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetType", is("LEARNING_SLOT")))
                .andExpect(jsonPath("$.routineItemId").doesNotExist());
    }

    @Test
    void rejectsARoutineItemTemplateWithNoRoutineItemId() throws Exception {
        mockMvc.perform(post("/api/schedule-templates").with(asUser(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dayOfWeek": "MONDAY", "targetType": "ROUTINE_ITEM",
                                 "targetDurationMinutes": 60, "sortOrder": 1, "label": "x"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listsTemplatesFilteredByDayOfWeek() throws Exception {
        mockMvc.perform(post("/api/schedule-templates").with(asUser(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dayOfWeek": "SATURDAY", "targetType": "LEARNING_SLOT",
                                 "targetDurationMinutes": 30, "sortOrder": 1, "label": "Catch-up"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/schedule-templates").with(asUser(me.getId())).param("dayOfWeek", "SATURDAY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].label", hasItem("Catch-up")));
    }
}
