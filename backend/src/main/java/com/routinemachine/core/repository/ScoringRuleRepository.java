package com.routinemachine.core.repository;

import com.routinemachine.core.domain.ScoringRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ScoringRuleRepository extends JpaRepository<ScoringRule, Long> {

    // The newest published rule for this goal, whatever version it is — EvidenceCreditService
    // falls back to a presence-based default when this is empty, so a goal with no rule row
    // yet is never silently un-creditable. See docs/backend.md's decisions log.
    Optional<ScoringRule> findFirstByGoalIdOrderByVersionDesc(Long goalId);
}
