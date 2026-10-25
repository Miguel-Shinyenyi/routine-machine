package com.routinemachine.core.api;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.DailyLogRepository;
import com.routinemachine.core.repository.GoalRepository;
import com.routinemachine.core.repository.RoutineItemRepository;
import com.routinemachine.core.repository.ScheduleTemplateRepository;
import com.routinemachine.core.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"auth.internal-token=the-real-internal-token", "demo.enabled=true"})
class DemoLoginEnabledApiIntegrationTest extends AbstractIntegrationTest {

    private static final String TEST_SECRET = "test-only-secret-not-for-real-use-0123456789";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private GoalRepository goalRepository;
    @Autowired private RoutineItemRepository routineItemRepository;
    @Autowired private ScheduleTemplateRepository scheduleTemplateRepository;
    @Autowired private DailyLogRepository dailyLogRepository;

    private MvcResult openDemo() throws Exception {
        return mockMvc.perform(post("/api/auth/demo-login")
                        .header("Authorization", "Bearer the-real-internal-token"))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String signedToken(String subject) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .expirationTime(new Date(System.currentTimeMillis() + 60_000))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(TEST_SECRET.getBytes()));
        return jwt.serialize();
    }

    @Test
    void withoutTheInternalTokenTheDemoLoginIsRefused() throws Exception {
        mockMvc.perform(post("/api/auth/demo-login")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/demo-login").header("Authorization", "Bearer wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void opensAnAccountFlaggedAsDemoOnAnAddressThatCannotReceiveMail() throws Exception {
        mockMvc.perform(post("/api/auth/demo-login").header("Authorization", "Bearer the-real-internal-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demo", is(true)))
                .andExpect(jsonPath("$.email", endsWith(".invalid")));
    }

    @Test
    void seedsSampleGoalsRoutinesScheduleAndLogsForThatUserOnly() throws Exception {
        openDemo();
        User demo = userRepository.findAll().stream().filter(User::isDemo).findFirst().orElseThrow();

        assertThat(goalRepository.findByUserId(demo.getId())).hasSizeGreaterThanOrEqualTo(2);
        assertThat(routineItemRepository.findAllWithGoalByUserId(demo.getId())).hasSizeGreaterThanOrEqualTo(3);
        assertThat(scheduleTemplateRepository.findByUserId(demo.getId())).hasSizeGreaterThanOrEqualTo(7);
        assertThat(dailyLogRepository.findByUserId(demo.getId())).hasSizeGreaterThanOrEqualTo(10);
        // Everything belongs to the demo account; no row is left without an owner.
        assertThat(goalRepository.findByUserId(demo.getId()))
                .allSatisfy(goal -> assertThat(goal.getName()).isNotBlank());
    }

    @Test
    void openingTheDemoAgainReusesTheAccountAndAddsNoRows() throws Exception {
        openDemo();
        User demo = userRepository.findAll().stream().filter(User::isDemo).findFirst().orElseThrow();
        int goals = goalRepository.findByUserId(demo.getId()).size();
        int items = routineItemRepository.findAllWithGoalByUserId(demo.getId()).size();
        int templates = scheduleTemplateRepository.findByUserId(demo.getId()).size();
        int logs = dailyLogRepository.findByUserId(demo.getId()).size();

        openDemo();

        assertThat(userRepository.findAll().stream().filter(User::isDemo).count()).isEqualTo(1);
        assertThat(goalRepository.findByUserId(demo.getId())).hasSize(goals);
        assertThat(routineItemRepository.findAllWithGoalByUserId(demo.getId())).hasSize(items);
        assertThat(scheduleTemplateRepository.findByUserId(demo.getId())).hasSize(templates);
        assertThat(dailyLogRepository.findByUserId(demo.getId())).hasSize(logs);
    }

    @Test
    void theDemoAccountCannotBeSignedInToWithAPassword() throws Exception {
        openDemo();
        User demo = userRepository.findAll().stream().filter(User::isDemo).findFirst().orElseThrow();
        for (String guess : new String[] {"password", "demo", "demo-password", ""}) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\": \"" + demo.getEmail() + "\", \"password\": \"" + guess + "\"}"))
                    .andExpect(status().is4xxClientError());
        }
    }

    @Test
    void anotherPersonNeverSeesTheDemoData_andOrdinaryAccountsAreNotDemo() throws Exception {
        openDemo();
        User other = userRepository.save(new User("not-the-demo@example.com", "irrelevant-hash", null, null));

        mockMvc.perform(get("/api/goals").header("Authorization", "Bearer " + signedToken(other.getId().toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + signedToken(other.getId().toString())))
                .andExpect(jsonPath("$.demo", is(false)));
    }
}
