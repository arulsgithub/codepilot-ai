package com.codepilot.indexing.entity;

import com.codepilot.indexing.dto.IndexJobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "index_jobs")
public class IndexJobEntity {

    @Id
    private UUID id;

    @Column(name = "repository_id", nullable = false)
    private UUID repositoryId;

    /** Carried explicitly: the background thread has no SecurityContext to read it from. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private IndexJobStatus status;

    /** Human-readable phase, e.g. "Embedding batch 3/8". */
    @Column(name = "progress")
    private String progress;

    @Column(name = "chunk_count")
    private Integer chunkCount;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    protected IndexJobEntity() {
    }

    public IndexJobEntity(UUID id, UUID repositoryId, UUID userId) {
        this.id = id;
        this.repositoryId = repositoryId;
        this.userId = userId;
        this.status = IndexJobStatus.QUEUED;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getRepositoryId() { return repositoryId; }
    public UUID getUserId() { return userId; }
    public IndexJobStatus getStatus() { return status; }
    public void setStatus(IndexJobStatus status) { this.status = status; }
    public String getProgress() { return progress; }
    public void setProgress(String progress) { this.progress = progress; }
    public Integer getChunkCount() { return chunkCount; }
    public void setChunkCount(Integer chunkCount) { this.chunkCount = chunkCount; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }
    public OffsetDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(OffsetDateTime finishedAt) { this.finishedAt = finishedAt; }
}