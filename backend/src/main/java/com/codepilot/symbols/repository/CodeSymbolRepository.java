package com.codepilot.symbols.repository;

import com.codepilot.symbols.entity.CodeSymbolEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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

    /**
     * Suffix match for partial input: "ChatService#chat" should find
     * "com.codepilot.chat.service.ChatService#chat".
     *
     * LIKE alone is too loose - '%chat' also matches '...#prechat' - so the caller filters the
     * results for a real name boundary. Doing the coarse match in SQL keeps the row count small;
     * doing the precise check in Java keeps the query simple.
     */
    @Query("select s from CodeSymbolEntity s where s.repositoryRoot = :repositoryRoot "
            + "and s.qualifiedName like :pattern")
    List<CodeSymbolEntity> findByQualifiedNameLike(@Param("repositoryRoot") String repositoryRoot,
                                                   @Param("pattern") String pattern);

    /** Everything a given file declares - used to describe a file's own surface. */
    List<CodeSymbolEntity> findByRepositoryRootAndRelativeFilePath(String repositoryRoot,
                                                                   String relativeFilePath);

    /**
     * Used by dependency analysis to decide whether a reference target lives in THIS repository
     * (a project dependency) or outside it (a library call).
     */
    @Query("select distinct s.qualifiedName from CodeSymbolEntity s "
            + "where s.repositoryRoot = :repositoryRoot and s.qualifiedName in :qualifiedNames")
    List<String> findExistingQualifiedNames(@Param("repositoryRoot") String repositoryRoot,
                                            @Param("qualifiedNames") Collection<String> qualifiedNames);
}