package com.codepilot.indexing.service;

import com.codepilot.chunking.dto.CodeChunk;
import com.codepilot.chunking.service.CodeChunker;
import com.codepilot.embedding.EmbeddingClient;
import com.codepilot.indexing.entity.CodeChunkEntity;
import com.codepilot.indexing.repository.CodeChunkRepository;
import com.codepilot.ingestion.dto.SourceFile;
import com.codepilot.ingestion.service.FileFilter;
import com.codepilot.ingestion.service.RepositoryIngestionService;
import com.codepilot.parsing.dto.CodeUnit;
import com.codepilot.parsing.service.SourceFileParsingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The single pipeline that ties every Phase 2 piece together:
 *   scan (ingestion) -> parse -> chunk -> embed -> persist (pgvector)
 * Kept as one orchestrating service rather than spread across controllers, since this exact
 * sequence is reused (full index, targeted re-index after an edit, and later webhooks).
 */
@Service
public class RepositoryIndexingService {

    private static final int EMBEDDING_BATCH_SIZE = 20; // stay well under typical provider request limits

    private final RepositoryIngestionService ingestionService;
    private final SourceFileParsingService parsingService;
    private final CodeChunker codeChunker;
    private final EmbeddingClient embeddingClient;
    private final CodeChunkRepository codeChunkRepository;
    private final FileFilter fileFilter;

    public RepositoryIndexingService(RepositoryIngestionService ingestionService,
                                     SourceFileParsingService parsingService,
                                     CodeChunker codeChunker,
                                     EmbeddingClient embeddingClient,
                                     CodeChunkRepository codeChunkRepository,
                                     FileFilter fileFilter) {
        this.ingestionService = ingestionService;
        this.parsingService = parsingService;
        this.codeChunker = codeChunker;
        this.embeddingClient = embeddingClient;
        this.codeChunkRepository = codeChunkRepository;
        this.fileFilter = fileFilter;
    }

    @Transactional
    public int indexRepository(String repositoryRoot) {
        List<SourceFile> sourceFiles = ingestionService.scan(Path.of(repositoryRoot), false);
        List<CodeUnit> units = parsingService.parseAll(sourceFiles);
        List<CodeChunk> chunks = codeChunker.chunkAll(units);

        // Re-indexing replaces the previous run's chunks for this repo, rather than piling up
        // stale duplicates every time you re-run indexing during development.
        codeChunkRepository.deleteByRepositoryRoot(repositoryRoot);

        return persistChunks(repositoryRoot, chunks);
    }

    /**
     * Re-indexes only the given files after an edit. A full re-index would re-embed the entire
     * repository (slow, and burns embedding-provider quota) when only a few files changed.
     */
    @Transactional
    public int reindexFiles(String repositoryRoot, List<String> relativeFilePaths) {
        if (relativeFilePaths == null || relativeFilePaths.isEmpty()) {
            return 0;
        }
        Path root = Path.of(repositoryRoot);
        List<SourceFile> sourceFiles = new ArrayList<>();

        for (String relativePath : relativeFilePaths) {
            Path absolute = root.resolve(relativePath);
            if (!Files.isRegularFile(absolute) || !fileFilter.isIndexable(absolute)) {
                continue; // edited a file we don't index (e.g. a .txt) - nothing to refresh
            }
            try {
                sourceFiles.add(new SourceFile(
                        relativePath,
                        absolute.toString(),
                        fileFilter.languageOf(absolute),
                        Files.size(absolute)));
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to stat " + absolute, e);
            }
        }

        // Always clear the old chunks for these paths, even if nothing is re-added - otherwise
        // a file that stopped being indexable would keep serving stale chunks forever.
        codeChunkRepository.deleteByRepositoryRootAndFilePaths(repositoryRoot, relativeFilePaths);

        if (sourceFiles.isEmpty()) {
            return 0;
        }

        List<CodeChunk> chunks = codeChunker.chunkAll(parsingService.parseAll(sourceFiles));
        return persistChunks(repositoryRoot, chunks);
    }

    /**
     * Embeds chunks in batches and saves them. Shared by full indexing and targeted re-indexing
     * so the batching/pacing logic lives in exactly one place.
     */
    private int persistChunks(String repositoryRoot, List<CodeChunk> chunks) {
        if (chunks.isEmpty()) {
            return 0;
        }

        List<CodeChunkEntity> entities = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now();

        for (int i = 0; i < chunks.size(); i += EMBEDDING_BATCH_SIZE) {
            if (i > 0) {
                // Space out embedding calls so we don't trip the provider's per-minute rate limit.
                sleep(1000);
            }
            List<CodeChunk> batch = chunks.subList(i, Math.min(i + EMBEDDING_BATCH_SIZE, chunks.size()));
            List<String> texts = batch.stream().map(CodeChunk::content).toList();
            List<List<Float>> vectors =
                    embeddingClient.embedBatch(texts, EmbeddingClient.InputType.PASSAGE);

            for (int j = 0; j < batch.size(); j++) {
                CodeChunk chunk = batch.get(j);

                CodeChunkEntity entity = new CodeChunkEntity();
                entity.setId(UUID.randomUUID());
                entity.setRepositoryRoot(repositoryRoot);
                entity.setRelativeFilePath(chunk.relativeFilePath());
                entity.setQualifiedName(chunk.qualifiedName());
                entity.setContent(chunk.content());
                entity.setStartLine(chunk.startLine());
                entity.setEndLine(chunk.endLine());
                entity.setChunkIndex(chunk.chunkIndex());
                entity.setTotalChunks(chunk.totalChunks());
                entity.setEmbedding(toFloatArray(vectors.get(j)));
                entity.setCreatedAt(now);
                entities.add(entity);
            }
        }

        codeChunkRepository.saveAll(entities);
        return entities.size();
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Indexing interrupted while pacing embedding requests", e);
        }
    }

    private float[] toFloatArray(List<Float> values) {
        float[] array = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            array[i] = values.get(i);
        }
        return array;
    }
}