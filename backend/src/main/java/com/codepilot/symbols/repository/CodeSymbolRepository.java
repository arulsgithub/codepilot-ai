package com.codepilot.symbols.repository;

import com.codepilot.symbols.entity.CodeSymbolEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CodeSymbolRepository extends JpaRepository<CodeSymbolEntity, UUID> {

    @Modifying
    @Query("delete from CodeSymbolEntity s where s.repositoryRoot = :repositoryRoot")
    void deleteByRepositoryRoot(@Param("repositoryRoot") String repositoryRoot);

    /** Used after an edit: refresh only the changed files. */
    @Modifying
    @Query("delete from CodeSymbolEntity s where s.repositoryRoot = :repositoryRoot "
            + "and s.relativeFilePath in :relativeFilePaths")
    void deleteByRepositoryRootAndFilePaths(@Param("repositoryRoot") String repositoryRoot,
                                            @Param("relativeFilePaths") List<String> relativeFilePaths);

    List<CodeSymbolEntity> findByRepositoryRootAndQualifiedName(String repositoryRoot, String qualifiedName);

    List<CodeSymbolEntity> findByRepositoryRootAndSimpleName(String repositoryRoot, String simpleName);
}