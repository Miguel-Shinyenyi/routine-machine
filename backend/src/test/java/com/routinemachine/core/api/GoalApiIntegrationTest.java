package com.routinemachine.core.api;

import com.routinemachine.core.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GoalApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listsTheThreeSeededGoals() throws Exception {
        mockMvc.perform(get("/api/goals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("cmu-masters-application")))
                .andExpect(jsonPath("$[*].name", hasItem("target-roles")))
                .andExpect(jsonPath("$[*].name", hasItem("ai-engineer-evidence")));
    }

    private long registerUser(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + email + "\", \"password\": \"a-strong-password\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    @Test
    void createsAGoalForAUserAndReturnsIt() throws Exception {
        long userId = registerUser("goal-owner@example.com");

        mockMvc.perform(post("/api/goals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"learn rust\", \"description\": \"ship a small CLI\", \"userId\": "
                                + userId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name", is("learn rust")))
                .andExpect(jsonPath("$.description", is("ship a small CLI")))
                .andExpect(jsonPath("$.userId", is((int) userId)));
    }

    @Test
    void createsAGoalWithNoUserAndReturnsANullUserId() throws Exception {
        mockMvc.perform(post("/api/goals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"unowned goal\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId", nullValue()));
    }

    @Test
    void rejectsAGoalWithNoName() throws Exception {
        mockMvc.perform(post("/api/goals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\": \"missing a name\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAGoalWithAnUnknownUserId() throws Exception {
        mockMvc.perform(post("/api/goals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"orphan goal\", \"userId\": 999999}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsASecondGoalWithTheSameNameForTheSameUser() throws Exception {
        long userId = registerUser("goal-dup@example.com");

        mockMvc.perform(post("/api/goals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"repeat\", \"userId\": " + userId + "}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/goals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"repeat\", \"userId\": " + userId + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void goalsCreatedForOneUserAreNotVisibleWhenListingAnother() throws Exception {
        long userA = registerUser("goal-list-a@example.com");
        long userB = registerUser("goal-list-b@example.com");

        mockMvc.perform(post("/api/goals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"User A's goal\", \"userId\": " + userA + "}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/goals").param("userId", String.valueOf(userA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].name", is("User A's goal")));

        mockMvc.perform(get("/api/goals").param("userId", String.valueOf(userB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));
    }
}
