package com.routinemachine.core.repository;

import com.routinemachine.core.domain.CurrentReadingLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CurrentReadingLogRepository extends JpaRepository<CurrentReadingLog, Long> {

    Optional<CurrentReadingLog> findFirstByOrderByCreatedAtDescIdDesc();

    // Per-user scoping (docs/backend.md): the latest entry of one user, or of nobody.
    Optional<CurrentReadingLog> findFirstByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    Optional<CurrentReadingLog> findFirstByUserIsNullOrderByCreatedAtDescIdDesc();

    default Optional<CurrentReadingLog> findLatest() {
        return findFirstByOrderByCreatedAtDescIdDesc();
    }
}
