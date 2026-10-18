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
