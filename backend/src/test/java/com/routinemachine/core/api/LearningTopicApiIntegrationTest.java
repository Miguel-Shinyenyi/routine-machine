package com.routinemachine.core.api;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.Goal;
import com.routinemachine.core.domain.LearningTopic;
import com.routinemachine.core.repository.GoalRepository;
import com.routinemachine.core.repository.LearningTopicRepository;
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
class LearningTopicApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GoalRepository goalRepository;

    @Autowired
    private LearningTopicRepository learningTopicRepository;

    @Test
    void listsTopicsWithTheirGoalNamePopulated() throws Exception {
        Goal goal = goalRepository.findByName("ai-engineer-evidence").orElseThrow();
        learningTopicRepository.save(new LearningTopic("write article on evals", goal));

        mockMvc.perform(get("/api/learning-topics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("write article on evals")))
                .andExpect(jsonPath("$[*].goalName", hasItem("ai-engineer-evidence")));
    }

    @Test
    void createsATopicLinkedToAnExistingGoal() throws Exception {
        Long goalId = goalRepository.findByName("target-roles").orElseThrow().getId();

        mockMvc.perform(post("/api/learning-topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "PostHog product analytics", "goalId": %d}
                                """.formatted(goalId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("PostHog product analytics")))
                .andExpect(jsonPath("$.goalId", is(goalId.intValue())));
    }

    @Test
    void rejectsATopicForAGoalThatDoesNotExist() throws Exception {
        mockMvc.perform(post("/api/learning-topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "orphan topic", "goalId": 999999}
                                """))
                .andExpect(status().isBadRequest());
    }
}
