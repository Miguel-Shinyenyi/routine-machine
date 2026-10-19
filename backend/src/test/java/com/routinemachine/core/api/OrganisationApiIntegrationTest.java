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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    @Test
    void bulkInvitingByCsvAddsAnExistingUserAndReservesASeatForAnUnknownEmail() throws Exception {
        Long admin = saveUser("admin3@example.com");
        Long learner = saveUser("learner3@example.com");

        MvcResult orgResult = mockMvc.perform(post("/api/organisations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Moringa School\",\"foundingAdminUserId\":" + admin + "}"))
                .andReturn();
        Long organisationId = Long.valueOf(
                orgResult.getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1"));
        MvcResult cohortResult = mockMvc.perform(post("/api/organisations/" + organisationId + "/cohorts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestingUserId\":" + admin
                                + ",\"name\":\"Cohort 14\",\"startsOn\":\"2026-10-19\",\"weeks\":8}"))
                .andReturn();
        Long cohortId = Long.valueOf(
                cohortResult.getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1"));

        mockMvc.perform(post("/api/organisations/cohorts/" + cohortId + "/bulk-invite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestingUserId\":" + admin
                                + ",\"csv\":\"learner3@example.com\\nnewcomer3@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email", is("learner3@example.com")))
                .andExpect(jsonPath("$[0].outcome", is("ADDED_IMMEDIATELY")))
                .andExpect(jsonPath("$[1].email", is("newcomer3@example.com")))
                .andExpect(jsonPath("$[1].outcome", is("INVITE_RESERVED")));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"newcomer3@example.com\",\"password\":\"a-strong-password\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/organisations/cohorts/" + cohortId + "/members")
                        .param("requestingUserId", String.valueOf(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].email", is("newcomer3@example.com")));
    }

    @Test
    void settingASeatLimitCapsFurtherBulkInvites() throws Exception {
        Long admin = saveUser("admin4@example.com");

        MvcResult orgResult = mockMvc.perform(post("/api/organisations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Moringa School\",\"foundingAdminUserId\":" + admin + "}"))
                .andReturn();
        Long organisationId = Long.valueOf(
                orgResult.getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1"));

        mockMvc.perform(put("/api/organisations/" + organisationId + "/seat-limit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestingUserId\":" + admin + ",\"seatLimit\":1}"))
                .andExpect(status().isNoContent());

        MvcResult cohortResult = mockMvc.perform(post("/api/organisations/" + organisationId + "/cohorts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestingUserId\":" + admin
                                + ",\"name\":\"Cohort 14\",\"startsOn\":\"2026-10-19\",\"weeks\":8}"))
                .andReturn();
        Long cohortId = Long.valueOf(
                cohortResult.getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1"));

        mockMvc.perform(post("/api/organisations/cohorts/" + cohortId + "/bulk-invite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestingUserId\":" + admin + ",\"csv\":\"a4@example.com\\nb4@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].outcome", is("INVITE_RESERVED")))
                .andExpect(jsonPath("$[1].outcome", is("SEAT_LIMIT_REACHED")));
    }

    @Test
    void theDashboardReflectsASurveyResponseAndIsReadableByAMember() throws Exception {
        Long admin = saveUser("admin5@example.com");
        Long learner = saveUser("learner5@example.com");

        MvcResult orgResult = mockMvc.perform(post("/api/organisations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Moringa School\",\"foundingAdminUserId\":" + admin + "}"))
                .andReturn();
        Long organisationId = Long.valueOf(
                orgResult.getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1"));
        MvcResult cohortResult = mockMvc.perform(post("/api/organisations/" + organisationId + "/cohorts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestingUserId\":" + admin
                                + ",\"name\":\"Cohort 14\",\"startsOn\":\"2026-10-19\",\"weeks\":8}"))
                .andReturn();
        Long cohortId = Long.valueOf(
                cohortResult.getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1"));
        mockMvc.perform(post("/api/organisations/cohorts/" + cohortId + "/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestingUserId\":" + admin + ",\"newMemberUserId\":" + learner + "}"));

        mockMvc.perform(post("/api/organisations/cohorts/" + cohortId + "/survey-response")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestingUserId\":" + learner + ",\"answer\":\"SUPPORTIVE\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/organisations/cohorts/" + cohortId + "/dashboard")
                        .param("requestingUserId", String.valueOf(learner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feltSupportive.count", is(1)))
                .andExpect(jsonPath("$.feltSupportive.denominator", is(1)));
    }

    @Test
    void someoneWhoIsNeitherAnAdminNorAMemberCannotReadTheDashboard() throws Exception {
        Long admin = saveUser("admin6@example.com");
        Long outsider = saveUser("outsider6@example.com");

        MvcResult orgResult = mockMvc.perform(post("/api/organisations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Moringa School\",\"foundingAdminUserId\":" + admin + "}"))
                .andReturn();
        Long organisationId = Long.valueOf(
                orgResult.getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1"));
        MvcResult cohortResult = mockMvc.perform(post("/api/organisations/" + organisationId + "/cohorts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestingUserId\":" + admin
                                + ",\"name\":\"Cohort 14\",\"startsOn\":\"2026-10-19\",\"weeks\":8}"))
                .andReturn();
        Long cohortId = Long.valueOf(
                cohortResult.getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1"));

        mockMvc.perform(get("/api/organisations/cohorts/" + cohortId + "/dashboard")
                        .param("requestingUserId", String.valueOf(outsider)))
                .andExpect(status().isForbidden());
    }

    @Test
    void theByGroupBreakdownHidesAGroupUnderFiveButShowsItsMemberCount() throws Exception {
        Long admin = saveUser("admin7@example.com");
        saveUser("weekend1@example.com");
        saveUser("weekend2@example.com");

        MvcResult orgResult = mockMvc.perform(post("/api/organisations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Moringa School\",\"foundingAdminUserId\":" + admin + "}"))
                .andReturn();
        Long organisationId = Long.valueOf(
                orgResult.getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1"));
        MvcResult cohortResult = mockMvc.perform(post("/api/organisations/" + organisationId + "/cohorts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestingUserId\":" + admin
                                + ",\"name\":\"Cohort 14\",\"startsOn\":\"2026-10-19\",\"weeks\":8}"))
                .andReturn();
        Long cohortId = Long.valueOf(
                cohortResult.getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1"));

        mockMvc.perform(post("/api/organisations/cohorts/" + cohortId + "/bulk-invite")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestingUserId\":" + admin
                        + ",\"csv\":\"weekend1@example.com,Weekend group\\nweekend2@example.com,Weekend group\"}"));

        mockMvc.perform(get("/api/organisations/cohorts/" + cohortId + "/dashboard/by-group")
                        .param("requestingUserId", String.valueOf(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studyGroup", is("Weekend group")))
                .andExpect(jsonPath("$[0].memberCount", is(2)))
                .andExpect(jsonPath("$[0].hidden", is(true)))
                .andExpect(jsonPath("$[0].checkedInThisWeek").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void theWeeklyCheckInChartHasOneBarPerWeekOfThePilot() throws Exception {
        Long admin = saveUser("admin8@example.com");

        MvcResult orgResult = mockMvc.perform(post("/api/organisations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Moringa School\",\"foundingAdminUserId\":" + admin + "}"))
                .andReturn();
        Long organisationId = Long.valueOf(
                orgResult.getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1"));
        MvcResult cohortResult = mockMvc.perform(post("/api/organisations/" + organisationId + "/cohorts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestingUserId\":" + admin
                                + ",\"name\":\"Cohort 14\",\"startsOn\":\"2026-10-19\",\"weeks\":8}"))
                .andReturn();
        Long cohortId = Long.valueOf(
                cohortResult.getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1"));

        mockMvc.perform(get("/api/organisations/cohorts/" + cohortId + "/dashboard/weekly-check-ins")
                        .param("requestingUserId", String.valueOf(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weeks.length()", is(8)))
                .andExpect(jsonPath("$.weeks[0].week", is(1)))
                .andExpect(jsonPath("$.weeks[0].isFuture", is(false)))
                .andExpect(jsonPath("$.weeks[7].isFuture", is(true)));
    }
}
