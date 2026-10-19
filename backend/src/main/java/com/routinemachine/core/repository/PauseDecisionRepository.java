package com.routinemachine.core.repository;

import com.routinemachine.core.domain.PauseDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PauseDecisionRepository extends JpaRepository<PauseDecision, Long> {

    List<PauseDecision> findByUserIdOrderByCreatedAtDesc(Long userId);
}
