package com.routinemachine.core.repository;

import com.routinemachine.core.domain.DailyLogSource;
import com.routinemachine.core.domain.EvidenceEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EvidenceEventRepository extends JpaRepository<EvidenceEvent, Long> {

    // The dedupe lookup (pipeline step 4): is this exact event already recorded for this
    // user? The DB's own UNIQUE(user_id, source, source_event_id) is the real guarantee under
    // concurrency; this is the fast-path check that avoids a doomed insert attempt first.
    Optional<EvidenceEvent> findByUserIdAndSourceAndSourceEventId(Long userId, DailyLogSource source, String sourceEventId);

    // The ledger read path (GET /api/evidence-events): goal fetched eagerly since open-in-view
    // is off and EvidenceEventResponse.from needs its name.
    @Query("SELECT e FROM EvidenceEvent e JOIN FETCH e.goal WHERE e.user.id = :userId "
            + "AND e.localDate BETWEEN :start AND :end ORDER BY e.occurredAt DESC")
    List<EvidenceEvent> findLedgerForUser(Long userId, LocalDate start, LocalDate end);

    // "Question a credit" (PATCH .../question) needs the goal's name for its response; goal
    // fetched eagerly for the same open-in-view reason as findLedgerForUser.
    @Query("SELECT e FROM EvidenceEvent e JOIN FETCH e.goal WHERE e.id = :id")
    Optional<EvidenceEvent> findWithGoalById(Long id);
}
