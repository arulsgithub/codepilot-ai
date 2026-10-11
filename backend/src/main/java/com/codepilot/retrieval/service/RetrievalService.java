package com.codepilot.retrieval.service;

import com.codepilot.embedding.EmbeddingClient;
import com.codepilot.indexing.entity.CodeChunkEntity;
import com.codepilot.indexing.repository.CodeChunkRepository;
import com.codepilot.retrieval.RetrievalProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    private final EmbeddingClient embeddingClient;
    private final CodeChunkRepository codeChunkRepository;
    private final RetrievalProperties properties;

    public RetrievalService(EmbeddingClient embeddingClient,
                            CodeChunkRepository codeChunkRepository,
                            RetrievalProperties properties) {
        this.embeddingClient = embeddingClient;
        this.codeChunkRepository = codeChunkRepository;
        this.properties = properties;
    }

    /**
     * Chat retrieval: a candidate pool from pgvector, packed into the chat character budget.
     *
     * @param question       the user's chat message in plain English
     * @param repositoryRoot which indexed repo to search (canonical form - see RepositoryPathResolver)
     */
    public List<CodeChunkEntity> retrieveRelevantChunks(String question, String repositoryRoot) {
        return retrieve(question, repositoryRoot, properties.getMaxContextChars());
    }

    /**
     * Editing needs wider context than Q&A - a change usually touches several places - so it
     * gets its own, larger budget.
     */
    public List<CodeChunkEntity> retrieveForEditing(String instruction, String repositoryRoot) {
        return retrieve(instruction, repositoryRoot, properties.getMaxEditContextChars());
    }

    /**
     * Kept for callers that still pass an explicit chunk count. The budget still applies -
     * topK becomes the chunk ceiling, not a licence to exceed the character limit.
     */
    public List<CodeChunkEntity> retrieveRelevantChunks(String question, String repositoryRoot, int topK) {
        List<CodeChunkEntity> candidates = search(question, repositoryRoot,
                Math.max(topK, properties.getCandidatePoolSize()));
        return ContextBudget.pack(candidates, properties.getMaxEditContextChars(), topK);
    }

    private List<CodeChunkEntity> retrieve(String question, String repositoryRoot, int maxChars) {
        // Ask for MORE candidates than we will use. The budget then picks the best combination
        // that fits, rather than being stuck with whatever five rows came back - if the top
        // chunk is huge, smaller relevant ones behind it can still make it into the prompt.
        List<CodeChunkEntity> candidates = search(question, repositoryRoot,
                properties.getCandidatePoolSize());

        return ContextBudget.pack(candidates, maxChars, properties.getMaxChunks());
    }

    private List<CodeChunkEntity> search(String question, String repositoryRoot, int limit) {
        List<Float> questionVector = embeddingClient.embed(question, EmbeddingClient.InputType.QUERY);
        List<CodeChunkEntity> chunks = codeChunkRepository.findSimilarChunks(
                repositoryRoot, toPgvectorLiteral(questionVector), limit);

        if (chunks.isEmpty()) {
            // Zero results is almost never "nothing relevant here" - similarity search returns
            // the nearest rows regardless of distance. Empty means no rows matched
            // repository_root AT ALL: wrong path spelling, or never indexed.
            log.warn("Retrieval returned 0 candidates for repositoryRoot='{}'. The repository is "
                    + "probably not indexed, or was indexed under a different path spelling - "
                    + "check SELECT DISTINCT repository_root FROM code_chunks.", repositoryRoot);
        } else {
            log.info("Retrieval: {} candidate(s) for '{}' from {}",
                    chunks.size(), truncate(question), repositoryRoot);
        }
        return chunks;
    }

    /**
     * pgvector expects vector input as text shaped like "[0.1,0.2,0.3]" - this formats our
     * Java List<Float> into that exact shape so Postgres can parse it.
     */
    private String toPgvectorLiteral(List<Float> vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(vector.get(i));
        }
        return sb.append(']').toString();
    }

    private String truncate(String text) {
        if (text == null) return "";
        return text.length() <= 60 ? text : text.substring(0, 60) + "...";
    }
}