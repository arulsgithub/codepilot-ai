package com.codepilot.repo.entity;

import com.codepilot.repo.dto.SourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "repositories")
public class CodeRepositoryEntity {

    @Id
    private UUID id;

    @Column(name = "name", nullable = false, length = 512)
    private String name;

    /**
     * EnumType.STRING, never ORDINAL. ORDINAL stores 0/1, so reordering the enum
     * silently rewrites the meaning of every existing row. STRING stores "GITHUB".
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 16)
    private SourceType sourceType;

    @Column(name = "remote_url", length = 1024)
    private String remoteUrl;

    @Column(name = "branch", length = 255)
    private String branch;

    @Column(name = "local_path", nullable = false, length = 1024)
    private String localPath;

    @Column(name = "last_synced_commit", length = 64)
    private String lastSyncedCommit;

    @Column(name = "last_synced_at")
    private OffsetDateTime lastSyncedAt;

    /**
     * Set by the indexing flow, not by git. Comparing this with lastSyncedAt tells us
     * whether the index is stale relative to the working copy - which is exactly the
     * check the UI will want ("3 new commits since last index").
     */
    @Column(name = "last_indexed_at")
    private OffsetDateTime lastIndexedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected CodeRepositoryEntity() {
        // JPA requires a no-arg constructor.
    }

    public CodeRepositoryEntity(UUID id, String name, SourceType sourceType, String remoteUrl,
                                String branch, String localPath, OffsetDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.sourceType = sourceType;
        this.remoteUrl = remoteUrl;
        this.branch = branch;
        this.localPath = localPath;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public String getRemoteUrl() {
        return remoteUrl;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getLocalPath() {
        return localPath;
    }

    public String getLastSyncedCommit() {
        return lastSyncedCommit;
    }

    public void setLastSyncedCommit(String lastSyncedCommit) {
        this.lastSyncedCommit = lastSyncedCommit;
    }

    public OffsetDateTime getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(OffsetDateTime lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }

    public OffsetDateTime getLastIndexedAt() {
        return lastIndexedAt;
    }

    public void setLastIndexedAt(OffsetDateTime lastIndexedAt) {
        this.lastIndexedAt = lastIndexedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}