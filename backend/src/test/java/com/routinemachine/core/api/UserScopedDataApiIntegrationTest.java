package com.routinemachine.core.api;

import com.routinemachine.core.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static com.routinemachine.core.TestTokens.asUser;
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

        mockMvc.perform(post("/api/routine-items").with(asUser(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"User A's routine\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId", is((int) userA)));

        mockMvc.perform(post("/api/routine-items").with(asUser(userB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"User B's routine\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/routine-items").with(asUser(userA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].name", is("User A's routine")));

        mockMvc.perform(get("/api/routine-items").with(asUser(userB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].name", is("User B's routine")));
    }

    private long createRoutineItemFor(String name, long userId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/routine-items").with(asUser(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return Long.parseLong(result.getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    @Test
    void dailyLogsCreatedForOneUserAreNotVisibleWhenListingAnother() throws Exception {
        long userA = registerUser("logs-a@example.com");
        long userB = registerUser("logs-b@example.com");

        // Each user logs their own routine item: since per-user scoping part 2c, a day cannot be
        // logged against an item owned by someone else (or by nobody) when a userId is given.
        long itemA = createRoutineItemFor("Log scoping item A", userA);
        long itemB = createRoutineItemFor("Log scoping item B", userB);

        mockMvc.perform(post("/api/daily-logs").with(asUser(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"logDate\": \"2026-10-17\", \"routineItemId\": " + itemA
                                + "}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/daily-logs").with(asUser(userB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"logDate\": \"2026-10-17\", \"routineItemId\": " + itemB
                                + "}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/daily-logs").with(asUser(userA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].userId", is((int) userA)));

        mockMvc.perform(get("/api/daily-logs").with(asUser(userB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].userId", is((int) userB)));
    }

    @Test
    void creatingARoutineItemForATokenWhoseUserDoesNotExistFails() throws Exception {
        mockMvc.perform(post("/api/routine-items").with(asUser(999999))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"orphan\"}"))
                .andExpect(status().isBadRequest());
    }
}
