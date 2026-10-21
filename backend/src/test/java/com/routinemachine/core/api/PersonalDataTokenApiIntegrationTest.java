package com.routinemachine.core.api;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static com.routinemachine.core.TestTokens.asUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Access control, part 2: goals, routine items, learning topics, daily logs, tasks and schedule
// templates act for the person in the signed token. No token is 401. A user id in the request
// (query or body) is not read at all.
@SpringBootTest
@AutoConfigureMockMvc
class PersonalDataTokenApiIntegrationTest extends AbstractIntegrationTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;

    private long saveUser(String email) {
        return userRepository.save(new User(email, "irrelevant-hash", null, null)).getId();
    }

    private long idOf(MvcResult result) throws Exception {
        return Long.parseLong(result.getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    private long create(String path, long asUser, String json) throws Exception {
        return idOf(mockMvc.perform(post(path).with(asUser(asUser)).contentType(JSON).content(json))
                .andExpect(status().isCreated()).andReturn());
    }

    private String list(String path, long asUser) throws Exception {
        return mockMvc.perform(get(path).with(asUser(asUser))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void everyRouteRefusesARequestWithNoToken() throws Exception {
        for (String path : new String[] {"/api/goals", "/api/routine-items", "/api/learning-topics",
                "/api/daily-logs", "/api/tasks", "/api/schedule-templates"}) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
            mockMvc.perform(get(path).param("userId", "1")).andExpect(status().isUnauthorized());
            mockMvc.perform(post(path).contentType(JSON).content("{}")).andExpect(status().isUnauthorized());
        }
        mockMvc.perform(patch("/api/tasks/1").contentType(JSON).content("{\"status\": \"DONE\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createdRowsBelongToTheTokensUserWhateverTheRequestSays() throws Exception {
        long a = saveUser("pd-token-a@example.com");
        long b = saveUser("pd-token-b@example.com");

        long goal = create("/api/goals", a, "{\"name\": \"pd-A-goal\", \"userId\": " + b + "}");
        long item = create("/api/routine-items", a, "{\"name\": \"pd-A-routine\", \"userId\": " + b + "}");
        create("/api/learning-topics", a, "{\"name\": \"pd-A-topic\", \"goalId\": " + goal + ", \"userId\": " + b + "}");
        create("/api/schedule-templates", a, "{\"dayOfWeek\": \"MONDAY\", \"targetType\": \"LEARNING_SLOT\", "
                + "\"targetDurationMinutes\": 30, \"sortOrder\": 1, \"label\": \"pd-A-template\", \"userId\": " + b + "}");
        create("/api/tasks", a, "{\"taskDate\": \"2034-01-01\", \"label\": \"pd-A-task\", \"userId\": " + b + "}");
        create("/api/daily-logs", a, "{\"logDate\": \"2034-01-01\", \"routineItemId\": " + item + ", \"userId\": " + b + "}");

        for (String path : new String[] {"/api/goals", "/api/routine-items", "/api/learning-topics",
                "/api/schedule-templates", "/api/tasks"}) {
            assertThat(list(path, a)).as(path + " for A").contains("pd-A-");
            // B's token, with a request naming A, still sees nothing of A's.
            assertThat(mockMvc.perform(get(path).param("userId", String.valueOf(a)).with(asUser(b)))
                    .andReturn().getResponse().getContentAsString()).as(path + " for B").doesNotContain("pd-A-");
        }
        assertThat(list("/api/daily-logs", a)).contains("\"routineItemId\":" + item);
        assertThat(list("/api/daily-logs", b)).doesNotContain("\"routineItemId\":" + item);
    }

    @Test
    void anotherUsersTaskCannotBeChanged() throws Exception {
        long a = saveUser("pd-token-c@example.com");
        long b = saveUser("pd-token-d@example.com");
        long task = create("/api/tasks", a, "{\"taskDate\": \"2034-02-02\", \"label\": \"pd-C-task\"}");

        mockMvc.perform(patch("/api/tasks/" + task).param("userId", String.valueOf(a)).with(asUser(b))
                        .contentType(JSON).content("{\"status\": \"DONE\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/tasks/" + task).with(asUser(a)).contentType(JSON).content("{\"status\": \"DONE\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void aRowCannotLinkToAnotherUsersRow() throws Exception {
        long a = saveUser("pd-token-e@example.com");
        long b = saveUser("pd-token-f@example.com");
        long bsGoal = create("/api/goals", b, "{\"name\": \"pd-F-goal\"}");

        mockMvc.perform(post("/api/learning-topics").with(asUser(a)).contentType(JSON)
                        .content("{\"name\": \"x\", \"goalId\": " + bsGoal + "}"))
                .andExpect(status().isBadRequest());
    }
}
