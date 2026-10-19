package com.routinemachine.core.repository;

import com.routinemachine.core.domain.DailyCredit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

public interface DailyCreditRepository extends JpaRepository<DailyCredit, Long> {

    // The whole concurrency-safety mechanism: ON CONFLICT DO NOTHING against the DB's own
    // UNIQUE(user_id, goal_id, local_date) means two simultaneous callers racing to award the
    // same day's credit for the same goal can both run this and only one row ever exists —
    // no application-level lock, no check-then-insert race. See EvidenceCreditService.
    @Modifying
    @Transactional
    @Query(
            value = "INSERT INTO daily_credits (user_id, goal_id, local_date, credits, evidence_event_id) "
                    + "VALUES (:userId, :goalId, :localDate, 1, :evidenceEventId) "
                    + "ON CONFLICT (user_id, goal_id, local_date) DO NOTHING",
            nativeQuery = true)
    int awardCreditIfAbsent(
            @Param("userId") Long userId, @Param("goalId") Long goalId,
            @Param("localDate") LocalDate localDate, @Param("evidenceEventId") Long evidenceEventId);

    List<DailyCredit> findByUserIdAndLocalDateBetween(Long userId, LocalDate start, LocalDate end);

    List<DailyCredit> findByUserIdAndGoalIdAndLocalDateBetween(Long userId, Long goalId, LocalDate start, LocalDate end);

    boolean existsByUserIdAndLocalDate(Long userId, LocalDate localDate);

    boolean existsByUserIdAndLocalDateBefore(Long userId, LocalDate localDate);
}
