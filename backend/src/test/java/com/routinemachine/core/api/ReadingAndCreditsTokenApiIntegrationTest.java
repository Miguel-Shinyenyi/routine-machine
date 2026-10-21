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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static com.routinemachine.core.TestTokens.asUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Access control, part 3: books, reading notes, current reading, Watering holes and evidence
// credits act for the person in the signed token. No token is 401. A user id in the request
// (query or body) is not read at all.
@SpringBootTest
@AutoConfigureMockMvc
class ReadingAndCreditsTokenApiIntegrationTest extends AbstractIntegrationTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private GoalRepository goalRepository;

    private User saveUser(String email) {
        return userRepository.save(new User(email, "irrelevant-hash", null, null));
    }

    private long idOf(MvcResult result) throws Exception {
        return Long.parseLong(result.getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    private long create(String path, long asUser, String json) throws Exception {
        return idOf(mockMvc.perform(post(path).with(asUser(asUser)).contentType(JSON).content(json))
                .andExpect(status().isCreated()).andReturn());
    }

    private String read(String path, long asUser, String... queryPairs) throws Exception {
        var request = get(path).with(asUser(asUser));
        for (int i = 0; i < queryPairs.length; i += 2) {
            request = request.param(queryPairs[i], queryPairs[i + 1]);
        }
        return mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void everyRouteRefusesARequestWithNoToken() throws Exception {
        for (String path : new String[] {"/api/books", "/api/books/current", "/api/reading-notes",
                "/api/current-reading", "/api/watering-holes", "/api/evidence-events"}) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
            mockMvc.perform(get(path).param("userId", "1")).andExpect(status().isUnauthorized());
        }
        for (String path : new String[] {"/api/books", "/api/reading-notes", "/api/current-reading",
                "/api/evidence-events"}) {
            mockMvc.perform(post(path).contentType(JSON).content("{}")).andExpect(status().isUnauthorized());
        }
        mockMvc.perform(patch("/api/evidence-events/1/question")).andExpect(status().isUnauthorized());
    }

    @Test
    void createdRowsBelongToTheTokensUserWhateverTheRequestSays() throws Exception {
        User a = saveUser("rc-token-a@example.com");
        User b = saveUser("rc-token-b@example.com");

        long book = create("/api/books", a.getId(), "{\"title\": \"rc-A-book\", \"userId\": " + b.getId() + "}");
        create("/api/reading-notes", a.getId(),
                "{\"bookId\": " + book + ", \"noteText\": \"rc-A-note\", \"userId\": " + b.getId() + "}");
        create("/api/current-reading", a.getId(), "{\"title\": \"rc-A-reading\", \"userId\": " + b.getId() + "}");

        for (String path : new String[] {"/api/books", "/api/reading-notes", "/api/current-reading", "/api/books/current"}) {
            assertThat(read(path, a.getId())).as(path + " for A").contains("rc-A-");
            // B's token, with a request naming A, still sees nothing of A's.
            String asB = mockMvc.perform(get(path).param("userId", String.valueOf(a.getId())).with(asUser(b.getId())))
                    .andReturn().getResponse().getContentAsString();
            assertThat(asB).as(path + " for B").doesNotContain("rc-A-");
        }
    }

    @Test
    void aNoteCannotBeWrittenOnAnotherUsersBook() throws Exception {
        User a = saveUser("rc-token-c@example.com");
        User b = saveUser("rc-token-d@example.com");
        long bsBook = create("/api/books", b.getId(), "{\"title\": \"rc-D-book\"}");

        mockMvc.perform(post("/api/reading-notes").with(asUser(a.getId())).contentType(JSON)
                        .content("{\"bookId\": " + bsBook + ", \"noteText\": \"intruder\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void creditsBelongToTheTokensUserAndOnlyTheyCanQuestionThem() throws Exception {
        User a = saveUser("rc-token-e@example.com");
        User b = saveUser("rc-token-f@example.com");
        Goal goal = goalRepository.save(new Goal("rc-E-goal", null, a));

        long event = create("/api/evidence-events", a.getId(), "{\"userId\": " + b.getId() + ", \"goalId\": " + goal.getId()
                + ", \"source\": \"MANUAL\", \"sourceEventId\": \"rc-evt\", \"occurredAt\": \"2034-03-02T12:00:00Z\"}");

        assertThat(read("/api/evidence-events", a.getId(), "seasonStart", "2034-02-27", "seasonEnd", "2034-03-05"))
                .contains("rc-E-goal");
        assertThat(read("/api/evidence-events", b.getId(), "seasonStart", "2034-02-27", "seasonEnd", "2034-03-05"))
                .doesNotContain("rc-E-goal");

        mockMvc.perform(patch("/api/evidence-events/" + event + "/question")
                        .param("userId", String.valueOf(a.getId())).with(asUser(b.getId())))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/evidence-events/" + event + "/question").with(asUser(a.getId())))
                .andExpect(status().isOk());
    }

    @Autowired
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;

    @Test
    void questioningACreditIsCountedOnceEvenIfAskedTwice() throws Exception {
        User a = saveUser("rc-token-m@example.com");
        Goal goal = goalRepository.save(new Goal("rc-M-goal", null, a));
        long event = create("/api/evidence-events", a.getId(), "{\"goalId\": " + goal.getId()
                + ", \"source\": \"MANUAL\", \"sourceEventId\": \"rc-q\", \"occurredAt\": \"2034-03-02T12:00:00Z\"}");
        var counter = meterRegistry.counter("credits.questioned");
        double before = counter.count();

        mockMvc.perform(patch("/api/evidence-events/" + event + "/question").with(asUser(a.getId())))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/evidence-events/" + event + "/question").with(asUser(a.getId())))
                .andExpect(status().isOk());

        assertThat(counter.count() - before).isEqualTo(1);
    }

    @Test
    void creditForAnotherUsersGoalIsRefused() throws Exception {
        User a = saveUser("rc-token-g@example.com");
        User b = saveUser("rc-token-h@example.com");
        Goal bsGoal = goalRepository.save(new Goal("rc-H-goal", null, b));

        mockMvc.perform(post("/api/evidence-events").with(asUser(a.getId())).contentType(JSON)
                        .content("{\"goalId\": " + bsGoal.getId() + ", \"source\": \"MANUAL\", "
                                + "\"sourceEventId\": \"rc-intruder\", \"occurredAt\": \"2034-03-02T12:00:00Z\"}"))
                .andExpect(status().isBadRequest());
    }
}
