package com.codepilot.retrieval.service;

import com.codepilot.indexing.entity.CodeChunkEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Packs retrieved chunks into a character budget, in relevance order.
 *
 * Why this exists: selecting context by COUNT is wrong when chunk sizes vary by 40x. Five
 * chunks might be 300 characters or 12,000, so prompt size is unpredictable and a free-tier
 * tokens-per-minute limit gets blown at random.
 */
public final class ContextBudget {

    private static final Logger log = LoggerFactory.getLogger(ContextBudget.class);

    private ContextBudget() {
    }

    /**
     * @param candidates chunks in relevance order (closest first)
     * @param maxChars   total character budget across all chunks
     * @param maxChunks  hard ceiling on how many chunks to include
     */
    public static List<CodeChunkEntity> pack(List<CodeChunkEntity> candidates,
                                             int maxChars, int maxChunks) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        List<CodeChunkEntity> selected = new ArrayList<>();
        int used = 0;
        int skipped = 0;

        for (CodeChunkEntity candidate : candidates) {
            if (selected.size() >= maxChunks) {
                break;
            }

            String content = candidate.getContent();
            int size = content == null ? 0 : content.length();
            int remaining = maxChars - used;

            if (size <= remaining) {
                selected.add(candidate);
                used += size;
                continue;
            }

            // The single most relevant chunk is bigger than the entire budget. Dropping it
            // would answer the question using only less relevant code, which is worse than
            // answering from a truncated version of the right code. So truncate - but ONLY
            // when nothing has been selected yet, so this never silently shortens a chunk
            // that could simply have waited for the next request.
            if (selected.isEmpty() && remaining > 0) {
                log.warn("Top chunk {} is {} chars, over the {} char budget - truncating",
                        candidate.getQualifiedName(), size, maxChars);
                selected.add(truncatedCopy(candidate, remaining));
                used = maxChars;
                continue;
            }

            // Doesn't fit, but smaller chunks further down the list might. Continuing rather
            // than breaking fills the budget more completely; the cost is that a slightly less
            // relevant small chunk can appear where a large relevant one could not.
            skipped++;
        }

        if (skipped > 0) {
            log.debug("Context budget: {} chunk(s) skipped as too large for the remaining budget",
                    skipped);
        }

        log.info("Context: {} chunk(s), {} of {} chars used",
                selected.size(), used, maxChars);

        return selected;
    }

    /**
     * Returns a DETACHED copy rather than mutating the entity.
     *
     * The original came from a JPA query. Changing its content field risks Hibernate seeing a
     * dirty entity and writing the truncated text back into code_chunks - silently corrupting
     * the index with a shortened copy of the code. A copy cannot do that.
     */
    private static CodeChunkEntity truncatedCopy(CodeChunkEntity source, int maxChars) {
        String content = source.getContent();
        String truncated = content.substring(0, Math.min(maxChars, content.length()))
                + "\n// ... truncated to fit the context budget ...";

        CodeChunkEntity copy = new CodeChunkEntity();
        copy.setId(source.getId());
        copy.setRepositoryRoot(source.getRepositoryRoot());
        copy.setRelativeFilePath(source.getRelativeFilePath());
        copy.setQualifiedName(source.getQualifiedName());
        copy.setContent(truncated);
        copy.setStartLine(source.getStartLine());
        copy.setEndLine(source.getEndLine());
        copy.setChunkIndex(source.getChunkIndex());
        copy.setTotalChunks(source.getTotalChunks());
        copy.setCreatedAt(source.getCreatedAt());
        // embedding deliberately not copied - nothing downstream of retrieval reads it.
        return copy;
    }
}