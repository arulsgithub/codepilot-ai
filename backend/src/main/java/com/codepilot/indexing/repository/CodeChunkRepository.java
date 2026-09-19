package com.codepilot.indexing.repository;

import com.codepilot.indexing.entity.CodeChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CodeChunkRepository extends JpaRepository<CodeChunkEntity, UUID> {

    @Modifying
    @Query("delete from CodeChunkEntity c where c.repositoryRoot = :repositoryRoot")
    void deleteByRepositoryRoot(@Param("repositoryRoot") String repositoryRoot);

    /** Used after an edit: replace just the changed files' chunks, not the whole repository. */
    @Modifying
    @Query("delete from CodeChunkEntity c where c.repositoryRoot = :repositoryRoot "
            + "and c.relativeFilePath in :relativeFilePaths")
    void deleteByRepositoryRootAndFilePaths(
            @Param("repositoryRoot") String repositoryRoot,
            @Param("relativeFilePaths") List<String> relativeFilePaths
    );

    /**
     * Fetches chunks by qualified name.
     *
     * This is what joins the symbol index to the vector index. Because JavaAstParser and
     * SymbolExtractor both name members through JavaNames, a reference's fromQualifiedName
     * (the calling method) is exactly a chunk's qualifiedName - so finding "the code that calls
     * this" is a name lookup, not line-range arithmetic.
     */
    @Query("select c from CodeChunkEntity c where c.repositoryRoot = :repositoryRoot "
            + "and c.qualifiedName in :qualifiedNames")
    List<CodeChunkEntity> findByQualifiedNameIn(
            @Param("repositoryRoot") String repositoryRoot,
            @Param("qualifiedNames") Collection<String> qualifiedNames
    );

    /**
     * "queryVector" arrives as a pgvector literal string like "[0.12,0.98,...]" (built in
     * RetrievalService) - we can't pass a Java List<Float> directly into a native query,
     * so we format it as text and let Postgres parse/cast it back into a real vector with
     * ::vector. embedding <=> :queryVector is pgvector's cosine-distance operator: SMALLER
     * means MORE similar, which is why we ORDER BY it ascending (closest first) with no DESC.
     */
    @Query(value = """
            SELECT * FROM code_chunks
            WHERE repository_root = :repositoryRoot
            ORDER BY embedding <=> CAST(:queryVector AS vector)
            LIMIT :topK
            """, nativeQuery = true)
    List<CodeChunkEntity> findSimilarChunks(
            @Param("repositoryRoot") String repositoryRoot,
            @Param("queryVector") String queryVector,
            @Param("topK") int topK
    );
}