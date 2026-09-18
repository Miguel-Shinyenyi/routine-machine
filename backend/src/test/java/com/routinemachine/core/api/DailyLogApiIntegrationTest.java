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

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DailyLogApiIntegrationTest extends AbstractIntegrationTest {

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
        RoutineItem item = routineItemRepository.save(new RoutineItem("journaling", null));

        mockMvc.perform(post("/api/daily-logs")
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
        var goal = goalRepository.findByName("ai-engineer-evidence").orElseThrow();
        var topic = learningTopicRepository.save(
                new com.routinemachine.core.domain.LearningTopic("write article on evals", goal));

        mockMvc.perform(post("/api/daily-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"logDate": "2026-09-18", "learningTopicId": %d, "source": "hub-sync"}
                                """.formatted(topic.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.learningTopicId", is(topic.getId().intValue())))
                .andExpect(jsonPath("$.source", is("hub-sync")));
    }

    @Test
    void rejectsALogWithBothTargetsSet() throws Exception {
        RoutineItem item = routineItemRepository.save(new RoutineItem("exercise", null));
        var goal = goalRepository.findByName("target-roles").orElseThrow();
        var topic = learningTopicRepository.save(
                new com.routinemachine.core.domain.LearningTopic("PostHog analytics", goal));

        mockMvc.perform(post("/api/daily-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"logDate": "2026-09-18", "routineItemId": %d, "learningTopicId": %d}
                                """.formatted(item.getId(), topic.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsALogWithNeitherTargetSet() throws Exception {
        mockMvc.perform(post("/api/daily-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"logDate": "2026-09-18"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAnUnknownSource() throws Exception {
        RoutineItem item = routineItemRepository.save(new RoutineItem("reading", null));

        mockMvc.perform(post("/api/daily-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"logDate": "2026-09-18", "routineItemId": %d, "source": "automatic"}
                                """.formatted(item.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listsLogsFilteredByDate() throws Exception {
        RoutineItem item = routineItemRepository.save(new RoutineItem("stretching", null));
        mockMvc.perform(post("/api/daily-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"logDate": "2026-09-17", "routineItemId": %d}
                                """.formatted(item.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/daily-logs").param("date", "2026-09-17"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].routineItemId", org.hamcrest.Matchers.hasItem(item.getId().intValue())));
    }
}
