package com.codepilot.indexing.service;

import com.codepilot.chunking.dto.CodeChunk;
import com.codepilot.chunking.service.CodeChunker;
import com.codepilot.embedding.EmbeddingClient;
import com.codepilot.indexing.entity.CodeChunkEntity;
import com.codepilot.indexing.repository.CodeChunkRepository;
import com.codepilot.ingestion.dto.SourceFile;
import com.codepilot.ingestion.service.RepositoryIngestionService;
import com.codepilot.parsing.dto.CodeUnit;
import com.codepilot.parsing.service.SourceFileParsingService;
import com.pgvector.PGvector;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The single pipeline that ties every Phase 2 piece together so far:
 *   scan (ingestion) -> parse (JavaParser) -> chunk -> embed -> persist (pgvector)
 * Kept as one orchestrating service rather than spread across controllers, since this exact
 * sequence will be reused later (re-indexing on a schedule, indexing via a GitHub webhook, etc).
 */
@Service
public class RepositoryIndexingService {

    private static final int EMBEDDING_BATCH_SIZE = 20; // stay well under typical provider request limits

    private final RepositoryIngestionService ingestionService;
    private final SourceFileParsingService parsingService;
    private final CodeChunker codeChunker;
    private final EmbeddingClient embeddingClient;
    private final CodeChunkRepository codeChunkRepository;

    public RepositoryIndexingService(RepositoryIngestionService ingestionService,
                                     SourceFileParsingService parsingService,
                                     CodeChunker codeChunker,
                                     EmbeddingClient embeddingClient,
                                     CodeChunkRepository codeChunkRepository) {
        this.ingestionService = ingestionService;
        this.parsingService = parsingService;
        this.codeChunker = codeChunker;
        this.embeddingClient = embeddingClient;
        this.codeChunkRepository = codeChunkRepository;
    }

    @Transactional
    public int indexRepository(String repositoryRoot) {
        List<SourceFile> sourceFiles = ingestionService.scan(Path.of(repositoryRoot), false);
        List<CodeUnit> units = parsingService.parseAll(sourceFiles);
        List<CodeChunk> chunks = codeChunker.chunkAll(units);

        // Re-indexing replaces the previous run's chunks for this repo, rather than piling up
        // stale duplicates every time you re-run indexing during development.
        codeChunkRepository.deleteByRepositoryRoot(repositoryRoot);

        List<CodeChunkEntity> entities = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now();

        for (int i = 0; i < chunks.size(); i += EMBEDDING_BATCH_SIZE) {
            List<CodeChunk> batch = chunks.subList(i, Math.min(i + EMBEDDING_BATCH_SIZE, chunks.size()));
            List<String> texts = batch.stream().map(CodeChunk::content).toList();
            List<List<Float>> vectors = embeddingClient.embedBatch(texts);

            for (int j = 0; j < batch.size(); j++) {
                CodeChunk chunk = batch.get(j);
                float[] vectorArray = toFloatArray(vectors.get(j));

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
                entity.setEmbedding(new PGvector(vectorArray));
                entity.setCreatedAt(now);
                entities.add(entity);
            }
        }

        codeChunkRepository.saveAll(entities);
        return entities.size();
    }

    private float[] toFloatArray(List<Float> values) {
        float[] array = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            array[i] = values.get(i);
        }
        return array;
    }
}