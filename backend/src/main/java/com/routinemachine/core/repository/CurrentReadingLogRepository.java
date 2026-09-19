package com.routinemachine.core.repository;

import com.routinemachine.core.domain.CurrentReadingLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CurrentReadingLogRepository extends JpaRepository<CurrentReadingLog, Long> {

    Optional<CurrentReadingLog> findFirstByOrderByCreatedAtDescIdDesc();

    default Optional<CurrentReadingLog> findLatest() {
        return findFirstByOrderByCreatedAtDescIdDesc();
    }
}
