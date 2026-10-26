package com.routinemachine.core.api;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.UserRepository;
import com.routinemachine.core.repository.WeeklyFailureModeRepository;
import com.routinemachine.core.service.WeeklyFailureModeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Staged Prompt 7: the weekly "Which failure mode did you meet this week?" answer (docs/backend.md, "Failure modes").
@SpringBootTest
@AutoConfigureMockMvc
class FailureModeApiIntegrationTest extends AbstractIntegrationTest {

    private static final String TEST_SECRET = "test-only-secret-not-for-real-use-0123456789";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private WeeklyFailureModeRepository repository;

    private String signedToken(Long userId) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                .expirationTime(new Date(System.currentTimeMillis() + 60_000))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(TEST_SECRET.getBytes()));
        return jwt.serialize();
    }

    private User newUser(String email) {
        return userRepository.save(new User(email, "irrelevant-hash", null, null));
    }

    private static String thisWeek() {
        return WeeklyFailureModeService.weekStart(LocalDate.now(ZoneId.of("UTC"))).toString();
    }

    @Test
    void noAnswerYetShowsTheCurrentWeekAsUnanswered() throws Exception {
        User user = newUser("fm-none@example.com");
        mockMvc.perform(get("/api/failure-modes/current").header("Authorization", "Bearer " + signedToken(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekStart", is(thisWeek())))
                .andExpect(jsonPath("$.answered", is(false)))
                .andExpect(jsonPath("$.failureMode", nullValue()));
    }

    @Test
    void anAnswerIsSavedAndReadBack() throws Exception {
        User user = newUser("fm-answer@example.com");
        String token = "Bearer " + signedToken(user.getId());
        mockMvc.perform(put("/api/failure-modes/current").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"failureMode\": \"PROGRESSION\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answered", is(true)))
                .andExpect(jsonPath("$.failureMode", is("PROGRESSION")));
        mockMvc.perform(get("/api/failure-modes/current").header("Authorization", token))
                .andExpect(jsonPath("$.failureMode", is("PROGRESSION")))
                .andExpect(jsonPath("$.weekStart", is(thisWeek())));
    }

    @Test
    void answeringAgainInTheSameWeekChangesTheAnswerAndKeepsOneRow() throws Exception {
        User user = newUser("fm-change@example.com");
        String token = "Bearer " + signedToken(user.getId());
        for (String mode : new String[] {"FORMING", "MISLEAD"}) {
            mockMvc.perform(put("/api/failure-modes/current").header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"failureMode\": \"" + mode + "\"}"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/failure-modes/current").header("Authorization", token))
                .andExpect(jsonPath("$.failureMode", is("MISLEAD")));
        assertThat(repository.findAll().stream().filter(r -> r.getUser().getId().equals(user.getId())).count()).isEqualTo(1);
    }

    @Test
    void allEightModesAreAccepted() throws Exception {
        User user = newUser("fm-all@example.com");
        String token = "Bearer " + signedToken(user.getId());
        for (String mode : new String[] {"FORMING", "DISLODGING", "ASSUMPTION", "LOCATION", "ACHIEVEMENT", "PROGRESSION",
                "INTERRUPTION", "MISLEAD"}) {
            mockMvc.perform(put("/api/failure-modes/current").header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"failureMode\": \"" + mode + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.failureMode", is(mode)));
        }
    }

    @Test
    void aMadeUpModeOrNoModeIsRefusedAndNothingIsSaved() throws Exception {
        User user = newUser("fm-bad@example.com");
        String token = "Bearer " + signedToken(user.getId());
        for (String body : new String[] {"{\"failureMode\": \"LAZINESS\"}", "{\"failureMode\": null}", "{}", "{\"failureMode\": \"\"}"}) {
            mockMvc.perform(put("/api/failure-modes/current").header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/api/failure-modes/current").header("Authorization", token))
                .andExpect(jsonPath("$.answered", is(false)));
    }

    @Test
    void oneMansAnswerIsNeverAnotherMans() throws Exception {
        User a = newUser("fm-a@example.com");
        User b = newUser("fm-b@example.com");
        mockMvc.perform(put("/api/failure-modes/current").header("Authorization", "Bearer " + signedToken(a.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"failureMode\": \"ACHIEVEMENT\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/failure-modes/current").header("Authorization", "Bearer " + signedToken(b.getId())))
                .andExpect(jsonPath("$.answered", is(false)));
    }

    @Test
    void withoutATokenTheRoutesAreRefused() throws Exception {
        mockMvc.perform(get("/api/failure-modes/current")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/failure-modes/current").contentType(MediaType.APPLICATION_JSON)
                .content("{\"failureMode\": \"FORMING\"}")).andExpect(status().isUnauthorized());
    }
}
