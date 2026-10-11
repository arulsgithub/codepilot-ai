package com.codepilot.indexing.dto;

import com.codepilot.indexing.entity.IndexJobEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record IndexJobResponse(
        UUID jobId,
        UUID repositoryId,
        IndexJobStatus status,
        String progress,
        Integer chunkCount,
        String errorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime finishedAt,
        boolean done
) {
    public static IndexJobResponse from(IndexJobEntity job) {
        return new IndexJobResponse(
                job.getId(), job.getRepositoryId(), job.getStatus(), job.getProgress(),
                job.getChunkCount(), job.getErrorMessage(),
                job.getCreatedAt(), job.getFinishedAt(),
                job.getStatus().isTerminal());
    }
}