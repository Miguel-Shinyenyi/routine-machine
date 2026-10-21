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

import static com.routinemachine.core.TestTokens.asUser;
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
    private com.routinemachine.core.repository.UserRepository userRepository;

    private com.routinemachine.core.domain.User me;

    @org.junit.jupiter.api.BeforeEach
    void signInAsANewUser() {
        me = userRepository.save(new com.routinemachine.core.domain.User(
                "LearningTopicApiIntegrationTest-" + System.nanoTime() + "@example.com", "irrelevant-hash", null, null));
    }

    // A goal this test's user owns. The seeded goals belong to nobody and the API never shows those.
    private com.routinemachine.core.domain.Goal myGoal(String name) {
        return goalRepository.save(new com.routinemachine.core.domain.Goal(name, null, me));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GoalRepository goalRepository;

    @Autowired
    private LearningTopicRepository learningTopicRepository;

    @Test
    void listsTopicsWithTheirGoalNamePopulated() throws Exception {
        Goal goal = myGoal("ai-engineer-evidence");
        learningTopicRepository.save(new LearningTopic("write article on evals", goal));

        mockMvc.perform(get("/api/learning-topics").with(asUser(me.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("write article on evals")))
                .andExpect(jsonPath("$[*].goalName", hasItem("ai-engineer-evidence")));
    }

    @Test
    void createsATopicLinkedToAnExistingGoal() throws Exception {
        Long goalId = myGoal("target-roles").getId();

        mockMvc.perform(post("/api/learning-topics").with(asUser(me.getId()))
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
        mockMvc.perform(post("/api/learning-topics").with(asUser(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "orphan topic", "goalId": 999999}
                                """))
                .andExpect(status().isBadRequest());
    }
}
