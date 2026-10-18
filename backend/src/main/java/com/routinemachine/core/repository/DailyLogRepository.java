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

    // Hillside groundwork (Phase 8): every non-private, user-owned log in a window from a user
    // who has opted into the hillside, with its routine item fetched eagerly since
    // HillsideService needs each log's hillsideField. Learning-topic logs are excluded (no
    // user_id path to a "field" without a routine item).
    @org.springframework.data.jpa.repository.Query(
            "SELECT d FROM DailyLog d JOIN FETCH d.routineItem r JOIN d.user u "
                    + "WHERE u.hillsideOptIn = true AND r.privateItem = false "
                    + "AND d.logDate BETWEEN :start AND :end")
    List<DailyLog> findForHillside(LocalDate start, LocalDate end);
}
