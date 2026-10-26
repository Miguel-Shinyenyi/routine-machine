package com.routinemachine.core.api;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Date;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The same shared secret AbstractIntegrationTest sets for every test context
// ("auth.jwt-secret"), used here to build a REAL signed token the same way Next.js's server
// will, rather than mocking Spring Security's authentication context: this is the one test in
// the suite that proves the actual signature verification wiring works, not just the
// authorization rule around it.
@SpringBootTest
@AutoConfigureMockMvc
class UserControllerApiIntegrationTest extends AbstractIntegrationTest {

    private static final String TEST_SECRET = "test-only-secret-not-for-real-use-0123456789";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

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
    void aValidSignedTokenReturnsTheAuthenticatedUser() throws Exception {
        User user = userRepository.save(new User("reader@example.com", "irrelevant-hash", null, "Reader"));

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + signedToken(user.getId().toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("reader@example.com")))
                .andExpect(jsonPath("$.hillsideOptIn", is(false)));
    }

    @Test
    void optingIntoTheHillsideUpdatesAndReturnsTheUser() throws Exception {
        User user = userRepository.save(new User("opt-in@example.com", "irrelevant-hash", null, null));

        mockMvc.perform(put("/api/users/me/hillside-opt-in")
                        .header("Authorization", "Bearer " + signedToken(user.getId().toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optIn\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hillsideOptIn", is(true)));

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + signedToken(user.getId().toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hillsideOptIn", is(true)));
    }

    @Test
    void optingOutOfTheHillsideUpdatesAndReturnsTheUser() throws Exception {
        User user = new User("opt-out@example.com", "irrelevant-hash", null, null);
        user.optIntoHillside();
        user = userRepository.save(user);

        mockMvc.perform(put("/api/users/me/hillside-opt-in")
                        .header("Authorization", "Bearer " + signedToken(user.getId().toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optIn\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hillsideOptIn", is(false)));
    }

    @Test
    void theHillsideOptInEndpointRequiresAValidToken() throws Exception {
        mockMvc.perform(put("/api/users/me/hillside-opt-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optIn\": true}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void gettingTheProfileReturnsDefaultsForANewUser() throws Exception {
        User user = userRepository.save(new User("profile-defaults@example.com", "irrelevant-hash", null, null));

        mockMvc.perform(get("/api/users/me/profile")
                        .header("Authorization", "Bearer " + signedToken(user.getId().toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dayEndHour", is(0)))
                .andExpect(jsonPath("$.timeZone", is("UTC")))
                .andExpect(jsonPath("$.analogiesEnabled", is(true)))
                .andExpect(jsonPath("$.failureModePromptDay", is("MONDAY")));
    }

    @Test
    void updatingTheProfileChangesOnlyTheFieldsSent() throws Exception {
        User user = userRepository.save(new User("profile-update@example.com", "irrelevant-hash", null, null));

        mockMvc.perform(put("/api/users/me/profile")
                        .header("Authorization", "Bearer " + signedToken(user.getId().toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"interests\": [\"football\", \"philosophy\"], \"dayEndHour\": 3, "
                                + "\"timeZone\": \"Africa/Nairobi\", \"currency\": \"KES\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interests[0]", is("football")))
                .andExpect(jsonPath("$.dayEndHour", is(3)))
                .andExpect(jsonPath("$.timeZone", is("Africa/Nairobi")))
                .andExpect(jsonPath("$.currency", is("KES")))
                .andExpect(jsonPath("$.analogiesEnabled", is(true)));

        mockMvc.perform(put("/api/users/me/profile")
                        .header("Authorization", "Bearer " + signedToken(user.getId().toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"analogiesEnabled\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interests[0]", is("football")))
                .andExpect(jsonPath("$.analogiesEnabled", is(false)));
    }

    @Test
    void anOutOfRangeDayEndHourIsRejected() throws Exception {
        User user = userRepository.save(new User("profile-bad-hour@example.com", "irrelevant-hash", null, null));

        mockMvc.perform(put("/api/users/me/profile")
                        .header("Authorization", "Bearer " + signedToken(user.getId().toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dayEndHour\": 24}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aTimeZoneThatIsNotARealZoneIsRejectedAndTheSavedZoneStays() throws Exception {
        User user = userRepository.save(new User("profile-bad-zone@example.com", "irrelevant-hash", null, null));

        for (String bad : new String[] {"Mars/Base", "not a zone", "   "}) {
            mockMvc.perform(put("/api/users/me/profile")
                            .header("Authorization", "Bearer " + signedToken(user.getId().toString()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"timeZone\": \"" + bad + "\"}"))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(get("/api/users/me/profile")
                        .header("Authorization", "Bearer " + signedToken(user.getId().toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeZone", is("UTC")));
    }

    @Test
    void theDayTheWeeklyFailureModeQuestionStartsOnIsChosenAndKept() throws Exception {
        User user = userRepository.save(new User("profile-prompt-day@example.com", "irrelevant-hash", null, null));
        String token = "Bearer " + signedToken(user.getId().toString());

        mockMvc.perform(put("/api/users/me/profile").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"failureModePromptDay\": \"THURSDAY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failureModePromptDay", is("THURSDAY")));

        // A later update that does not mention it leaves it alone.
        mockMvc.perform(put("/api/users/me/profile").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dayEndHour\": 2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failureModePromptDay", is("THURSDAY")))
                .andExpect(jsonPath("$.dayEndHour", is(2)));

        mockMvc.perform(get("/api/users/me/profile").header("Authorization", token))
                .andExpect(jsonPath("$.failureModePromptDay", is("THURSDAY")));
    }

    @Test
    void aDayThatIsNotADayOfTheWeekIsRejectedAndTheChoiceStays() throws Exception {
        User user = userRepository.save(new User("profile-bad-prompt-day@example.com", "irrelevant-hash", null, null));
        String token = "Bearer " + signedToken(user.getId().toString());

        for (String bad : new String[] {"FUNDAY", "monday", ""}) {
            mockMvc.perform(put("/api/users/me/profile").header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"failureModePromptDay\": \"" + bad + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/api/users/me/profile").header("Authorization", token))
                .andExpect(jsonPath("$.failureModePromptDay", is("MONDAY")));
    }

    @Test
    void theProfileEndpointsRequireAValidToken() throws Exception {
        mockMvc.perform(get("/api/users/me/profile")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/users/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aMissingTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenSignedWithTheWrongSecretIsRejected() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().subject("1").build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner("a-completely-different-secret-0123456789ab".getBytes()));

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + jwt.serialize()))
                .andExpect(status().isUnauthorized());
    }
}
