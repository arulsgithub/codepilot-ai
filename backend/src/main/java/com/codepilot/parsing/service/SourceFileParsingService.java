package com.codepilot.parsing.service;

import com.codepilot.ingestion.dto.SourceFile;
import com.codepilot.parsing.dto.CodeUnit;
import com.github.javaparser.ParseProblemException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads each SourceFile from disk and parses it into CodeUnits.
 *
 * Java files get true AST parsing (method/class-level units). Every other indexable language
 * falls back to whole-file units, which CodeChunker then splits by line count. Previously
 * non-Java files were silently skipped, which meant the Angular frontend could be ingested
 * but never actually searched.
 */
@Service
public class SourceFileParsingService {

    private static final Logger log = LoggerFactory.getLogger(SourceFileParsingService.class);

    private final JavaAstParser javaAstParser;
    private final TextFileParser textFileParser;

    public SourceFileParsingService(JavaAstParser javaAstParser, TextFileParser textFileParser) {
        this.javaAstParser = javaAstParser;
        this.textFileParser = textFileParser;
    }

    public List<CodeUnit> parseAll(List<SourceFile> sourceFiles) {
        List<CodeUnit> allUnits = new ArrayList<>();
        int javaFiles = 0;
        int textFiles = 0;

        for (SourceFile sourceFile : sourceFiles) {
            String content;
            try {
                content = Files.readString(Path.of(sourceFile.absolutePath()));
            } catch (IOException e) {
                // A file that can't be read (encoding, permissions, or a binary that slipped
                // past the extension filter) shouldn't kill the whole index run.
                log.warn("Skipping unreadable file {}: {}", sourceFile.relativePath(), e.getMessage());
                continue;
            }

            if ("java".equals(sourceFile.language())) {
                try {
                    allUnits.addAll(javaAstParser.parse(sourceFile.relativePath(), content));
                    javaFiles++;
                } catch (ParseProblemException e) {
                    // Invalid/unsupported Java syntax: fall back to whole-file units rather than
                    // losing the file entirely. Partial searchability beats none.
                    log.warn("Java parse failed for {} ({}), indexing as plain text",
                            sourceFile.relativePath(), e.getMessage());
                    allUnits.addAll(textFileParser.parse(sourceFile.relativePath(), content));
                    textFiles++;
                }
            } else {
                allUnits.addAll(textFileParser.parse(sourceFile.relativePath(), content));
                textFiles++;
            }
        }

        log.info("Parsed {} units from {} Java files and {} text files",
                allUnits.size(), javaFiles, textFiles);
        return allUnits;
    }
}