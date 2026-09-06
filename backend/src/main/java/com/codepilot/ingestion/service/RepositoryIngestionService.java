package com.codepilot.ingestion.service;

import com.codepilot.ingestion.dto.SourceFile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * Why Files.walkFileTree instead of Files.walk(Path):
 * walk(Path) lists every file under a directory before you get a chance to react to it, so on
 * a real repo you'd still descend into node_modules/.git/target and filter afterwards.
 * walkFileTree lets us return SKIP_SUBTREE the instant we see an excluded directory name, so we
 * never even list files inside those folders - this matters a lot on repos with node_modules.
 */
@Service
public class RepositoryIngestionService {

    private final FileFilter fileFilter;

    public RepositoryIngestionService(FileFilter fileFilter) {
        this.fileFilter = fileFilter;
    }

    public List<SourceFile> scan(Path rootPath, boolean includeTestSources) {
        if (!Files.exists(rootPath)) {
            throw new IllegalArgumentException("Path does not exist: " + rootPath);
        }
        if (!Files.isDirectory(rootPath)) {
            throw new IllegalArgumentException("Path is not a directory: " + rootPath);
        }

        List<SourceFile> results = new ArrayList<>();

        try {
            Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String dirName = dir.getFileName() == null ? "" : dir.getFileName().toString();
                    if (fileFilter.isExcludedDirectory(dirName)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (!includeTestSources && isTestSourceDirectory(dirName)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (fileFilter.isIndexable(file)) {
                        results.add(new SourceFile(
                                rootPath.relativize(file).toString(),
                                file.toAbsolutePath().toString(),
                                fileFilter.languageOf(file),
                                attrs.size()
                        ));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    // Don't let one unreadable file (permissions, broken symlink) kill the whole scan.
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to scan repository at " + rootPath, e);
        }

        return results;
    }

    private boolean isTestSourceDirectory(String dirName) {
        return dirName.equals("test") || dirName.equals("tests");
    }
}