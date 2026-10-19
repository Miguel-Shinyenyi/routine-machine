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

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrganisationApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;

    private Long saveUser(String email) {
        return userRepository.save(new User(email, "irrelevant-hash", null, null)).getId();
    }

    @Test
    void creatingAnOrganisationThenACohortThenAddingAMemberWorksEndToEnd() throws Exception {
        Long admin = saveUser("admin1@example.com");
        Long learner = saveUser("learner1@example.com");

        MvcResult orgResult = mockMvc.perform(post("/api/organisations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Moringa School\",\"foundingAdminUserId\":" + admin + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Moringa School")))
                .andReturn();
        Long organisationId = Long.valueOf(
                orgResult.getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1"));

        MvcResult cohortResult = mockMvc.perform(post("/api/organisations/" + organisationId + "/cohorts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestingUserId\":" + admin
                                + ",\"name\":\"Cohort 14\",\"startsOn\":\"2026-10-19\",\"weeks\":8}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Cohort 14")))
                .andExpect(jsonPath("$.weeks", is(8)))
                .andReturn();
        Long cohortId = Long.valueOf(
                cohortResult.getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1"));

        mockMvc.perform(post("/api/organisations/cohorts/" + cohortId + "/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestingUserId\":" + admin + ",\"newMemberUserId\":" + learner + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("learner1@example.com")));

        mockMvc.perform(get("/api/organisations/cohorts/" + cohortId + "/members")
                        .param("requestingUserId", String.valueOf(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email", is("learner1@example.com")));
    }

    @Test
    void someoneWhoIsNotAnAdminGetsForbiddenCreatingACohort() throws Exception {
        Long admin = saveUser("admin2@example.com");
        Long outsider = saveUser("outsider2@example.com");

        MvcResult orgResult = mockMvc.perform(post("/api/organisations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Moringa School\",\"foundingAdminUserId\":" + admin + "}"))
                .andReturn();
        Long organisationId = Long.valueOf(
                orgResult.getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1"));

        mockMvc.perform(post("/api/organisations/" + organisationId + "/cohorts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestingUserId\":" + outsider
                                + ",\"name\":\"Cohort 14\",\"startsOn\":\"2026-10-19\",\"weeks\":8}"))
                .andExpect(status().isForbidden());
    }
}
