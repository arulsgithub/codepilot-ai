package com.codepilot.chunking.dto;

/**
 * A piece of source small enough to embed. Most CodeUnits (a typical method) become exactly
 * one CodeChunk untouched; only oversized units (a big class, a long method) get split into
 * several sequential chunks.
 *
 * chunkIndex/totalChunks let us later reassemble "this is part 2 of 3 of ChatService" if the
 * user wants the full context around a partial match.
 */
public record CodeChunk(
        String relativeFilePath,
        String qualifiedName,
        String content,
        int startLine,
        int endLine,
        int chunkIndex,
        int totalChunks
) {
}