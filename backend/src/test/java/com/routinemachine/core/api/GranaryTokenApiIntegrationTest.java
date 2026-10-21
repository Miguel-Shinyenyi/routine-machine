package com.routinemachine.core.api;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Access control, part 1: the granary routes act for the person in the signed token and for
// nobody else. No token is 401. A user id in the request (query or body) is not read at all.
@SpringBootTest
@AutoConfigureMockMvc
class GranaryTokenApiIntegrationTest extends AbstractIntegrationTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;

    private long saveUser(String email) {
        return userRepository.save(new User(email, "irrelevant-hash", null, null)).getId();
    }

    private RequestPostProcessor tokenFor(long userId) {
        return jwt().jwt(token -> token.subject(String.valueOf(userId)));
    }

    @Test
    void everyGranaryRouteRefusesARequestWithNoToken() throws Exception {
        mockMvc.perform(get("/api/granary/summary")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/granary/pauses")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/granary/pauses/due")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/granary/mpesa-categories")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/granary/savings")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/granary/saving-target-amount").contentType(JSON).content("{\"amount\": 50}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/granary/pauses").contentType(JSON).content("{\"what\": \"x\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/granary/pauses/1/decide").contentType(JSON).content("{\"outcome\": \"DECLINED\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/granary/pauses/1/reflect").contentType(JSON).content("{\"reflection\": \"x\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/granary/mpesa-statement")
                        .file(new MockMultipartFile("file", "s.pdf", "application/pdf", new byte[] {1})))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenNamingAnUnknownUserIsABadRequestNotAServerError() throws Exception {
        mockMvc.perform(get("/api/granary/summary").with(tokenFor(999999))).andExpect(status().isBadRequest());
    }

    @Test
    void theTokenDecidesWhoseSavingTargetChangesNotTheUserIdInTheRequest() throws Exception {
        long a = saveUser("granary-token-a@example.com");
        long b = saveUser("granary-token-b@example.com");

        // A's token, but the body and the query both name B. Only A may change.
        mockMvc.perform(put("/api/granary/saving-target-amount").param("userId", String.valueOf(b))
                        .with(tokenFor(a)).contentType(JSON).content("{\"userId\": " + b + ", \"amount\": 123}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savingTargetAmount").value(123));

        mockMvc.perform(get("/api/granary/summary").with(tokenFor(b)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savingTargetAmount").doesNotExist());
        assertThat(userRepository.findById(b).orElseThrow().getSavingTargetAmount()).isNull();
    }

    @Test
    void pausesBelongToTheTokensUser() throws Exception {
        long a = saveUser("granary-token-c@example.com");
        long b = saveUser("granary-token-d@example.com");

        String created = mockMvc.perform(post("/api/granary/pauses").param("userId", String.valueOf(b))
                        .with(tokenFor(a)).contentType(JSON).content("{\"userId\": " + b + ", \"what\": \"token-A-pause\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long pauseId = Long.parseLong(created.replaceAll(".*\"id\":(\\d+).*", "$1"));

        assertThat(mockMvc.perform(get("/api/granary/pauses").with(tokenFor(a))).andReturn().getResponse().getContentAsString())
                .contains("token-A-pause");
        assertThat(mockMvc.perform(get("/api/granary/pauses").param("userId", String.valueOf(a)).with(tokenFor(b)))
                .andReturn().getResponse().getContentAsString()).doesNotContain("token-A-pause");

        // B's token, even when the request names A as the owner, cannot touch A's pause.
        mockMvc.perform(patch("/api/granary/pauses/" + pauseId + "/decide").param("userId", String.valueOf(a))
                        .with(tokenFor(b)).contentType(JSON).content("{\"outcome\": \"DECLINED\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/granary/pauses/" + pauseId + "/reflect")
                        .with(tokenFor(b)).contentType(JSON).content("{\"reflection\": \"not mine\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/granary/pauses/" + pauseId + "/decide")
                        .with(tokenFor(a)).contentType(JSON).content("{\"outcome\": \"DECLINED\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void storingASavingStoresItForTheTokensUserOnly() throws Exception {
        long a = saveUser("granary-token-e@example.com");
        long b = saveUser("granary-token-f@example.com");

        mockMvc.perform(put("/api/granary/saving-target-amount").with(tokenFor(a)).contentType(JSON)
                .content("{\"amount\": 50}")).andExpect(status().isOk());

        mockMvc.perform(post("/api/granary/savings").with(tokenFor(a)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalStoredEver").value(50));

        mockMvc.perform(get("/api/granary/summary").with(tokenFor(b)))
                .andExpect(jsonPath("$.totalStoredEver").value(0));
    }
}
