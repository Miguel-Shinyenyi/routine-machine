package com.routinemachine.core.repository;

import com.routinemachine.core.domain.DailyLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DailyLogRepository extends JpaRepository<DailyLog, Long> {

    List<DailyLog> findByLogDate(LocalDate logDate);

    boolean existsByRoutineItemIdAndLogDate(Long routineItemId, LocalDate logDate);

    List<DailyLog> findByUserId(Long userId);

    List<DailyLog> findByUserIdAndLogDate(Long userId, LocalDate logDate);
}
