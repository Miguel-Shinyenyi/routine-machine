package com.routinemachine.core.repository;

import com.routinemachine.core.domain.RoutineItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutineItemRepository extends JpaRepository<RoutineItem, Long> {
}
