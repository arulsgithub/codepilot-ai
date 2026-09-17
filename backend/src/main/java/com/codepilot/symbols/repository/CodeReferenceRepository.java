package com.codepilot.symbols.repository;

import com.codepilot.symbols.entity.CodeReferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CodeReferenceRepository extends JpaRepository<CodeReferenceEntity, UUID> {

    @Modifying
    @Query("delete from CodeReferenceEntity r where r.repositoryRoot = :repositoryRoot")
    void deleteByRepositoryRoot(@Param("repositoryRoot") String repositoryRoot);

    @Modifying
    @Query("delete from CodeReferenceEntity r where r.repositoryRoot = :repositoryRoot "
            + "and r.relativeFilePath in :relativeFilePaths")
    void deleteByRepositoryRootAndFilePaths(@Param("repositoryRoot") String repositoryRoot,
                                            @Param("relativeFilePaths") List<String> relativeFilePaths);

    /** Precise lookup: every place that calls exactly this resolved target. */
    List<CodeReferenceEntity> findByRepositoryRootAndTargetQualifiedName(
            String repositoryRoot, String targetQualifiedName);

    /** Fallback lookup: name-only matches, including unresolved ones. May contain false positives. */
    List<CodeReferenceEntity> findByRepositoryRootAndTargetSimpleName(
            String repositoryRoot, String targetSimpleName);
}