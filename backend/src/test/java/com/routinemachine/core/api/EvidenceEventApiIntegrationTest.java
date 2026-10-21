package com.routinemachine.core.api;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.Goal;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.GoalRepository;
import com.routinemachine.core.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;

import static com.routinemachine.core.TestTokens.asUser;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EvidenceEventApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private com.routinemachine.core.repository.UserRepository meRepository;

    private com.routinemachine.core.domain.User me;

    @org.junit.jupiter.api.BeforeEach
    void signInAsANewUser() {
        me = meRepository.save(new com.routinemachine.core.domain.User(
                "EvidenceEventApiIntegrationTest-" + System.nanoTime() + "@example.com", "irrelevant-hash", null, null));
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private GoalRepository goalRepository;

    @Test
    void ingestingEvidenceCreatesAnAcceptedEventWhenNoRuleExists() throws Exception {
        User user = me;
        Goal goal = goalRepository.save(new Goal("api-evidence-goal", null, user));

        mockMvc.perform(post("/api/evidence-events").with(asUser(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goalId\": " + goal.getId() + ", "
                                + "\"source\": \"WAKATIME_SYNC\", \"sourceEventId\": \"evt-1\", "
                                + "\"occurredAt\": \"2026-10-16T12:00:00Z\", \"minutes\": 40}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("ACCEPTED")))
                .andExpect(jsonPath("$.goalName", is("api-evidence-goal")))
                .andExpect(jsonPath("$.localDate", is("2026-10-16")));
    }

    @Test
    void ingestingWithAnUnknownUserFails() throws Exception {
        Goal goal = goalRepository.save(new Goal("unknown-user-goal", null, me));

        mockMvc.perform(post("/api/evidence-events").with(asUser(999999))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goalId\": " + goal.getId() + ", "
                                + "\"source\": \"MANUAL\", \"sourceEventId\": \"evt-2\", "
                                + "\"occurredAt\": \"2026-10-16T12:00:00Z\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theLedgerListsEveryEventInTheWindowWhateverItsStatus() throws Exception {
        User user = me;
        Goal goal = goalRepository.save(new Goal("api-ledger-goal", null, user));

        mockMvc.perform(post("/api/evidence-events").with(asUser(me.getId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"goalId\": " + goal.getId() + ", "
                        + "\"source\": \"MANUAL\", \"sourceEventId\": \"evt-3\", "
                        + "\"occurredAt\": \"2026-10-16T12:00:00Z\"}"));

        mockMvc.perform(get("/api/evidence-events").with(asUser(me.getId()))
                        
                        .param("seasonStart", "2026-10-12")
                        .param("seasonEnd", "2026-10-18"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status", is("SELF_REPORTED")));
    }

    @Test
    void questioningACreditRecordsTheRequest() throws Exception {
        User user = me;
        Goal goal = goalRepository.save(new Goal("api-question-goal", null, user));

        MvcResult created = mockMvc.perform(post("/api/evidence-events").with(asUser(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goalId\": " + goal.getId() + ", "
                                + "\"source\": \"MANUAL\", \"sourceEventId\": \"evt-4\", "
                                + "\"occurredAt\": \"2026-10-16T12:00:00Z\"}"))
                .andReturn();
        String body = created.getResponse().getContentAsString();
        long id = Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mockMvc.perform(patch("/api/evidence-events/" + id + "/question").with(asUser(me.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questioned", is(true)));
    }
}
