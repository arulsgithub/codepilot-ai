package com.codepilot.embedding;

import java.util.List;

/**
 * Mirrors LLMClient's role for chat: one interface, swappable implementations. Whatever calls
 * this later (the indexing pipeline, retrieval) never needs to know if it's Nemotron's
 * embedding endpoint or a different provider underneath.
 */
public interface EmbeddingClient {

    /** @return the embedding vector for a single piece of text. */
    List<Float> embed(String text);

    /**
     * Batched form - most providers charge/rate-limit per request, so embedding a whole
     * repo's chunks one-by-one would be slow and wasteful. Implementations should call the
     * provider's batch endpoint where available.
     */
    List<List<Float>> embedBatch(List<String> texts);

    /** Vector size this client produces (e.g. 1024) - pgvector's column needs a fixed dimension. */
    int dimensions();
}