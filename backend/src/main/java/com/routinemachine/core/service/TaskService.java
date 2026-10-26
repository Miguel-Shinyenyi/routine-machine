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
        return changeStatus(taskId, newStatus, null);
    }

    // userId is who is asking; the controller passes the signed-in person's id from the token. A null id means "nobody",
    // which can only touch unowned tasks. Someone else's task answers exactly like one that does not exist, so the id of a
    // task you do not own tells you nothing.
    @Transactional
    public Task changeStatus(Long taskId, TaskStatus newStatus, Long userId) {
        Task task = taskRepository.findById(taskId)
                .filter(found -> Ownership.ownedBy(found.getUser(), userId))
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
        DailyLog log = null;
        if (task.getRoutineItem() != null) {
            log = DailyLog.forRoutineItem(task.getTaskDate(), task.getRoutineItem(), DailyLogSource.SCHEDULE);
        } else if (task.getLearningTopic() != null) {
            log = DailyLog.forLearningTopic(task.getTaskDate(), task.getLearningTopic(), DailyLogSource.SCHEDULE);
        }
        if (log != null) {
            // The day is logged for whoever owns the task, never for nobody.
            log.assignUser(task.getUser());
        }
        return Optional.ofNullable(log);
    }
}
