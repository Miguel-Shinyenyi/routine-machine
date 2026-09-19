package com.routinemachine.core.repository;

import com.routinemachine.core.domain.ScheduleTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.DayOfWeek;
import java.util.List;

public interface ScheduleTemplateRepository extends JpaRepository<ScheduleTemplate, Long> {

    // Fetch-joins routineItem: see LearningTopicRepository for why (open-in-view is disabled,
    // and ScheduleTemplateResponse reads routineItem.getName() after the transaction closes).
    @Query("SELECT t FROM ScheduleTemplate t LEFT JOIN FETCH t.routineItem")
    List<ScheduleTemplate> findAllWithRoutineItem();

    @Query("SELECT t FROM ScheduleTemplate t LEFT JOIN FETCH t.routineItem WHERE t.dayOfWeek = :dayOfWeek")
    List<ScheduleTemplate> findByDayOfWeekWithRoutineItem(@Param("dayOfWeek") DayOfWeek dayOfWeek);
}
