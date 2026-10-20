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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Per-user scoping, part 2c: goals, routine items and daily logs. userId omitted used to return
// every account's rows; it now returns only unowned (pre-account) rows. Names, labels and dates
// are unique to each test: the database is shared across this class.
@SpringBootTest
@AutoConfigureMockMvc
class ScopedGoalsItemsAndLogsApiIntegrationTest extends AbstractIntegrationTest {

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

    private String userField(Long userId) {
        return userId == null ? "" : ", \"userId\": " + userId;
    }

    private long createGoal(String name, Long userId) throws Exception {
        return idOf(mockMvc.perform(post("/api/goals").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"" + name + "\"" + userField(userId) + "}"))
                .andExpect(status().isCreated()).andReturn());
    }

    private long createRoutineItem(String name, Long userId) throws Exception {
        return idOf(mockMvc.perform(post("/api/routine-items").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"" + name + "\"" + userField(userId) + "}"))
                .andExpect(status().isCreated()).andReturn());
    }

    private String body(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        return mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void omittingUserIdListsOnlyUnownedGoals() throws Exception {
        long a = saveUser("goal-a@example.com");
        createGoal("goal-owned-by-A", a);
        createGoal("goal-unowned", null);

        assertThat(body(get("/api/goals"))).contains("goal-unowned").doesNotContain("goal-owned-by-A");
        assertThat(body(get("/api/goals").param("userId", String.valueOf(a)))).contains("goal-owned-by-A")
                .doesNotContain("goal-unowned");
    }

    @Test
    void aNewUserHasNoGoals() throws Exception {
        long newcomer = saveUser("goal-new@example.com");
        createGoal("goal-someone-elses", saveUser("goal-other@example.com"));

        assertThat(body(get("/api/goals").param("userId", String.valueOf(newcomer)))).isEqualTo("[]");
    }

    @Test
    void omittingUserIdListsOnlyUnownedRoutineItems() throws Exception {
        long a = saveUser("item-a@example.com");
        createRoutineItem("item-owned-by-A", a);
        createRoutineItem("item-unowned", null);

        assertThat(body(get("/api/routine-items"))).contains("item-unowned").doesNotContain("item-owned-by-A");
    }

    @Test
    void aRoutineItemCannotBeCreatedUnderSomeoneElsesGoal() throws Exception {
        long a = saveUser("item-b@example.com");
        long b = saveUser("item-c@example.com");
        long bsGoal = createGoal("item-Bs-goal", b);

        mockMvc.perform(post("/api/routine-items").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"item-intruder\", \"goalId\": " + bsGoal + ", \"userId\": " + a + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void omittingUserIdListsOnlyUnownedDailyLogs() throws Exception {
        long a = saveUser("log-a@example.com");
        long aItem = createRoutineItem("log-A-routine", a);
        long unownedItem = createRoutineItem("log-unowned-routine", null);
        mockMvc.perform(post("/api/daily-logs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"logDate\": \"2032-01-01\", \"routineItemId\": " + aItem + ", \"userId\": " + a + "}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/daily-logs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"logDate\": \"2032-01-01\", \"routineItemId\": " + unownedItem + "}"))
                .andExpect(status().isCreated());

        String forTheDay = body(get("/api/daily-logs").param("date", "2032-01-01"));
        assertThat(forTheDay).contains("\"routineItemId\":" + unownedItem).doesNotContain("\"routineItemId\":" + aItem);
        String everything = body(get("/api/daily-logs"));
        assertThat(everything).doesNotContain("\"routineItemId\":" + aItem);
    }

    @Test
    void aDayCannotBeLoggedForSomeoneElsesRoutineItem() throws Exception {
        long a = saveUser("log-b@example.com");
        long b = saveUser("log-c@example.com");
        long bsItem = createRoutineItem("log-Bs-routine", b);

        mockMvc.perform(post("/api/daily-logs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"logDate\": \"2032-02-02\", \"routineItemId\": " + bsItem + ", \"userId\": " + a + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aDayCannotBeLoggedForSomeoneElsesLearningTopic() throws Exception {
        long a = saveUser("log-d@example.com");
        long b = saveUser("log-e@example.com");
        long bsGoal = createGoal("log-Bs-goal", b);
        long bsTopic = idOf(mockMvc.perform(post("/api/learning-topics").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"log-Bs-topic\", \"goalId\": " + bsGoal + ", \"userId\": " + b + "}"))
                .andExpect(status().isCreated()).andReturn());

        mockMvc.perform(post("/api/daily-logs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"logDate\": \"2032-03-03\", \"learningTopicId\": " + bsTopic + ", \"userId\": " + a + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aUserCanLogTheirOwnRoutineItem() throws Exception {
        long a = saveUser("log-f@example.com");
        long aItem = createRoutineItem("log-F-routine", a);

        mockMvc.perform(post("/api/daily-logs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"logDate\": \"2032-04-04\", \"routineItemId\": " + aItem + ", \"userId\": " + a + "}"))
                .andExpect(status().isCreated());

        assertThat(body(get("/api/daily-logs").param("userId", String.valueOf(a)).param("date", "2032-04-04")))
                .contains("\"routineItemId\":" + aItem);
    }
}
