package com.codepilot.edit.dto;

/**
 * A single exact-match replacement in one file.
 *
 * searchText must appear EXACTLY ONCE in the target file. Zero matches means the model
 * hallucinated the existing code; multiple matches means the edit is ambiguous and could
 * be applied in the wrong place. Both are rejected rather than guessed at - this is what
 * makes search/replace safer than line-number-based patches.
 */
public record FileEdit(
        String relativeFilePath,
        String searchText,
        String replaceText
) {
}