package com.routinemachine.core.repository;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.RoutineItem;
import com.routinemachine.core.domain.Task;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TaskRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private RoutineItemRepository routineItemRepository;

    @Test
    void findsTasksByDateWithRoutineItemLoaded() {
        RoutineItem item = routineItemRepository.save(new RoutineItem("exercise", null));
        LocalDate today = LocalDate.of(2026, 9, 21);
        taskRepository.save(Task.adHoc(today, "exercise", item, null));
        taskRepository.save(Task.adHoc(today.plusDays(1), "other day", null, null));

        List<Task> found = taskRepository.findByTaskDateWithTargets(today);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).getRoutineItem().getName()).isEqualTo("exercise");
    }
}
