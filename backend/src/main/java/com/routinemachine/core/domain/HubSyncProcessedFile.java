package com.routinemachine.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;

// One row per UMWAYI file hub-sync has already turned into a daily_log. Keyed by the file's
// git blob sha, not its path, so a rename (same content, new path) is recognised as already
// counted instead of being logged a second time.
@Entity
@Table(name = "hub_sync_processed_files")
public class HubSyncProcessedFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "repo_path", nullable = false)
    private String repoPath;

    @Column(name = "content_sha", nullable = false, unique = true)
    private String contentSha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HubSyncFileKind kind;

    @Column(name = "file_date", nullable = false)
    private LocalDate fileDate;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    protected HubSyncProcessedFile() {
    }

    public HubSyncProcessedFile(String repoPath, String contentSha, HubSyncFileKind kind, LocalDate fileDate) {
        this.repoPath = Objects.requireNonNull(repoPath, "repoPath must not be null");
        this.contentSha = Objects.requireNonNull(contentSha, "contentSha must not be null");
        this.kind = Objects.requireNonNull(kind, "kind must not be null");
        this.fileDate = Objects.requireNonNull(fileDate, "fileDate must not be null");
    }

    public Long getId() {
        return id;
    }

    public String getRepoPath() {
        return repoPath;
    }

    public String getContentSha() {
        return contentSha;
    }

    public HubSyncFileKind getKind() {
        return kind;
    }

    public LocalDate getFileDate() {
        return fileDate;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
