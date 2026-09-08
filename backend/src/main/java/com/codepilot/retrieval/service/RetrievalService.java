package com.codepilot.retrieval.service;

import com.codepilot.embedding.EmbeddingClient;
import com.codepilot.indexing.entity.CodeChunkEntity;
import com.codepilot.indexing.repository.CodeChunkRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RetrievalService {

    private static final int DEFAULT_TOP_K = 5; // how many chunks we hand to the LLM per question

    private final EmbeddingClient embeddingClient;
    private final CodeChunkRepository codeChunkRepository;

    public RetrievalService(EmbeddingClient embeddingClient, CodeChunkRepository codeChunkRepository) {
        this.embeddingClient = embeddingClient;
        this.codeChunkRepository = codeChunkRepository;
    }

    /**
     * @param question       the user's chat message, in plain English (e.g. "how does sendMessage work?")
     * @param repositoryRoot which indexed repo to search within (matches what you passed to /api/v1/indexing)
     * @return the topK code chunks whose meaning is closest to the question
     */
    public List<CodeChunkEntity> retrieveRelevantChunks(String question, String repositoryRoot) {
        List<Float> questionVector = embeddingClient.embed(question, EmbeddingClient.InputType.QUERY);
        String pgvectorLiteral = toPgvectorLiteral(questionVector);
        return codeChunkRepository.findSimilarChunks(repositoryRoot, pgvectorLiteral, DEFAULT_TOP_K);
    }

    /**
     * pgvector expects vector input as text shaped like "[0.1,0.2,0.3]" - this just formats our
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
}