package com.codepilot.indexing.service;

import com.codepilot.chunking.dto.CodeChunk;
import com.codepilot.chunking.service.CodeChunker;
import com.codepilot.embedding.EmbeddingClient;
import com.codepilot.indexing.entity.CodeChunkEntity;
import com.codepilot.ingestion.dto.SourceFile;
import com.codepilot.ingestion.service.FileFilter;
import com.codepilot.ingestion.service.RepositoryIngestionService;
import com.codepilot.parsing.dto.CodeUnit;
import com.codepilot.parsing.service.SourceFileParsingService;
import com.codepilot.symbols.service.SymbolIndexingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
 *   scan (ingestion) -> parse -> chunk -> embed -> persist (pgvector) -> symbol index
 *
 * NOTE: this class is deliberately NOT @Transactional. Embedding a repository takes minutes of
 * network I/O, and holding a database connection open for that whole time starves the pool and
 * invites idle-connection timeouts. Instead, all embedding happens first with no DB connection
 * held, and ChunkPersistenceService performs one short atomic delete+insert at the end.
 *
 * A consequence worth stating explicitly: if embedding fails partway through, NOTHING is written,
 * so the previous index survives intact. Deleting first and inserting later would leave you with
 * an empty index after a network blip - strictly worse than doing nothing.
 */
@Service
public class RepositoryIndexingService {

    private static final Logger log = LoggerFactory.getLogger(RepositoryIndexingService.class);

    private static final int EMBEDDING_BATCH_SIZE = 20; // stay well under typical provider request limits

    private final RepositoryIngestionService ingestionService;
    private final SourceFileParsingService parsingService;
    private final CodeChunker codeChunker;
    private final EmbeddingClient embeddingClient;
    private final FileFilter fileFilter;
    private final ChunkPersistenceService chunkPersistenceService;
    private final SymbolIndexingService symbolIndexingService;

    public RepositoryIndexingService(RepositoryIngestionService ingestionService,
                                     SourceFileParsingService parsingService,
                                     CodeChunker codeChunker,
                                     EmbeddingClient embeddingClient,
                                     FileFilter fileFilter,
                                     ChunkPersistenceService chunkPersistenceService,
                                     SymbolIndexingService symbolIndexingService) {
        this.ingestionService = ingestionService;
        this.parsingService = parsingService;
        this.codeChunker = codeChunker;
        this.embeddingClient = embeddingClient;
        this.fileFilter = fileFilter;
        this.chunkPersistenceService = chunkPersistenceService;
        this.symbolIndexingService = symbolIndexingService;
    }

    public int indexRepository(String repositoryRoot) {
        List<SourceFile> sourceFiles = ingestionService.scan(Path.of(repositoryRoot), false);
        List<CodeUnit> units = parsingService.parseAll(sourceFiles);
        List<CodeChunk> chunks = codeChunker.chunkAll(units);

        // Slow phase: all network, no DB connection held.
        List<CodeChunkEntity> entities = embedChunks(repositoryRoot, chunks);

        // Symbol extraction is CPU-only (no network), so its own short transaction is fine.
        symbolIndexingService.indexAll(repositoryRoot, sourceFiles);

        // Fast phase: one short atomic transaction.
        int saved = chunkPersistenceService.replaceAll(repositoryRoot, entities);
        log.info("Indexed {} chunks for {}", saved, repositoryRoot);
        return saved;
    }

    /**
     * Re-indexes only the given files after an edit. A full re-index would re-embed the entire
     * repository (slow, and burns embedding-provider quota) when only a few files changed.
     */
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

        List<CodeChunkEntity> entities = List.of();
        if (!sourceFiles.isEmpty()) {
            List<CodeChunk> chunks = codeChunker.chunkAll(parsingService.parseAll(sourceFiles));
            entities = embedChunks(repositoryRoot, chunks);
            symbolIndexingService.reindexFiles(repositoryRoot, sourceFiles, relativeFilePaths);
        }

        // Always clear old chunks for these paths, even when nothing is re-added - otherwise a
        // file that stopped being indexable would keep serving stale chunks forever.
        return chunkPersistenceService.replaceFiles(repositoryRoot, relativeFilePaths, entities);
    }

    /**
     * Embeds chunks in batches and builds entities IN MEMORY. Does not touch the database - that
     * separation is what lets the DB transaction stay short.
     */
    private List<CodeChunkEntity> embedChunks(String repositoryRoot, List<CodeChunk> chunks) {
        if (chunks.isEmpty()) {
            return List.of();
        }

        List<CodeChunkEntity> entities = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now();
        int totalBatches = (chunks.size() + EMBEDDING_BATCH_SIZE - 1) / EMBEDDING_BATCH_SIZE;

        for (int i = 0; i < chunks.size(); i += EMBEDDING_BATCH_SIZE) {
            if (i > 0) {
                // Space out embedding calls so we don't trip the provider's per-minute rate limit.
                sleep(1000);
            }
            int batchNumber = (i / EMBEDDING_BATCH_SIZE) + 1;
            // Progress logging: a silent multi-minute run is indistinguishable from a hung one.
            log.info("Embedding batch {}/{} ({} chunks total)", batchNumber, totalBatches, chunks.size());

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

        return entities;
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