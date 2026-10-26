package com.routinemachine.core.repository;

import com.routinemachine.core.domain.WeeklyFailureMode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface WeeklyFailureModeRepository extends JpaRepository<WeeklyFailureMode, Long> {

    Optional<WeeklyFailureMode> findByUserIdAndWeekStart(Long userId, LocalDate weekStart);
}
