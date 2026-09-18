package com.routinemachine.core.repository;

import com.routinemachine.core.domain.Goal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GoalRepository extends JpaRepository<Goal, Long> {

    Optional<Goal> findByName(String name);
}
