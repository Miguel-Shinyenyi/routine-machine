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

    @Query("SELECT t FROM LearningTopic t JOIN FETCH t.goal WHERE t.goal.id = :goalId")
    List<LearningTopic> findByGoalIdWithGoal(@Param("goalId") Long goalId);
}
