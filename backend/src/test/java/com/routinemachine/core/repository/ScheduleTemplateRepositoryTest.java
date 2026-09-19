package com.routinemachine.core.repository;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.RoutineItem;
import com.routinemachine.core.domain.ScheduleTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.DayOfWeek;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ScheduleTemplateRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private ScheduleTemplateRepository scheduleTemplateRepository;

    @Autowired
    private RoutineItemRepository routineItemRepository;

    @Test
    void findsTemplatesByDayOfWeekWithRoutineItemLoaded() {
        RoutineItem item = routineItemRepository.save(new RoutineItem("weight lifting", null));
        scheduleTemplateRepository.save(
                ScheduleTemplate.forRoutineItem(DayOfWeek.MONDAY, item, 60, 1, "Weight lifting"));
        scheduleTemplateRepository.save(
                ScheduleTemplate.forLearningSlot(DayOfWeek.TUESDAY, 45, 1, "Goal-suggested topic"));

        List<ScheduleTemplate> mondayTemplates = scheduleTemplateRepository.findByDayOfWeekWithRoutineItem(DayOfWeek.MONDAY);

        assertThat(mondayTemplates).hasSize(1);
        assertThat(mondayTemplates.get(0).getRoutineItem().getName()).isEqualTo("weight lifting");
    }
}
