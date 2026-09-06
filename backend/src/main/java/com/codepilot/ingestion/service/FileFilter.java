package com.codepilot.ingestion.service;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * Isolated on purpose: this list of excluded dirs/extensions will need tuning constantly as we
 * test on real repos, so we want one small file to touch, and it's trivially unit-testable.
 */
@Component
public class FileFilter {

    private static final Set<String> EXCLUDED_DIRS = Set.of(
            ".git", ".idea", ".vscode",
            "node_modules", "dist", "build", "target",
            ".angular", "coverage", ".mvn"
    );

    private static final Map<String, String> EXTENSION_TO_LANGUAGE = Map.ofEntries(
            Map.entry("java", "java"),
            Map.entry("ts", "typescript"),
            Map.entry("html", "html"),
            Map.entry("scss", "scss"),
            Map.entry("css", "css"),
            Map.entry("xml", "xml"),
            Map.entry("sql", "sql"),
            Map.entry("md", "markdown"),
            Map.entry("yml", "yaml"),
            Map.entry("yaml", "yaml"),
            Map.entry("properties", "properties")
    );

    private static final Set<String> EXCLUDED_FILENAMES = Set.of(
            "package-lock.json", "yarn.lock", "pnpm-lock.yaml"
    );

    public boolean isExcludedDirectory(String dirName) {
        return EXCLUDED_DIRS.contains(dirName);
    }

    public boolean isIndexable(Path path) {
        String fileName = path.getFileName().toString();
        if (EXCLUDED_FILENAMES.contains(fileName)) {
            return false;
        }
        return EXTENSION_TO_LANGUAGE.containsKey(extensionOf(fileName));
    }

    public String languageOf(Path path) {
        return EXTENSION_TO_LANGUAGE.getOrDefault(extensionOf(path.getFileName().toString()), "unknown");
    }

    private String extensionOf(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot < 0 || lastDot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(lastDot + 1).toLowerCase();
    }
}