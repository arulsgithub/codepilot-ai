package com.codepilot.retrieval.dto;

import com.codepilot.indexing.entity.CodeChunkEntity;

import java.util.List;

/**
 * The result of combining both kinds of search:
 *  - seedChunks: what semantic search found (code the instruction is ABOUT)
 *  - callSiteChunks: what the symbol index added (code that USES it)
 *  - callSites: the individual usages, for telling the model what it must keep consistent
 */
public record EnrichedContext(
        List<CodeChunkEntity> seedChunks,
        List<CodeChunkEntity> callSiteChunks,
        List<CallSite> callSites
) {

    /** Everything the model should see, seed code first so the primary subject leads. */
    public List<CodeChunkEntity> allChunks() {
        List<CodeChunkEntity> all = new java.util.ArrayList<>(seedChunks);
        all.addAll(callSiteChunks);
        return all;
    }

    public boolean isEmpty() {
        return seedChunks.isEmpty() && callSiteChunks.isEmpty();
    }
}