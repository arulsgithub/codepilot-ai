package com.codepilot.chunking.service;

import com.codepilot.chunking.dto.CodeChunk;
import com.codepilot.parsing.dto.CodeUnit;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits CodeUnits into CodeChunks by line count. We chunk by lines (not characters/tokens)
 * because it's simple, deterministic, and keeps line numbers meaningful for navigation - a
 * token-accurate splitter is a reasonable future upgrade once we see real embedding costs.
 *
 * maxLinesPerChunk: most methods/small classes are well under this, so in practice the large
 * majority of CodeUnits pass through as a single, untouched chunk.
 */
@Component
public class CodeChunker {

    private static final int MAX_LINES_PER_CHUNK = 60;
    private static final int OVERLAP_LINES = 5; // small overlap so split points don't lose context

    public List<CodeChunk> chunk(CodeUnit unit) {
        String[] lines = unit.sourceCode().split("\n", -1);

        if (lines.length <= MAX_LINES_PER_CHUNK) {
            return List.of(new CodeChunk(
                    unit.relativeFilePath(), unit.qualifiedName(), unit.sourceCode(),
                    unit.startLine(), unit.endLine(), 0, 1
            ));
        }

        List<CodeChunk> rawChunks = new ArrayList<>();
        int start = 0;
        while (start < lines.length) {
            int end = Math.min(start + MAX_LINES_PER_CHUNK, lines.length);
            String content = String.join("\n", java.util.Arrays.asList(lines).subList(start, end));

            int chunkStartLine = unit.startLine() + start;
            int chunkEndLine = unit.startLine() + end - 1;

            rawChunks.add(new CodeChunk(
                    unit.relativeFilePath(), unit.qualifiedName(), content,
                    chunkStartLine, chunkEndLine, rawChunks.size(), -1 // totalChunks filled below
            ));

            if (end == lines.length) break;
            start = end - OVERLAP_LINES; // step back a bit so context isn't lost at the boundary
        }

        // Backfill totalChunks now that we know the final count.
        List<CodeChunk> finalChunks = new ArrayList<>();
        for (CodeChunk c : rawChunks) {
            finalChunks.add(new CodeChunk(
                    c.relativeFilePath(), c.qualifiedName(), c.content(),
                    c.startLine(), c.endLine(), c.chunkIndex(), rawChunks.size()
            ));
        }
        return finalChunks;
    }

    public List<CodeChunk> chunkAll(List<CodeUnit> units) {
        List<CodeChunk> all = new ArrayList<>();
        units.forEach(u -> all.addAll(chunk(u)));
        return all;
    }
}