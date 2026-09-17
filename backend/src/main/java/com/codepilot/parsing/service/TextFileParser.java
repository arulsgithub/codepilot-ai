package com.codepilot.parsing.service;

import com.codepilot.parsing.dto.CodeUnit;
import com.codepilot.parsing.dto.CodeUnitType;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Fallback parser for files we have no AST parser for (.ts, .html, .scss, .sql, .md, ...).
 *
 * It produces exactly ONE CodeUnit covering the whole file. That is deliberate: CodeChunker
 * already splits anything oversized by line count, so a whole-file unit flows through the
 * existing pipeline unchanged and gets sliced into embeddable chunks automatically.
 *
 * The obvious future upgrade is a real TypeScript parser so .ts files get method-level units
 * like Java does. This gets the frontend searchable today without that dependency.
 */
@Component
public class TextFileParser {

    public List<CodeUnit> parse(String relativeFilePath, String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return List.of();
        }

        int lineCount = (int) sourceCode.lines().count();

        return List.of(new CodeUnit(
                CodeUnitType.FILE,
                relativeFilePath,
                // No package/class to qualify with, so the path IS the identifier. This is what
                // the user sees in the sources list, so it needs to be human-recognisable.
                relativeFilePath,
                relativeFilePath,   // "signature" - just the path again for file-level units
                sourceCode,
                1,
                Math.max(lineCount, 1)
        ));
    }
}