package com.codepilot.indexing.repository;

import com.codepilot.indexing.dto.IndexJobStatus;
import com.codepilot.indexing.entity.IndexJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IndexJobRepository extends JpaRepository<IndexJobEntity, UUID> {

    /** Scoped by user, like every other finder — the id comes from the client. */
    Optional<IndexJobEntity> findByIdAndUserId(UUID id, UUID userId);

    Optional<IndexJobEntity> findFirstByRepositoryIdOrderByCreatedAtDesc(UUID repositoryId);

    /** Used to reject a second job for a repository that is already indexing. */
    boolean existsByRepositoryIdAndStatusIn(UUID repositoryId, List<IndexJobStatus> statuses);
}