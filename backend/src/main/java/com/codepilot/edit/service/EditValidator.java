package com.codepilot.edit.service;

import com.codepilot.edit.dto.FileEdit;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class EditValidator {

    /** @return null if the edit is valid, otherwise a human-readable reason it was rejected. */
    public String validate(String repositoryRoot, FileEdit edit) {

        Path root = Path.of(repositoryRoot).toAbsolutePath().normalize();
        Path target;
        try {
            target = root.resolve(edit.relativeFilePath()).toAbsolutePath().normalize();
        } catch (Exception e) {
            return "Invalid file path: " + edit.relativeFilePath();
        }

        // SECURITY: normalize() collapses "..", so this catches path traversal attempts.
        // Without this check, a malicious or confused model could target any file on disk.
        if (!target.startsWith(root)) {
            return "Path escapes the repository root: " + edit.relativeFilePath();
        }
        if (!Files.isRegularFile(target)) {
            return "File does not exist: " + edit.relativeFilePath();
        }
        if (edit.searchText() == null || edit.searchText().isBlank()) {
            return "Empty SEARCH block";
        }

        String content;
        try {
            content = Files.readString(target);
        } catch (IOException e) {
            return "Could not read file: " + e.getMessage();
        }

        // Exactly-once is the core safety rule of the search/replace format.
        int first = content.indexOf(edit.searchText());
        if (first < 0) {
            return "SEARCH text not found in file (the model may have invented or abbreviated it)";
        }
        if (content.indexOf(edit.searchText(), first + 1) >= 0) {
            return "SEARCH text appears more than once - the edit is ambiguous";
        }
        return null;
    }

    /** Applies the edit in memory only - used to render the preview diff. */
    public String applyInMemory(String originalContent, FileEdit edit) {
        return originalContent.replace(edit.searchText(), edit.replaceText());
    }
}