package com.codepilot.retrieval.dto;

/**
 * One place in the codebase that uses a symbol found by semantic search.
 *
 * `confirmed` is the honesty flag: true means the symbol solver proved this call points at the
 * target; false means only the name matched. Both are worth showing the model when editing -
 * a missed call site is worse than an extra one - but the model is told which is which.
 */
public record CallSite(
        String callerQualifiedName,
        String relativeFilePath,
        int lineNumber,
        String targetQualifiedName,
        String targetSimpleName,
        boolean confirmed
) {
}