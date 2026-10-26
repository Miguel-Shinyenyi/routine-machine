package com.routinemachine.core.api;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.RoutineItem;
import com.routinemachine.core.repository.GoalRepository;
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
class RoutineItemApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private com.routinemachine.core.repository.UserRepository userRepository;

    private com.routinemachine.core.domain.User me;

    @org.junit.jupiter.api.BeforeEach
    void signInAsANewUser() {
        me = userRepository.save(new com.routinemachine.core.domain.User(
                "RoutineItemApiIntegrationTest-" + System.nanoTime() + "@example.com", "irrelevant-hash", null, null));
    }

    // A goal this test's user owns. The seeded goals belong to nobody and the API never shows those.
    private com.routinemachine.core.domain.Goal myGoal(String name) {
        return goalRepository.save(new com.routinemachine.core.domain.Goal(name, null, me));
    }

    private com.routinemachine.core.domain.RoutineItem myItem(String name) {
        com.routinemachine.core.domain.RoutineItem item = new com.routinemachine.core.domain.RoutineItem(name, null);
        item.assignUser(me);
        return routineItemRepository.save(item);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoutineItemRepository routineItemRepository;

    @Autowired
    private GoalRepository goalRepository;

    @Test
    void createsARoutineItemAndReturnsIt() throws Exception {
        mockMvc.perform(post("/api/routine-items").with(asUser(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "journaling", "description": "Daily journal entry"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name", is("journaling")))
                .andExpect(jsonPath("$.description", is("Daily journal entry")))
                .andExpect(jsonPath("$.source", is("MANUAL")))
                .andExpect(jsonPath("$.isPrivate", is(true)))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void createsAFullyCategorisedRoutineItemLinkedToAGoal() throws Exception {
        var goal = myGoal("ai-engineer-evidence");

        mockMvc.perform(post("/api/routine-items").with(asUser(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "deep work", "category": "WORK", "countRule": "30 active minutes",
                                 "source": "WAKATIME", "hillsideField": "DEEP_WORK", "isPrivate": false,
                                 "goalId": %d}
                                """.formatted(goal.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category", is("WORK")))
                .andExpect(jsonPath("$.countRule", is("30 active minutes")))
                .andExpect(jsonPath("$.source", is("WAKATIME")))
                .andExpect(jsonPath("$.hillsideField", is("DEEP_WORK")))
                .andExpect(jsonPath("$.isPrivate", is(false)))
                .andExpect(jsonPath("$.goalId", is(goal.getId().intValue())))
                .andExpect(jsonPath("$.goalName", is("ai-engineer-evidence")));
    }

    @Test
    void rejectsARoutineItemWithAnUnknownGoalId() throws Exception {
        mockMvc.perform(post("/api/routine-items").with(asUser(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "deep work", "goalId": 999999}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsARoutineItemWithNoName() throws Exception {
        mockMvc.perform(post("/api/routine-items").with(asUser(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": "missing a name"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listsAllRoutineItems() throws Exception {
        myItem("exercise");

        mockMvc.perform(get("/api/routine-items").with(asUser(me.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("exercise")))
                .andExpect(jsonPath("$[0].createdAt").exists());
    }
}
