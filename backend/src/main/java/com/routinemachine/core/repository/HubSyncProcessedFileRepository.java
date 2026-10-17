package com.routinemachine.core.repository;

import com.routinemachine.core.domain.HubSyncProcessedFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HubSyncProcessedFileRepository extends JpaRepository<HubSyncProcessedFile, Long> {

    boolean existsByContentSha(String contentSha);

    Optional<HubSyncProcessedFile> findTopByOrderByCreatedAtDesc();
}
