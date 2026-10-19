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

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HillsideV2ApiIntegrationTest extends AbstractIntegrationTest {

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

    @Test
    void returnsYourBandAndNeighboursForAnExplicitSeason() throws Exception {
        User you = userRepository.save(new User("v2-you@example.com", "irrelevant-hash", null, null));
        you.optIntoHillside();
        userRepository.save(you);
        Goal goal = goalRepository.save(new Goal("v2-goal", null));

        EvidenceCreditService service =
                new EvidenceCreditService(evidenceEventRepository, dailyCreditRepository, scoringRuleRepository);
        LocalDate day = LocalDate.of(2026, 10, 12);
        service.ingest(you, goal, DailyLogSource.MANUAL, "v2-evt-1", day.atStartOfDay(ZoneOffset.UTC).toInstant(), null);

        mockMvc.perform(get("/api/hillside/v2")
                        .param("seasonStart", "2026-10-12")
                        .param("asUserId", you.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalParticipants", is(1)))
                .andExpect(jsonPath("$.yourScore", is(1)))
                .andExpect(jsonPath("$.yourRank", is(1)));
    }

    @Test
    void aUserWithNoCreditsThisSeasonGetsNoPersonalBand() throws Exception {
        User user = userRepository.save(new User("v2-none@example.com", "irrelevant-hash", null, null));
        user.optIntoHillside();
        userRepository.save(user);

        mockMvc.perform(get("/api/hillside/v2")
                        .param("seasonStart", "2026-10-12")
                        .param("asUserId", user.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yourScore").doesNotExist());
    }
}
