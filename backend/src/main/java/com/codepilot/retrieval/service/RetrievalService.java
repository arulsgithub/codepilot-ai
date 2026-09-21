package com.codepilot.retrieval.service;

import com.codepilot.embedding.EmbeddingClient;
import com.codepilot.indexing.entity.CodeChunkEntity;
import com.codepilot.indexing.repository.CodeChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    private static final int DEFAULT_TOP_K = 5; // how many chunks we hand to the LLM per question

    private final EmbeddingClient embeddingClient;
    private final CodeChunkRepository codeChunkRepository;

    public RetrievalService(EmbeddingClient embeddingClient, CodeChunkRepository codeChunkRepository) {
        this.embeddingClient = embeddingClient;
        this.codeChunkRepository = codeChunkRepository;
    }

    /**
     * @param question       the user's chat message, in plain English
     * @param repositoryRoot which indexed repo to search within (matches what /api/v1/indexing used)
     * @return the topK code chunks whose meaning is closest to the question
     */
    public List<CodeChunkEntity> retrieveRelevantChunks(String question, String repositoryRoot) {
        return retrieveRelevantChunks(question, repositoryRoot, DEFAULT_TOP_K);
    }

    /** Editing needs broader context than Q&A, so callers can request more chunks. */
    public List<CodeChunkEntity> retrieveRelevantChunks(String question, String repositoryRoot, int topK) {

        List<Float> questionVector = embeddingClient.embed(question, EmbeddingClient.InputType.QUERY);
        List<CodeChunkEntity> chunks = codeChunkRepository.findSimilarChunks(
                repositoryRoot, toPgvectorLiteral(questionVector), topK);

        if (chunks.isEmpty()) {
            // Zero results is almost never "this repo has nothing relevant" - similarity search
            // returns the topK nearest regardless of how distant they are. Empty means no rows
            // matched repository_root AT ALL: wrong path spelling, or never indexed.
            log.warn("Retrieval returned 0 chunks for repositoryRoot='{}'. "
                            + "The repository is probably not indexed, or was indexed under a different "
                            + "path spelling - check SELECT DISTINCT repository_root FROM code_chunks.",
                    repositoryRoot);
        } else {
            log.info("Retrieval: {} chunk(s) for '{}' from {}",
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