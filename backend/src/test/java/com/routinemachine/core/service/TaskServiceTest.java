package com.routinemachine.core.service;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.RoutineItem;
import com.routinemachine.core.domain.Task;
import com.routinemachine.core.domain.TaskStatus;
import com.routinemachine.core.repository.DailyLogRepository;
import com.routinemachine.core.repository.RoutineItemRepository;
import com.routinemachine.core.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TaskServiceTest extends AbstractIntegrationTest {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private DailyLogRepository dailyLogRepository;

    @Autowired
    private RoutineItemRepository routineItemRepository;

    private TaskService taskService;

    private TaskService service() {
        if (taskService == null) {
            taskService = new TaskService(taskRepository, dailyLogRepository);
        }
        return taskService;
    }

    @Test
    void movingATaskToDoneCreatesADailyLogWithScheduleSource() {
        RoutineItem item = routineItemRepository.save(new RoutineItem("exercise", null));
        Task task = taskRepository.save(Task.adHoc(LocalDate.of(2026, 9, 21), "exercise", item, null));

        Task updated = service().changeStatus(task.getId(), TaskStatus.DONE);

        assertThat(updated.getStatus()).isEqualTo(TaskStatus.DONE);
        assertThat(updated.getDailyLog()).isNotNull();
        assertThat(updated.getDailyLog().getSource().getValue()).isEqualTo("schedule");
        assertThat(dailyLogRepository.findAll()).hasSize(1);
    }

    @Test
    void movingADoneTaskBackToTodoDeletesItsDailyLog() {
        RoutineItem item = routineItemRepository.save(new RoutineItem("exercise", null));
        Task task = taskRepository.save(Task.adHoc(LocalDate.of(2026, 9, 21), "exercise", item, null));
        service().changeStatus(task.getId(), TaskStatus.DONE);

        Task reverted = service().changeStatus(task.getId(), TaskStatus.TODO);

        assertThat(reverted.getDailyLog()).isNull();
        assertThat(dailyLogRepository.findAll()).isEmpty();
    }

    @Test
    void movingATaskWithNoTargetToDoneCreatesNoDailyLog() {
        Task task = taskRepository.save(Task.adHoc(LocalDate.of(2026, 9, 21), "call the dentist", null, null));

        Task updated = service().changeStatus(task.getId(), TaskStatus.DONE);

        assertThat(updated.getStatus()).isEqualTo(TaskStatus.DONE);
        assertThat(updated.getDailyLog()).isNull();
        assertThat(dailyLogRepository.findAll()).isEmpty();
    }

    @Test
    void reDoneingAnAlreadyDoneTaskDoesNotCreateASecondDailyLog() {
        RoutineItem item = routineItemRepository.save(new RoutineItem("exercise", null));
        Task task = taskRepository.save(Task.adHoc(LocalDate.of(2026, 9, 21), "exercise", item, null));
        service().changeStatus(task.getId(), TaskStatus.DONE);

        service().changeStatus(task.getId(), TaskStatus.DONE);

        assertThat(dailyLogRepository.findAll()).hasSize(1);
    }

    @Test
    void throwsWhenTheTaskDoesNotExist() {
        assertThatThrownBy(() -> service().changeStatus(999999L, TaskStatus.DONE))
                .isInstanceOf(NoSuchElementException.class);
    }
}
