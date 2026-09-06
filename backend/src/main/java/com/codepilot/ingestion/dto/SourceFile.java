package com.codepilot.ingestion.dto;

/**
 * A single file discovered during repository ingestion.
 *
 * relativePath - path relative to the scanned root (portable across machines; this is what
 *                we'll eventually store/display, never the absolute local path).
 * absolutePath - real path on disk, needed later to actually read file contents for parsing.
 * language     - coarse language tag derived from the extension (e.g. "java", "typescript").
 * sizeBytes    - raw file size, useful later for chunking/cost estimates.
 */
public record SourceFile(
        String relativePath,
        String absolutePath,
        String language,
        long sizeBytes
) {
}