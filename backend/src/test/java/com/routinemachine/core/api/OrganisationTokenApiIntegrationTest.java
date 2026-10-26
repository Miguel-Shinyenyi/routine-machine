package com.routinemachine.core.api;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static com.routinemachine.core.TestTokens.asUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Access control, part 4: organisation and cohort routes act for the person in the signed token.
// No token is 401. The person's Phase 12 rights (founding admin, cohort member) are now checked
// against the token, not against a user id the caller names.
@SpringBootTest
@AutoConfigureMockMvc
class OrganisationTokenApiIntegrationTest extends AbstractIntegrationTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;

    private long saveUser(String email) {
        return userRepository.save(new User(email, "irrelevant-hash", null, null)).getId();
    }

    private long idOf(MvcResult result) throws Exception {
        return Long.parseLong(result.getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    private long createOrganisation(long asUser, String name) throws Exception {
        return idOf(mockMvc.perform(post("/api/organisations").with(asUser(asUser)).contentType(JSON)
                .content("{\"name\":\"" + name + "\"}")).andExpect(status().isOk()).andReturn());
    }

    private long createCohort(long asUser, long organisationId) throws Exception {
        return idOf(mockMvc.perform(post("/api/organisations/" + organisationId + "/cohorts").with(asUser(asUser))
                        .contentType(JSON).content("{\"name\":\"c\",\"startsOn\":\"2034-03-01\",\"weeks\":4}"))
                .andExpect(status().isOk()).andReturn());
    }

    @Test
    void everyRouteRefusesARequestWithNoToken() throws Exception {
        mockMvc.perform(post("/api/organisations").contentType(JSON).content("{\"name\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/organisations/1/cohorts").contentType(JSON)
                .content("{\"name\":\"x\",\"startsOn\":\"2034-03-01\",\"weeks\":4}")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/organisations/cohorts/1/members").contentType(JSON)
                .content("{\"newMemberUserId\":1}")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/organisations/cohorts/1/members")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/organisations/1/seat-limit").contentType(JSON).content("{\"seatLimit\":2}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/organisations/cohorts/1/bulk-invite").contentType(JSON).content("{\"csv\":\"a@b.c\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/organisations/cohorts/1/dashboard")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/organisations/cohorts/1/dashboard/by-group")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/organisations/cohorts/1/dashboard/weekly-check-ins")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/organisations/cohorts/1/survey-response").contentType(JSON)
                .content("{\"answer\":\"SUPPORTIVE\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void theFoundingAdminIsTheTokensUserWhateverTheRequestSays() throws Exception {
        long a = saveUser("org-token-a@example.com");
        long b = saveUser("org-token-b@example.com");

        // A's token, but the body names B as the founding admin. A is the admin; B is not.
        MvcResult created = mockMvc.perform(post("/api/organisations").with(asUser(a)).contentType(JSON)
                .content("{\"name\":\"org-token-1\",\"foundingAdminUserId\":" + b + "}")).andExpect(status().isOk()).andReturn();
        long organisationId = idOf(created);

        mockMvc.perform(post("/api/organisations/" + organisationId + "/cohorts").with(asUser(b)).contentType(JSON)
                        .content("{\"requestingUserId\":" + a + ",\"name\":\"c\",\"startsOn\":\"2034-03-01\",\"weeks\":4}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/organisations/" + organisationId + "/cohorts").with(asUser(a)).contentType(JSON)
                        .content("{\"name\":\"c\",\"startsOn\":\"2034-03-01\",\"weeks\":4}"))
                .andExpect(status().isOk());
    }

    @Test
    void aNonAdminCannotOpenTheDashboardEvenWhenNamingTheAdmin() throws Exception {
        long admin = saveUser("org-token-c@example.com");
        long outsider = saveUser("org-token-d@example.com");
        long cohort = createCohort(admin, createOrganisation(admin, "org-token-2"));

        mockMvc.perform(get("/api/organisations/cohorts/" + cohort + "/dashboard")
                        .param("requestingUserId", String.valueOf(admin)).with(asUser(outsider)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/organisations/cohorts/" + cohort + "/dashboard").with(asUser(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void aMemberSubmitsTheirOwnSurveyAnswerNotSomeoneElses() throws Exception {
        long admin = saveUser("org-token-e@example.com");
        long member = saveUser("org-token-f@example.com");
        long cohort = createCohort(admin, createOrganisation(admin, "org-token-3"));
        mockMvc.perform(post("/api/organisations/cohorts/" + cohort + "/members").with(asUser(admin)).contentType(JSON)
                .content("{\"newMemberUserId\":" + member + "}")).andExpect(status().isOk());

        // The member's token submits; a body naming someone else changes nothing about who answered.
        mockMvc.perform(post("/api/organisations/cohorts/" + cohort + "/survey-response").with(asUser(member))
                        .contentType(JSON).content("{\"requestingUserId\":" + admin + ",\"answer\":\"SUPPORTIVE\"}"))
                .andExpect(status().isNoContent());
        // A member may read the dashboard but not the member list (admin only).
        mockMvc.perform(get("/api/organisations/cohorts/" + cohort + "/dashboard").with(asUser(member)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/organisations/cohorts/" + cohort + "/members").with(asUser(member)))
                .andExpect(status().isForbidden());
    }
}
