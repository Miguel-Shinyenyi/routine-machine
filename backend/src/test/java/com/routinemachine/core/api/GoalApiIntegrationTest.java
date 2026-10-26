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

import static com.routinemachine.core.TestTokens.asUser;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GoalApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;

    private long newUser(String email) {
        return userRepository.save(new User(email, "irrelevant-hash", null, null)).getId();
    }

    @Test
    void createsAGoalForTheSignedInUserAndReturnsIt() throws Exception {
        long userId = newUser("goal-owner@example.com");

        mockMvc.perform(post("/api/goals").with(asUser(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"learn rust\", \"description\": \"ship a small CLI\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name", is("learn rust")))
                .andExpect(jsonPath("$.description", is("ship a small CLI")))
                .andExpect(jsonPath("$.userId", is((int) userId)));
    }

    @Test
    void rejectsAGoalWithNoName() throws Exception {
        long userId = newUser("goal-noname@example.com");

        mockMvc.perform(post("/api/goals").with(asUser(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\": \"missing a name\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAGoalForATokenWhoseUserDoesNotExist() throws Exception {
        mockMvc.perform(post("/api/goals").with(asUser(999999))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"orphan goal\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsASecondGoalWithTheSameNameForTheSameUser() throws Exception {
        long userId = newUser("goal-dup@example.com");

        mockMvc.perform(post("/api/goals").with(asUser(userId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"repeat\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/goals").with(asUser(userId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"repeat\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void goalsCreatedForOneUserAreNotVisibleWhenListingAnother() throws Exception {
        long userA = newUser("goal-list-a@example.com");
        long userB = newUser("goal-list-b@example.com");

        mockMvc.perform(post("/api/goals").with(asUser(userA))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"User A's goal\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/goals").with(asUser(userA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].name", is("User A's goal")));

        mockMvc.perform(get("/api/goals").with(asUser(userB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));
    }
}
