package com.routinemachine.core.api;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.RoutineItem;
import com.routinemachine.core.repository.GoalRepository;
import com.routinemachine.core.repository.LearningTopicRepository;
import com.routinemachine.core.repository.RoutineItemRepository;
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
class DailyLogApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private com.routinemachine.core.repository.UserRepository userRepository;

    private com.routinemachine.core.domain.User me;

    @org.junit.jupiter.api.BeforeEach
    void signInAsANewUser() {
        me = userRepository.save(new com.routinemachine.core.domain.User(
                "DailyLogApiIntegrationTest-" + System.nanoTime() + "@example.com", "irrelevant-hash", null, null));
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
    private LearningTopicRepository learningTopicRepository;

    @Autowired
    private GoalRepository goalRepository;

    @Test
    void logsARoutineItemCompletionAsManualByDefault() throws Exception {
        RoutineItem item = myItem("journaling");

        mockMvc.perform(post("/api/daily-logs").with(asUser(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"logDate": "2026-09-18", "routineItemId": %d}
                                """.formatted(item.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.routineItemId", is(item.getId().intValue())))
                .andExpect(jsonPath("$.learningTopicId").doesNotExist())
                .andExpect(jsonPath("$.source", is("manual")));
    }

    @Test
    void logsALearningTopicCompletionWithAnExplicitHubSyncSource() throws Exception {
        var goal = myGoal("ai-engineer-evidence");
        var topic = learningTopicRepository.save(
                new com.routinemachine.core.domain.LearningTopic("write article on evals", goal));

        mockMvc.perform(post("/api/daily-logs").with(asUser(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"logDate": "2026-09-18", "learningTopicId": %d, "source": "hub-sync"}
                                """.formatted(topic.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.learningTopicId", is(topic.getId().intValue())))
                .andExpect(jsonPath("$.source", is("hub-sync")));
    }

    @Test
    void logsARoutineItemCompletionWithMeasuredMinutes() throws Exception {
        RoutineItem item = myItem("deep work");

        mockMvc.perform(post("/api/daily-logs").with(asUser(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"logDate": "2026-09-18", "routineItemId": %d, "measuredMinutes": 45}
                                """.formatted(item.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.measuredMinutes", is(45)));
    }

    @Test
    void rejectsALogWithBothTargetsSet() throws Exception {
        RoutineItem item = myItem("exercise");
        var goal = myGoal("target-roles");
        var topic = learningTopicRepository.save(
                new com.routinemachine.core.domain.LearningTopic("PostHog analytics", goal));

        mockMvc.perform(post("/api/daily-logs").with(asUser(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"logDate": "2026-09-18", "routineItemId": %d, "learningTopicId": %d}
                                """.formatted(item.getId(), topic.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsALogWithNeitherTargetSet() throws Exception {
        mockMvc.perform(post("/api/daily-logs").with(asUser(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"logDate": "2026-09-18"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAnUnknownSource() throws Exception {
        RoutineItem item = myItem("reading");

        mockMvc.perform(post("/api/daily-logs").with(asUser(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"logDate": "2026-09-18", "routineItemId": %d, "source": "automatic"}
                                """.formatted(item.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listsLogsFilteredByDate() throws Exception {
        RoutineItem item = myItem("stretching");
        mockMvc.perform(post("/api/daily-logs").with(asUser(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"logDate": "2026-09-17", "routineItemId": %d}
                                """.formatted(item.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/daily-logs").with(asUser(me.getId())).param("date", "2026-09-17"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].routineItemId", org.hamcrest.Matchers.hasItem(item.getId().intValue())));
    }
}
