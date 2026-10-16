package com.routinemachine.core.repository;

import com.routinemachine.core.domain.HubSyncProcessedFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HubSyncProcessedFileRepository extends JpaRepository<HubSyncProcessedFile, Long> {

    boolean existsByContentSha(String contentSha);
}
