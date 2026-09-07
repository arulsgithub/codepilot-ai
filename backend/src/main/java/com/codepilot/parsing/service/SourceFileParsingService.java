package com.codepilot.parsing.service;

import com.codepilot.ingestion.dto.SourceFile;
import com.codepilot.parsing.dto.CodeUnit;
import com.github.javaparser.ParseProblemException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads each SourceFile's content from disk and hands .java files to JavaAstParser.
 * Non-Java files are skipped here (they get a different, simpler chunker in a later step) -
 * we don't fail the whole batch for a file type we don't parse yet.
 */
@Service
public class SourceFileParsingService {

    private static final Logger log = LoggerFactory.getLogger(SourceFileParsingService.class);

    private final JavaAstParser javaAstParser;

    public SourceFileParsingService(JavaAstParser javaAstParser) {
        this.javaAstParser = javaAstParser;
    }

    public List<CodeUnit> parseAll(List<SourceFile> sourceFiles) {
        List<CodeUnit> allUnits = new ArrayList<>();

        for (SourceFile sourceFile : sourceFiles) {
            if (!"java".equals(sourceFile.language())) {
                continue;
            }
            try {
                String content = Files.readString(Path.of(sourceFile.absolutePath()));
                allUnits.addAll(javaAstParser.parse(sourceFile.relativePath(), content));
            } catch (ParseProblemException e) {
                // A single file with invalid/unsupported syntax shouldn't kill the whole scan -
                // log it so we can see coverage gaps, and move on.
                log.warn("Skipping unparsable file {}: {}", sourceFile.relativePath(), e.getMessage());
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to read " + sourceFile.absolutePath(), e);
            }
        }

        return allUnits;
    }
}