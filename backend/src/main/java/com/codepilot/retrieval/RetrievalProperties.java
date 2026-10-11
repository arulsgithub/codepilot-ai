package com.codepilot.retrieval;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Retrieval sizing. These were constants inside RetrievalService, but they are deployment
 * concerns - a paid model with a large token budget wants very different values from a free
 * tier capped at a few thousand tokens per minute.
 */
@ConfigurationProperties(prefix = "ai.retrieval")
public class RetrievalProperties {

    /** How many chunks to ASK the database for. Candidates, not final context. */
    private int candidatePoolSize = 15;

    /** Hard ceiling on chunks in the final context, whatever the budget allows. */
    private int maxChunks = 5;

    /**
     * Character budget for all context combined.
     *
     * Characters, not tokens, because counting tokens needs the model's tokenizer and we talk
     * to several models. For code, roughly 3.5-4 characters per token, so 8000 characters is
     * about 2000-2300 tokens - which leaves room for the system prompt, conversation history
     * and the answer inside a free tier's per-minute budget.
     *
     * This is the fix for the real problem: top-K counts chunks, so a 3-line getter and a
     * 114-line class count the same. One oversized chunk could spend the entire budget and
     * crowd out four relevant small ones.
     */
    private int maxContextChars = 8000;

    /** Editing needs wider context than Q&A, so it gets its own budget. */
    private int maxEditContextChars = 16000;

    public int getCandidatePoolSize() { return candidatePoolSize; }
    public void setCandidatePoolSize(int candidatePoolSize) { this.candidatePoolSize = candidatePoolSize; }
    public int getMaxChunks() { return maxChunks; }
    public void setMaxChunks(int maxChunks) { this.maxChunks = maxChunks; }
    public int getMaxContextChars() { return maxContextChars; }
    public void setMaxContextChars(int maxContextChars) { this.maxContextChars = maxContextChars; }
    public int getMaxEditContextChars() { return maxEditContextChars; }
    public void setMaxEditContextChars(int maxEditContextChars) { this.maxEditContextChars = maxEditContextChars; }
}