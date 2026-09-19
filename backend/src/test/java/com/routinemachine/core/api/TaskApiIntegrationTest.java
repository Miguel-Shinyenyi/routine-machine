package com.routinemachine.core.api;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.Goal;
import com.routinemachine.core.domain.LearningTopic;
import com.routinemachine.core.domain.RoutineItem;
import com.routinemachine.core.domain.ScheduleTemplate;
import com.routinemachine.core.repository.DailyLogRepository;
import com.routinemachine.core.repository.GoalRepository;
import com.routinemachine.core.repository.LearningTopicRepository;
import com.routinemachine.core.repository.RoutineItemRepository;
import com.routinemachine.core.repository.ScheduleTemplateRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.DayOfWeek;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TaskApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoutineItemRepository routineItemRepository;

    @Autowired
    private ScheduleTemplateRepository scheduleTemplateRepository;

    @Autowired
    private LearningTopicRepository learningTopicRepository;

    @Autowired
    private GoalRepository goalRepository;

    @Autowired
    private DailyLogRepository dailyLogRepository;

    @Test
    void createsAnAdHocTaskWithNoTarget() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"taskDate": "2026-09-21", "label": "call the dentist"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.label", is("call the dentist")))
                .andExpect(jsonPath("$.status", is("TODO")))
                .andExpect(jsonPath("$.routineItemId").doesNotExist());
    }

    @Test
    void materializesATaskFromARoutineItemTemplate() throws Exception {
        RoutineItem item = routineItemRepository.save(new RoutineItem("weight lifting", null));
        ScheduleTemplate template = scheduleTemplateRepository.save(
                ScheduleTemplate.forRoutineItem(DayOfWeek.MONDAY, item, 60, 1, "Weight lifting"));

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"taskDate": "2026-09-21", "scheduleTemplateId": %d}
                                """.formatted(template.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.label", is("Weight lifting")))
                .andExpect(jsonPath("$.routineItemId", is(item.getId().intValue())));
    }

    @Test
    void materializingALearningSlotTaskRequiresAResolvedTopic() throws Exception {
        ScheduleTemplate template = scheduleTemplateRepository.save(
                ScheduleTemplate.forLearningSlot(DayOfWeek.TUESDAY, 45, 1, "Goal-suggested topic"));

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"taskDate": "2026-09-22", "scheduleTemplateId": %d}
                                """.formatted(template.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void materializesALearningSlotTaskWithASuppliedTopic() throws Exception {
        Goal goal = goalRepository.findByName("target-roles").orElseThrow();
        LearningTopic topic = learningTopicRepository.save(new LearningTopic("PostHog analytics", goal));
        ScheduleTemplate template = scheduleTemplateRepository.save(
                ScheduleTemplate.forLearningSlot(DayOfWeek.TUESDAY, 45, 1, "Goal-suggested topic"));

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"taskDate": "2026-09-22", "scheduleTemplateId": %d, "learningTopicId": %d}
                                """.formatted(template.getId(), topic.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.learningTopicId", is(topic.getId().intValue())));
    }

    @Test
    void movingATaskToDoneCreatesADailyLog() throws Exception {
        RoutineItem item = routineItemRepository.save(new RoutineItem("journaling", null));
        String body = mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"taskDate": "2026-09-21", "label": "journaling", "routineItemId": %d}
                                """.formatted(item.getId())))
                .andReturn().getResponse().getContentAsString();
        Long taskId = ((Number) JsonPath.read(body, "$.id")).longValue();

        mockMvc.perform(patch("/api/tasks/" + taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "DONE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("DONE")))
                .andExpect(jsonPath("$.dailyLogId").exists());

        assertThatDailyLogWasCreatedWithScheduleSource();
    }

    private void assertThatDailyLogWasCreatedWithScheduleSource() {
        assertThat(dailyLogRepository.findAll())
                .extracting(l -> l.getSource().getValue())
                .contains("schedule");
    }

    @Test
    void returns404ForAStatusUpdateOnANonexistentTask() throws Exception {
        mockMvc.perform(patch("/api/tasks/999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "DONE"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void listsTasksFilteredByDate() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"taskDate": "2026-09-25", "label": "one-off"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/tasks").param("date", "2026-09-25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].label", hasItem("one-off")));
    }
}
