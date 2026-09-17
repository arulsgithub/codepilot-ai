package com.codepilot.symbols.service;

import com.codepilot.ingestion.dto.SourceFile;
import com.codepilot.symbols.dto.FileSymbols;
import com.codepilot.symbols.entity.CodeReferenceEntity;
import com.codepilot.symbols.entity.CodeSymbolEntity;
import com.codepilot.symbols.repository.CodeReferenceRepository;
import com.codepilot.symbols.repository.CodeSymbolRepository;
import com.github.javaparser.JavaParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds the symbol/reference index for a repository. Deliberately separate from
 * RepositoryIndexingService (which owns embeddings) because the two answer different questions:
 * embeddings answer "what code is ABOUT this?", symbols answer "what code TOUCHES this?".
 */
@Service
public class SymbolIndexingService {

    private static final Logger log = LoggerFactory.getLogger(SymbolIndexingService.class);

    private final SymbolExtractor symbolExtractor;
    private final CodeSymbolRepository symbolRepository;
    private final CodeReferenceRepository referenceRepository;

    public SymbolIndexingService(SymbolExtractor symbolExtractor,
                                 CodeSymbolRepository symbolRepository,
                                 CodeReferenceRepository referenceRepository) {
        this.symbolExtractor = symbolExtractor;
        this.symbolRepository = symbolRepository;
        this.referenceRepository = referenceRepository;
    }

    /** Full rebuild for a repository. */
    @Transactional
    public int indexAll(String repositoryRoot, List<SourceFile> sourceFiles) {
        symbolRepository.deleteByRepositoryRoot(repositoryRoot);
        referenceRepository.deleteByRepositoryRoot(repositoryRoot);
        return index(repositoryRoot, sourceFiles);
    }

    /** Targeted refresh after an edit. */
    @Transactional
    public int reindexFiles(String repositoryRoot, List<SourceFile> sourceFiles,
                            List<String> relativeFilePaths) {
        symbolRepository.deleteByRepositoryRootAndFilePaths(repositoryRoot, relativeFilePaths);
        referenceRepository.deleteByRepositoryRootAndFilePaths(repositoryRoot, relativeFilePaths);
        return index(repositoryRoot, sourceFiles);
    }

    private int index(String repositoryRoot, List<SourceFile> sourceFiles) {
        List<SourceFile> javaFiles = sourceFiles.stream()
                .filter(f -> "java".equals(f.language()))
                .toList();
        if (javaFiles.isEmpty()) {
            return 0;
        }

        // Built once and reused - constructing type solvers is expensive.
        JavaParser parser = symbolExtractor.createParser(repositoryRoot);

        List<CodeSymbolEntity> symbolEntities = new ArrayList<>();
        List<CodeReferenceEntity> referenceEntities = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now();
        int resolvedCount = 0;
        int unresolvedCount = 0;

        for (SourceFile sourceFile : javaFiles) {
            String content;
            try {
                content = Files.readString(Path.of(sourceFile.absolutePath()));
            } catch (IOException e) {
                log.warn("Symbol extraction skipped for unreadable file {}", sourceFile.relativePath());
                continue;
            }

            FileSymbols extracted =
                    symbolExtractor.extract(parser, sourceFile.relativePath(), content);

            for (FileSymbols.ExtractedSymbol symbol : extracted.symbols()) {
                CodeSymbolEntity entity = new CodeSymbolEntity();
                entity.setId(UUID.randomUUID());
                entity.setRepositoryRoot(repositoryRoot);
                entity.setRelativeFilePath(sourceFile.relativePath());
                entity.setSymbolKind(symbol.kind());
                entity.setQualifiedName(symbol.qualifiedName());
                entity.setSimpleName(symbol.simpleName());
                entity.setSignature(symbol.signature());
                entity.setStartLine(symbol.startLine());
                entity.setEndLine(symbol.endLine());
                entity.setCreatedAt(now);
                symbolEntities.add(entity);
            }

            for (FileSymbols.ExtractedReference reference : extracted.references()) {
                CodeReferenceEntity entity = new CodeReferenceEntity();
                entity.setId(UUID.randomUUID());
                entity.setRepositoryRoot(repositoryRoot);
                entity.setRelativeFilePath(sourceFile.relativePath());
                entity.setReferenceKind(reference.kind());
                entity.setTargetQualifiedName(reference.targetQualifiedName());
                entity.setTargetSimpleName(reference.targetSimpleName());
                entity.setFromQualifiedName(reference.fromQualifiedName());
                entity.setResolved(reference.resolved());
                entity.setLineNumber(reference.lineNumber());
                entity.setCreatedAt(now);
                referenceEntities.add(entity);

                if (reference.resolved()) {
                    resolvedCount++;
                } else {
                    unresolvedCount++;
                }
            }
        }

        symbolRepository.saveAll(symbolEntities);
        referenceRepository.saveAll(referenceEntities);

        // The resolved/unresolved ratio is the health metric for this whole feature - if
        // resolution is mostly failing, find-usages will be mostly guesswork.
        log.info("Symbol index: {} symbols, {} references ({} resolved, {} name-only)",
                symbolEntities.size(), referenceEntities.size(), resolvedCount, unresolvedCount);

        return symbolEntities.size();
    }
}