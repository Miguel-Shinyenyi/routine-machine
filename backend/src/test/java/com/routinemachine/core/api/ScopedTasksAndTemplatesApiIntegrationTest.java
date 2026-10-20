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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Per-user scoping, part 2a: tasks and schedule templates answer for exactly one user.
// userId given = that user's rows; userId omitted = only unowned (pre-account) rows.
// Each test uses names/labels no other test in this shared-database class uses.
@SpringBootTest
@AutoConfigureMockMvc
class ScopedTasksAndTemplatesApiIntegrationTest extends AbstractIntegrationTest {

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

    private long createRoutineItem(String name, Long userId) throws Exception {
        String user = userId == null ? "" : ", \"userId\": " + userId;
        return idOf(mockMvc.perform(post("/api/routine-items").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"" + name + "\"" + user + "}")).andExpect(status().isCreated()).andReturn());
    }

    private long createTemplate(String label, Long userId) throws Exception {
        String user = userId == null ? "" : ", \"userId\": " + userId;
        return idOf(mockMvc.perform(post("/api/schedule-templates").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dayOfWeek\": \"MONDAY\", \"targetType\": \"LEARNING_SLOT\", "
                                + "\"targetDurationMinutes\": 30, \"sortOrder\": 1, \"label\": \"" + label + "\"" + user + "}"))
                .andExpect(status().isCreated()).andReturn());
    }

    private long createAdHocTask(String label, String date, Long userId, Long routineItemId) throws Exception {
        String user = userId == null ? "" : ", \"userId\": " + userId;
        String item = routineItemId == null ? "" : ", \"routineItemId\": " + routineItemId;
        return idOf(mockMvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskDate\": \"" + date + "\", \"label\": \"" + label + "\"" + item + user + "}"))
                .andExpect(status().isCreated()).andReturn());
    }

    @Test
    void scheduleTemplatesAreListedOnlyForTheirOwner() throws Exception {
        long a = saveUser("tmpl-a@example.com");
        long b = saveUser("tmpl-b@example.com");
        createTemplate("tmpl-A-only", a);
        createTemplate("tmpl-B-only", b);

        mockMvc.perform(get("/api/schedule-templates").param("userId", String.valueOf(a)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].label", is("tmpl-A-only")))
                .andExpect(jsonPath("$[0].userId", is((int) a)));
        mockMvc.perform(get("/api/schedule-templates").param("userId", String.valueOf(b)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].label", is("tmpl-B-only")));
    }

    @Test
    void aNewUserHasNoScheduleTemplates() throws Exception {
        long newcomer = saveUser("tmpl-new@example.com");
        createTemplate("tmpl-someone-elses", saveUser("tmpl-other@example.com"));

        mockMvc.perform(get("/api/schedule-templates").param("userId", String.valueOf(newcomer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void omittingUserIdShowsOnlyUnownedTemplatesNeverAUsersOwn() throws Exception {
        long a = saveUser("tmpl-c@example.com");
        createTemplate("tmpl-owned-by-C", a);
        createTemplate("tmpl-unowned", null);

        String body = mockMvc.perform(get("/api/schedule-templates")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body).contains("tmpl-unowned").doesNotContain("tmpl-owned-by-C");
    }

    @Test
    void aTemplateCannotPointAtSomeoneElsesRoutineItem() throws Exception {
        long a = saveUser("tmpl-d@example.com");
        long b = saveUser("tmpl-e@example.com");
        long bsItem = createRoutineItem("tmpl-Bs-routine", b);

        mockMvc.perform(post("/api/schedule-templates").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dayOfWeek\": \"MONDAY\", \"targetType\": \"ROUTINE_ITEM\", \"routineItemId\": " + bsItem
                                + ", \"targetDurationMinutes\": 10, \"sortOrder\": 1, \"label\": \"steal\", \"userId\": " + a + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tasksAreListedOnlyForTheirOwner() throws Exception {
        long a = saveUser("task-a@example.com");
        long b = saveUser("task-b@example.com");
        createAdHocTask("task-A-only", "2031-05-05", a, null);
        createAdHocTask("task-B-only", "2031-05-05", b, null);

        mockMvc.perform(get("/api/tasks").param("userId", String.valueOf(a)).param("date", "2031-05-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].label", is("task-A-only")))
                .andExpect(jsonPath("$[0].userId", is((int) a)));
        mockMvc.perform(get("/api/tasks").param("userId", String.valueOf(b)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].label", is("task-B-only")));
    }

    @Test
    void omittingUserIdShowsOnlyUnownedTasks() throws Exception {
        long a = saveUser("task-c@example.com");
        createAdHocTask("task-owned-by-C", "2031-06-06", a, null);
        createAdHocTask("task-unowned", "2031-06-06", null, null);

        String body = mockMvc.perform(get("/api/tasks").param("date", "2031-06-06")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body).contains("task-unowned").doesNotContain("task-owned-by-C");
    }

    @Test
    void anAdHocTaskCannotLinkSomeoneElsesRoutineItem() throws Exception {
        long a = saveUser("task-d@example.com");
        long b = saveUser("task-e@example.com");
        long bsItem = createRoutineItem("task-Bs-routine", b);

        mockMvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskDate\": \"2031-07-07\", \"label\": \"x\", \"routineItemId\": " + bsItem
                                + ", \"userId\": " + a + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aTaskCannotBeMaterializedFromSomeoneElsesTemplate() throws Exception {
        long a = saveUser("task-f@example.com");
        long b = saveUser("task-g@example.com");
        long bsTemplate = createTemplate("task-Bs-template", b);

        mockMvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskDate\": \"2031-08-08\", \"scheduleTemplateId\": " + bsTemplate
                                + ", \"userId\": " + a + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changingSomeoneElsesTaskLooksLikeItDoesNotExist() throws Exception {
        long a = saveUser("task-h@example.com");
        long b = saveUser("task-i@example.com");
        long asTask = createAdHocTask("task-As-own", "2031-09-09", a, null);

        mockMvc.perform(patch("/api/tasks/" + asTask).param("userId", String.valueOf(b))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"DONE\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/tasks/" + asTask).contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"DONE\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/tasks/" + asTask).param("userId", String.valueOf(a))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("IN_PROGRESS")));
    }

    @Test
    void finishingATaskLogsTheDayForThatTasksUserNotForNobody() throws Exception {
        long a = saveUser("task-j@example.com");
        long b = saveUser("task-k@example.com");
        long item = createRoutineItem("task-J-routine", a);
        long task = createAdHocTask("task-J-done", "2031-10-10", a, item);

        mockMvc.perform(patch("/api/tasks/" + task).param("userId", String.valueOf(a))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"DONE\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/daily-logs").param("userId", String.valueOf(a)).param("date", "2031-10-10"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].userId", is((int) a)));
        mockMvc.perform(get("/api/daily-logs").param("userId", String.valueOf(b)).param("date", "2031-10-10"))
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
