package com.routinemachine.core.repository;

import com.routinemachine.core.domain.RoutineItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface RoutineItemRepository extends JpaRepository<RoutineItem, Long> {

    // LEFT JOIN FETCH: goal is optional on a routine item (unlike LearningTopic's required
    // goal), so a plain JOIN FETCH would silently drop items with no goal_id.
    @Query("SELECT r FROM RoutineItem r LEFT JOIN FETCH r.goal")
    List<RoutineItem> findAllWithGoal();

    @Query("SELECT r FROM RoutineItem r LEFT JOIN FETCH r.goal WHERE r.id = :id")
    Optional<RoutineItem> findWithGoalById(Long id);

    Optional<RoutineItem> findFirstByNameIgnoreCase(String name);
}
