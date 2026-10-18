package com.routinemachine.core.repository;

import com.routinemachine.core.domain.Goal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GoalRepository extends JpaRepository<Goal, Long> {

    // Name is no longer globally unique (Phase 8: unique per user instead), so this could
    // now match more than one row across different users; findFirst avoids throwing on that
    // rather than asserting uniqueness that no longer holds.
    Optional<Goal> findFirstByName(String name);

    boolean existsByUserIdAndName(Long userId, String name);

    List<Goal> findByUserId(Long userId);
}
