package com.codepilot.symbols.repository;

import com.codepilot.symbols.entity.CodeReferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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

    /** Everything a single file uses - the raw material for dependency analysis. */
    List<CodeReferenceEntity> findByRepositoryRootAndRelativeFilePath(
            String repositoryRoot, String relativeFilePath);

    /**
     * Batch form, used by impact analysis. Each level of the traversal asks about many targets at
     * once; querying them one by one would issue hundreds of round-trips for a deep graph.
     */
    @Query("select r from CodeReferenceEntity r where r.repositoryRoot = :repositoryRoot "
            + "and r.targetQualifiedName in :targetQualifiedNames")
    List<CodeReferenceEntity> findByTargetQualifiedNameIn(
            @Param("repositoryRoot") String repositoryRoot,
            @Param("targetQualifiedNames") Collection<String> targetQualifiedNames);

    /**
     * Batch form of the name-only lookup, for symbol-aware retrieval. Used to catch call sites
     * the symbol solver could not resolve - about a third of them in a Spring project, so
     * ignoring them would defeat the purpose of this feature.
     */
    @Query("select r from CodeReferenceEntity r where r.repositoryRoot = :repositoryRoot "
            + "and r.targetSimpleName in :targetSimpleNames and r.resolved = false")
    List<CodeReferenceEntity> findUnresolvedByTargetSimpleNameIn(
            @Param("repositoryRoot") String repositoryRoot,
            @Param("targetSimpleNames") Collection<String> targetSimpleNames);
}