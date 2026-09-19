package com.codepilot.repo.repository;

import com.codepilot.repo.entity.CodeRepositoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CodeRepositoryRepository extends JpaRepository<CodeRepositoryEntity, UUID> {

    /** Used to make registration idempotent: registering the same folder twice returns the existing row. */
    Optional<CodeRepositoryEntity> findByLocalPath(String localPath);

    Optional<CodeRepositoryEntity> findByRemoteUrlAndBranch(String remoteUrl, String branch);

    List<CodeRepositoryEntity> findAllByOrderByCreatedAtDesc();
}