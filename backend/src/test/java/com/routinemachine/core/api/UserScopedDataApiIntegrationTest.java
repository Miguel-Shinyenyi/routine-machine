package com.routinemachine.core.api;

import com.routinemachine.core.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Phase 8 groundwork: routine_items and daily_logs can optionally be scoped to a user (see
// docs/backend.md's decisions log). This is additive, not required auth, so the only thing
// worth proving here is that passing userId actually isolates two different users' data from
// each other and from the unscoped (legacy, userId-less) view — not a full auth test suite.
@SpringBootTest
@AutoConfigureMockMvc
class UserScopedDataApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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
    void routineItemsCreatedForOneUserAreNotVisibleWhenListingAnother() throws Exception {
        long userA = registerUser("scoped-a@example.com");
        long userB = registerUser("scoped-b@example.com");

        mockMvc.perform(post("/api/routine-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"User A's routine\", \"userId\": " + userA + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId", is((int) userA)));

        mockMvc.perform(post("/api/routine-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"User B's routine\", \"userId\": " + userB + "}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/routine-items").param("userId", String.valueOf(userA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].name", is("User A's routine")));

        mockMvc.perform(get("/api/routine-items").param("userId", String.valueOf(userB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].name", is("User B's routine")));
    }

    @Test
    void dailyLogsCreatedForOneUserAreNotVisibleWhenListingAnother() throws Exception {
        long userA = registerUser("logs-a@example.com");
        long userB = registerUser("logs-b@example.com");

        MvcResult itemResult = mockMvc.perform(post("/api/routine-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Shared-shape item for log scoping test\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long routineItemId = Long.parseLong(
                itemResult.getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1"));

        mockMvc.perform(post("/api/daily-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"logDate\": \"2026-10-17\", \"routineItemId\": " + routineItemId
                                + ", \"userId\": " + userA + "}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/daily-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"logDate\": \"2026-10-17\", \"routineItemId\": " + routineItemId
                                + ", \"userId\": " + userB + "}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/daily-logs").param("userId", String.valueOf(userA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].userId", is((int) userA)));

        mockMvc.perform(get("/api/daily-logs").param("userId", String.valueOf(userB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].userId", is((int) userB)));
    }

    @Test
    void creatingARoutineItemWithAnUnknownUserIdFails() throws Exception {
        mockMvc.perform(post("/api/routine-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"orphan\", \"userId\": 999999}"))
                .andExpect(status().isBadRequest());
    }
}
