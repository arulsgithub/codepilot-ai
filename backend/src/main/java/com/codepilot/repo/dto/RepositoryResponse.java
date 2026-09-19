package com.codepilot.repo.dto;

import com.codepilot.repo.entity.CodeRepositoryEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RepositoryResponse(
        UUID id,
        String name,
        SourceType sourceType,
        String remoteUrl,
        String branch,
        String localPath,
        String lastSyncedCommit,
        OffsetDateTime lastSyncedAt,
        OffsetDateTime lastIndexedAt,
        boolean indexStale
) {
    public static RepositoryResponse from(CodeRepositoryEntity e) {
        // "Stale" = we have pulled new code since the last time we indexed it.
        boolean stale = e.getLastIndexedAt() == null
                || (e.getLastSyncedAt() != null && e.getLastSyncedAt().isAfter(e.getLastIndexedAt()));

        return new RepositoryResponse(
                e.getId(), e.getName(), e.getSourceType(), e.getRemoteUrl(), e.getBranch(),
                e.getLocalPath(), e.getLastSyncedCommit(), e.getLastSyncedAt(),
                e.getLastIndexedAt(), stale);
    }
}