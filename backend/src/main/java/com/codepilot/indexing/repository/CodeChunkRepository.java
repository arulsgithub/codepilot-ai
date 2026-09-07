package com.codepilot.indexing.repository;

import com.codepilot.indexing.entity.CodeChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface CodeChunkRepository extends JpaRepository<CodeChunkEntity, UUID> {

    // Re-indexing the same repo should replace its old chunks, not duplicate them.
    @Modifying
    @Query("delete from CodeChunkEntity c where c.repositoryRoot = :repositoryRoot")
    void deleteByRepositoryRoot(@Param("repositoryRoot") String repositoryRoot);
}