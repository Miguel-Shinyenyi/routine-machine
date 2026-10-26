package com.routinemachine.core.repository;

import com.routinemachine.core.domain.LearningTopic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LearningTopicRepository extends JpaRepository<LearningTopic, Long> {

    // Fetch-joins goal so callers can read topic.getGoal().getName() outside a transaction
    // (e.g. when mapping to a response DTO in a controller with open-in-view disabled)
    // without hitting Hibernate's LazyInitializationException on the goal proxy.
    @Query("SELECT t FROM LearningTopic t JOIN FETCH t.goal")
    List<LearningTopic> findAllWithGoal();

    // Also loads the goal's owner, so a caller can check who a topic belongs to (a topic is
    // owned through its goal) outside a transaction.
    @Query("SELECT t FROM LearningTopic t JOIN FETCH t.goal g LEFT JOIN FETCH g.user WHERE t.id = :id")
    java.util.Optional<LearningTopic> findWithGoalAndOwnerById(@Param("id") Long id);

    // Per-user scoping (docs/backend.md): a topic belongs to whoever owns its goal.
    @Query("SELECT t FROM LearningTopic t JOIN FETCH t.goal g WHERE g.user.id = :userId")
    List<LearningTopic> findByOwnerWithGoal(@Param("userId") Long userId);

    @Query("SELECT t FROM LearningTopic t JOIN FETCH t.goal g WHERE g.id = :goalId AND g.user.id = :userId")
    List<LearningTopic> findByGoalIdAndOwnerWithGoal(@Param("goalId") Long goalId, @Param("userId") Long userId);

    @Query("SELECT t FROM LearningTopic t JOIN FETCH t.goal WHERE t.goal.id = :goalId")
    List<LearningTopic> findByGoalIdWithGoal(@Param("goalId") Long goalId);
}
