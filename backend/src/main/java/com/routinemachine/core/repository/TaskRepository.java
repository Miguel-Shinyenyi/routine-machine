package com.routinemachine.core.repository;

import com.routinemachine.core.domain.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    // Fetch-joins routineItem/learningTopic: same open-in-view reasoning as
    // ScheduleTemplateRepository and LearningTopicRepository.
    @Query("""
            SELECT t FROM Task t
            LEFT JOIN FETCH t.routineItem
            LEFT JOIN FETCH t.learningTopic
            WHERE t.taskDate = :taskDate
            """)
    List<Task> findByTaskDateWithTargets(@Param("taskDate") LocalDate taskDate);

    @Query("""
            SELECT t FROM Task t
            LEFT JOIN FETCH t.routineItem
            LEFT JOIN FETCH t.learningTopic
            """)
    List<Task> findAllWithTargets();
}
