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

    // Phase 10's explicit privacy requirement (docs/product-direction.md's Profile section:
    // "it never reaches the hillside, and your programme never sees it"). A distinctive
    // profile value is set, then the raw hillside v2 response body is checked for it directly
    // — not just that HillsideV2Response's fields don't name it (true by construction), but
    // that the actual JSON sent over the wire never contains it, however the response is built.
    @Test
    void profileDataNeverAppearsInTheHillsideResponse() throws Exception {
        User user = userRepository.save(new User("v2-privacy@example.com", "irrelevant-hash", null, null));
        user.optIntoHillside();
        user.updateProfile(
                java.util.List.of("a-very-private-interest-marker-xyz"), java.util.List.of("a-private-thinker-marker"),
                java.util.List.of("a-private-learning-pref-marker"), "PRIVMARK", null, null, null);
        userRepository.save(user);
        Goal goal = goalRepository.save(new Goal("v2-privacy-goal", null));

        EvidenceCreditService service =
                new EvidenceCreditService(evidenceEventRepository, dailyCreditRepository, scoringRuleRepository);
        // A different season window from this class's other tests (both hardcode 2026-10-12)
        // — this class's tests share one Postgres instance per class with no per-method
        // rollback, so a shared window would make totalParticipants order-dependent.
        LocalDate day = LocalDate.of(2026, 9, 7);
        service.ingest(user, goal, DailyLogSource.MANUAL, "v2-privacy-evt", day.atStartOfDay(ZoneOffset.UTC).toInstant(), null);

        String body = mockMvc.perform(get("/api/hillside/v2")
                        .param("seasonStart", "2026-09-07")
                        .param("asUserId", user.getId().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("a-very-private-interest-marker-xyz")
                .doesNotContain("a-private-thinker-marker")
                .doesNotContain("a-private-learning-pref-marker")
                .doesNotContain("PRIVMARK");
    }

    // Phase 11's explicit boundary (docs/product-direction.md's granary section): "Granary
    // data is excluded from the hillside and from admin views... Test it." Same technique as
    // the profile privacy test above: distinctive marker strings in a pause decision, checked
    // against the raw hillside v2 response body.
    @Test
    void granaryDataNeverAppearsInTheHillsideResponse() throws Exception {
        User user = userRepository.save(new User("v2-granary-privacy@example.com", "irrelevant-hash", null, null));
        user.optIntoHillside();
        userRepository.save(user);
        Goal goal = goalRepository.save(new Goal("v2-granary-privacy-goal", null));

        EvidenceCreditService service =
                new EvidenceCreditService(evidenceEventRepository, dailyCreditRepository, scoringRuleRepository);
        LocalDate day = LocalDate.of(2026, 8, 3);
        service.ingest(user, goal, DailyLogSource.MANUAL, "v2-granary-privacy-evt",
                day.atStartOfDay(ZoneOffset.UTC).toInstant(), null);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/granary/pauses")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"userId\": " + user.getId() + ", \"what\": \"a-granary-what-marker\", "
                                + "\"amount\": \"a-granary-amount-marker\", \"why\": \"a-granary-why-marker\", "
                                + "\"heardFrom\": \"a-granary-heard-from-marker\"}"))
                .andExpect(status().isOk());

        String body = mockMvc.perform(get("/api/hillside/v2")
                        .param("seasonStart", "2026-08-03")
                        .param("asUserId", user.getId().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("a-granary-what-marker")
                .doesNotContain("a-granary-amount-marker")
                .doesNotContain("a-granary-why-marker")
                .doesNotContain("a-granary-heard-from-marker");
    }
}
