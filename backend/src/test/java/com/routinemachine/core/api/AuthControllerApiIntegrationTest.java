package com.routinemachine.core.api;

import com.routinemachine.core.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "auth.internal-token=the-real-internal-token")
class AuthControllerApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registersAUserThenLogsInWithTheSamePassword() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "reader@example.com", "password": "a-strong-password"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email", is("reader@example.com")));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "reader@example.com", "password": "a-strong-password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("reader@example.com")));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "reader@example.com", "password": "the-wrong-password"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsRegisteringTheSameEmailTwice() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "duplicate@example.com", "password": "a-strong-password"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "duplicate@example.com", "password": "another-strong-password"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAShortPassword() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "shortpass@example.com", "password": "short"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void githubLoginWithTheCorrectInternalTokenCreatesAnAccount() throws Exception {
        mockMvc.perform(post("/api/auth/github-login")
                        .header("Authorization", "Bearer the-real-internal-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"githubId": "gh-42", "email": "ghuser@example.com", "displayName": "GH User"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("ghuser@example.com")));
    }

    @Test
    void githubLoginWithAWrongInternalTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/github-login")
                        .header("Authorization", "Bearer not-the-right-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"githubId": "gh-99", "email": "someone@example.com"}
                                """))
                .andExpect(status().isUnauthorized());
    }
}
