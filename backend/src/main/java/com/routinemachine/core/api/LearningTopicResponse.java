package com.routinemachine.core.api;

import com.routinemachine.core.domain.LearningTopic;

public record LearningTopicResponse(Long id, String name, Long goalId, String goalName) {

    public static LearningTopicResponse from(LearningTopic topic) {
        return new LearningTopicResponse(
                topic.getId(), topic.getName(), topic.getGoal().getId(), topic.getGoal().getName());
    }
}
