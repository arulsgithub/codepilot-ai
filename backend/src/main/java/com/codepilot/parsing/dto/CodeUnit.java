package com.codepilot.parsing.dto;

/**
 * One parsed, addressable unit of source code - e.g. a single method or a class declaration.
 * This is the thing we'll eventually chunk further if too large, embed, and store in pgvector.
 *
 * qualifiedName - e.g. "com.codepilot.chat.service.ChatService#sendMessage" so retrieval
 *                 results can point precisely at one method, not just "somewhere in this file".
 * signature     - human-readable signature (e.g. "public Mono<ChatResponse> sendMessage(ChatRequest request)"),
 *                 shown to the user/LLM as a quick identifier without dumping the whole body.
 * sourceCode    - the exact source text of this unit (used for embeddings + display).
 * startLine/endLine - 1-based, inclusive, for jumping to the right place in a file later.
 */
public record CodeUnit(
        CodeUnitType type,
        String relativeFilePath,
        String qualifiedName,
        String signature,
        String sourceCode,
        int startLine,
        int endLine
) {
}