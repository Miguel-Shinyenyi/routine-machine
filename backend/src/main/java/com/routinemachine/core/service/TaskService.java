package com.routinemachine.core.service;

import com.routinemachine.core.domain.DailyLog;
import com.routinemachine.core.domain.DailyLogSource;
import com.routinemachine.core.domain.Task;
import com.routinemachine.core.domain.TaskStatus;
import com.routinemachine.core.repository.DailyLogRepository;
import com.routinemachine.core.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final DailyLogRepository dailyLogRepository;

    public TaskService(TaskRepository taskRepository, DailyLogRepository dailyLogRepository) {
        this.taskRepository = taskRepository;
        this.dailyLogRepository = dailyLogRepository;
    }

    @Transactional
    public Task changeStatus(Long taskId, TaskStatus newStatus) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NoSuchElementException("No task with id " + taskId));

        if (newStatus == TaskStatus.DONE) {
            if (task.getDailyLog() == null) {
                createDailyLogFor(task).ifPresent(log -> task.attachDailyLog(dailyLogRepository.save(log)));
            }
        } else if (task.getDailyLog() != null) {
            // The task's own row must stop pointing at the daily_log before that row can be
            // deleted, since daily_log_id is a foreign key on tasks.
            DailyLog existingLog = task.getDailyLog();
            task.clearDailyLog();
            taskRepository.saveAndFlush(task);
            dailyLogRepository.delete(existingLog);
        }

        task.updateStatus(newStatus);
        return taskRepository.save(task);
    }

    private Optional<DailyLog> createDailyLogFor(Task task) {
        if (task.getRoutineItem() != null) {
            return Optional.of(DailyLog.forRoutineItem(task.getTaskDate(), task.getRoutineItem(), DailyLogSource.SCHEDULE));
        }
        if (task.getLearningTopic() != null) {
            return Optional.of(DailyLog.forLearningTopic(task.getTaskDate(), task.getLearningTopic(), DailyLogSource.SCHEDULE));
        }
        return Optional.empty();
    }
}
