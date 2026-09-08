package com.codepilot.indexing.repository;

import com.codepilot.indexing.entity.CodeChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CodeChunkRepository extends JpaRepository<CodeChunkEntity, UUID> {

    @Modifying
    @Query("delete from CodeChunkEntity c where c.repositoryRoot = :repositoryRoot")
    void deleteByRepositoryRoot(@Param("repositoryRoot") String repositoryRoot);

    /**
     * "queryVector" arrives as a pgvector literal string like "[0.12,0.98,...]" (built in
     * RetrievalService below) - we can't pass a Java List<Float> directly into a native query,
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