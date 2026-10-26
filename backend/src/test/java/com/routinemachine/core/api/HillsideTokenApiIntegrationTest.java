package com.routinemachine.core.api;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.DailyLogSource;
import com.routinemachine.core.domain.Goal;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.DailyCreditRepository;
import com.routinemachine.core.repository.EvidenceEventRepository;
import com.routinemachine.core.repository.GoalRepository;
import com.routinemachine.core.repository.ScoringRuleRepository;
import com.routinemachine.core.repository.UserRepository;
import com.routinemachine.core.service.EvidenceCreditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.ZoneOffset;

import static com.routinemachine.core.TestTokens.asUser;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Access control, part 5: the hillside is a public ranking, but "you" in it comes only from a valid
// token. The old asUserId parameter let anyone read any opted-in person's rank and score by naming
// them, so it is gone and ignored if sent.
@SpringBootTest
@AutoConfigureMockMvc
class HillsideTokenApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private GoalRepository goalRepository;
    @Autowired
    private EvidenceEventRepository evidenceEventRepository;
    @Autowired
    private DailyCreditRepository dailyCreditRepository;
    @Autowired
    private ScoringRuleRepository scoringRuleRepository;

    private User optedIn(String email) {
        User user = userRepository.save(new User(email, "irrelevant-hash", null, null));
        user.optIntoHillside();
        return userRepository.save(user);
    }

    private void credit(User user, Goal goal, String eventId, LocalDate day) {
        new EvidenceCreditService(evidenceEventRepository, dailyCreditRepository, scoringRuleRepository).ingest(
                user, goal, DailyLogSource.MANUAL, eventId, day.atStartOfDay(ZoneOffset.UTC).toInstant(), null);
    }

    @Test
    void aVisitorWithNoTokenSeesTheRankingButNoPersonalBand() throws Exception {
        mockMvc.perform(get("/api/hillside/v2").param("seasonStart", "2034-03-06"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yourScore").doesNotExist());
        mockMvc.perform(get("/api/hillside")).andExpect(status().isOk())
                .andExpect(jsonPath("$.yourRank").doesNotExist());
    }

    @Test
    void youAreTheTokensUserNotWhoeverTheRequestNames() throws Exception {
        User a = optedIn("hill-token-a@example.com");
        User b = optedIn("hill-token-b@example.com");
        Goal goal = goalRepository.save(new Goal("hill-token-goal", null));
        LocalDate season = LocalDate.of(2034, 3, 6);
        credit(a, goal, "hill-a-1", season);
        credit(b, goal, "hill-b-1", season);
        credit(b, goal, "hill-b-2", season.plusDays(1));

        // A's token, but the request names B. The answer is A's standing (score 1), not B's (score 2).
        mockMvc.perform(get("/api/hillside/v2").param("seasonStart", "2034-03-06")
                        .param("asUserId", String.valueOf(b.getId())).with(asUser(a.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yourScore", is(1)));
        mockMvc.perform(get("/api/hillside/v2").param("seasonStart", "2034-03-06")
                        .param("asUserId", String.valueOf(b.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yourScore").doesNotExist());
    }

    @Test
    void aBadTokenIsRefusedNotTreatedAsAVisitor() throws Exception {
        mockMvc.perform(get("/api/hillside/v2").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }
}
