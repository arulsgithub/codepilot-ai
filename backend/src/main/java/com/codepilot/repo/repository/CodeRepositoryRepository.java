package com.codepilot.repo.repository;

import com.codepilot.repo.entity.CodeRepositoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CodeRepositoryRepository extends JpaRepository<CodeRepositoryEntity, UUID> {

    /** Scoped by user - the same repository can be registered by two people independently. */
    Optional<CodeRepositoryEntity> findByLocalPathAndUserId(String localPath, UUID userId);

    Optional<CodeRepositoryEntity> findByIdAndUserId(UUID id, UUID userId);

    List<CodeRepositoryEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
}