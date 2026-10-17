package com.routinemachine.core.api;

import com.routinemachine.core.domain.Goal;

public record GoalResponse(Long id, String name, String description, Long userId) {

    public static GoalResponse from(Goal goal) {
        return new GoalResponse(
                goal.getId(),
                goal.getName(),
                goal.getDescription(),
                goal.getUser() == null ? null : goal.getUser().getId());
    }
}
