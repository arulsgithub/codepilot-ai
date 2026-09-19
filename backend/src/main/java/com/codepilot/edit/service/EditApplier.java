package com.codepilot.edit.service;

import com.codepilot.edit.dto.ApplyEditsRequest;
import com.codepilot.edit.dto.FileEdit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Applies approved edits atomically.
 *
 * Four properties this guarantees:
 *  1. Nothing is trusted from the client - paths and match text are re-validated here.
 *  2. Multiple edits to one file apply SEQUENTIALLY against the evolving content.
 *  3. All-or-nothing: validation happens entirely before any write, and a failed write
 *     rolls every file back.
 *  4. A file's original line endings survive the edit - matching happens on LF-normalised
 *     text, but what gets written back uses whatever the file already used.
 */
@Service
public class EditApplier {

    private static final Logger log = LoggerFactory.getLogger(EditApplier.class);
    private static final String BACKUP_DIR = ".codepilot-backups";

    private final EditValidator validator;

    public EditApplier(EditValidator validator) {
        this.validator = validator;
    }

    /** Thrown when validation fails; carries every problem so the UI can show them all at once. */
    public static class EditApplyException extends RuntimeException {
        private final List<String> problems;

        public EditApplyException(String message, List<String> problems) {
            super(message);
            this.problems = problems;
        }

        public List<String> getProblems() {
            return problems;
        }
    }

    public record ApplyResult(List<String> changedFiles, String backupLocation) {
    }

    public ApplyResult apply(ApplyEditsRequest request) {

        Path root = Path.of(request.repositoryRoot()).toAbsolutePath().normalize();
        List<String> problems = new ArrayList<>();

        // ---- Phase 1: group edits by file ----
        // Multiple edits can target the same file. They must be applied SEQUENTIALLY to the
        // same evolving content - validating each against the pristine original would break
        // as soon as two edits overlap or the second sits in text the first rewrote.
        Map<String, List<ApplyEditsRequest.ApprovedEdit>> byFile = new LinkedHashMap<>();
        for (ApplyEditsRequest.ApprovedEdit edit : request.edits()) {
            byFile.computeIfAbsent(edit.relativeFilePath(), k -> new ArrayList<>()).add(edit);
        }

        // ---- Phase 2: validate everything and compute final content, WITHOUT writing ----
        // originalContent holds the RAW bytes-as-read, so backups and rollback restore the file
        // exactly as it was, line endings included.
        Map<Path, String> originalContent = new LinkedHashMap<>();
        Map<Path, String> newContent = new LinkedHashMap<>();

        for (Map.Entry<String, List<ApplyEditsRequest.ApprovedEdit>> entry : byFile.entrySet()) {
            String relativePath = entry.getKey();
            Path target = root.resolve(relativePath).toAbsolutePath().normalize();

            // SECURITY: re-check the sandbox here, not just at plan time. The client controls
            // this path and could have altered it between planning and applying.
            if (!target.startsWith(root)) {
                problems.add(relativePath + ": path escapes the repository root");
                continue;
            }
            if (!Files.isRegularFile(target)) {
                problems.add(relativePath + ": file does not exist");
                continue;
            }

            String rawContent;
            long actualModified;
            try {
                rawContent = Files.readString(target);
                actualModified = Files.getLastModifiedTime(target).toMillis();
            } catch (IOException e) {
                problems.add(relativePath + ": could not read - " + e.getMessage());
                continue;
            }
            originalContent.put(target, rawContent);

            // Conflict detection: if the file changed since we planned against it (you edited it
            // in your IDE, or a git operation touched it), the diff you approved is stale.
            long expected = entry.getValue().getFirst().expectedLastModifiedMs();
            if (expected > 0 && expected != actualModified) {
                problems.add(relativePath
                        + ": file changed on disk since the edit was planned - re-plan the edit");
                continue;
            }

            // Remember the file's own convention, then work entirely in LF.
            boolean crlf = LineEndings.usesCrlf(rawContent);
            String working = LineEndings.normalize(rawContent);
            boolean fileFailed = false;

            for (ApplyEditsRequest.ApprovedEdit edit : entry.getValue()) {
                FileEdit fileEdit = new FileEdit(relativePath, edit.searchText(), edit.replaceText());

                if (fileEdit.searchText() == null || fileEdit.searchText().isBlank()) {
                    problems.add(relativePath + ": empty SEARCH text");
                    fileFailed = true;
                    break;
                }

                String normalizedSearch = LineEndings.normalize(fileEdit.searchText());

                // Validate against the WORKING copy, so edit #2 sees edit #1's result.
                int first = working.indexOf(normalizedSearch);
                if (first < 0) {
                    problems.add(relativePath
                            + ": SEARCH text not found (it may overlap another edit in this batch)");
                    fileFailed = true;
                    break;
                }
                if (working.indexOf(normalizedSearch, first + 1) >= 0) {
                    problems.add(relativePath + ": SEARCH text appears more than once - ambiguous");
                    fileFailed = true;
                    break;
                }
                working = validator.applyInMemory(working, fileEdit);
            }

            if (!fileFailed) {
                // Restore the file's original line endings before this ever reaches disk.
                // Writing LF into a CRLF file would show every line as changed in git.
                newContent.put(target, LineEndings.restore(working, crlf));
            }
        }

        if (!problems.isEmpty()) {
            // Nothing has been written at this point - failing here is completely safe.
            throw new EditApplyException("Edits were not applied", problems);
        }

        // ---- Phase 3: back up originals ----
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path backupRoot = root.resolve(BACKUP_DIR).resolve(stamp);
        try {
            for (Map.Entry<Path, String> entry : originalContent.entrySet()) {
                Path relative = root.relativize(entry.getKey());
                Path backupFile = backupRoot.resolve(relative);
                Files.createDirectories(backupFile.getParent());
                Files.writeString(backupFile, entry.getValue());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create backups - refusing to apply edits", e);
        }

        // ---- Phase 4: write, with in-memory rollback on any failure ----
        List<Path> written = new ArrayList<>();
        try {
            for (Map.Entry<Path, String> entry : newContent.entrySet()) {
                Files.writeString(entry.getKey(), entry.getValue());
                written.add(entry.getKey());
            }
        } catch (IOException e) {
            log.error("Write failed after {} file(s); rolling back", written.size(), e);
            for (Path path : written) {
                try {
                    Files.writeString(path, originalContent.get(path));
                } catch (IOException rollbackFailure) {
                    // Worst case: report loudly and point at the backups.
                    log.error("ROLLBACK FAILED for {} - restore manually from {}",
                            path, backupRoot, rollbackFailure);
                }
            }
            throw new EditApplyException("Write failed and changes were rolled back",
                    List.of(String.valueOf(e.getMessage()),
                            "Originals are backed up at " + backupRoot));
        }

        Set<String> changed = new LinkedHashSet<>();
        newContent.keySet().forEach(p -> changed.add(root.relativize(p).toString()));
        log.info("Applied edits to {} file(s); backups at {}", changed.size(), backupRoot);

        return new ApplyResult(new ArrayList<>(changed), backupRoot.toString());
    }
}