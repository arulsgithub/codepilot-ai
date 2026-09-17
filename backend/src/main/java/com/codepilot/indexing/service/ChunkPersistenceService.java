package com.codepilot.indexing.service;

import com.codepilot.indexing.entity.CodeChunkEntity;
import com.codepilot.indexing.repository.CodeChunkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Owns the SHORT database transactions for chunk storage.
 *
 * Deliberately a separate bean from RepositoryIndexingService: Spring's @Transactional is
 * proxy-based, so an internal call (this.save(...)) from within the same class never goes
 * through the proxy and the transaction silently does not start. Splitting the transactional
 * work into its own bean is the standard way to avoid that trap.
 *
 * The point of this class is that each method holds a DB connection for milliseconds, not for
 * the minutes that embedding a repository takes.
 */
@Service
public class ChunkPersistenceService {

    private final CodeChunkRepository codeChunkRepository;

    public ChunkPersistenceService(CodeChunkRepository codeChunkRepository) {
        this.codeChunkRepository = codeChunkRepository;
    }

    /**
     * Replaces every chunk for a repository. Delete and insert happen in ONE transaction, so a
     * reader never observes an empty index mid-swap, and a failure leaves the old index intact.
     */
    @Transactional
    public int replaceAll(String repositoryRoot, List<CodeChunkEntity> entities) {
        codeChunkRepository.deleteByRepositoryRoot(repositoryRoot);
        codeChunkRepository.saveAll(entities);
        return entities.size();
    }

    /** Same atomic swap, scoped to specific files (used after an edit). */
    @Transactional
    public int replaceFiles(String repositoryRoot, List<String> relativeFilePaths,
                            List<CodeChunkEntity> entities) {
        codeChunkRepository.deleteByRepositoryRootAndFilePaths(repositoryRoot, relativeFilePaths);
        if (!entities.isEmpty()) {
            codeChunkRepository.saveAll(entities);
        }
        return entities.size();
    }
}