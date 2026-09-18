package com.routinemachine.core.api;

import com.routinemachine.core.domain.Goal;

public record GoalResponse(Long id, String name, String description) {

    public static GoalResponse from(Goal goal) {
        return new GoalResponse(goal.getId(), goal.getName(), goal.getDescription());
    }
}
