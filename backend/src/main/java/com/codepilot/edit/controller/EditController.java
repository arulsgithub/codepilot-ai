package com.codepilot.edit.controller;

import com.codepilot.edit.dto.ApplyEditsRequest;
import com.codepilot.edit.dto.ApplyEditsResponse;
import com.codepilot.edit.dto.EditPlanRequest;
import com.codepilot.edit.dto.EditPlanResponse;
import com.codepilot.edit.service.EditApplier;
import com.codepilot.edit.service.EditDeliveryService;
import com.codepilot.edit.service.EditPlannerService;
import com.codepilot.indexing.service.RepositoryIndexingService;
import com.codepilot.repo.dto.EditDeliveryResult;
import com.codepilot.repo.dto.SourceType;
import com.codepilot.repo.service.RepositoryPathResolver;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/edits")
public class EditController {

    private static final Logger log = LoggerFactory.getLogger(EditController.class);

    private final EditPlannerService editPlannerService;
    private final EditApplier editApplier;
    private final RepositoryIndexingService indexingService;
    private final EditDeliveryService editDeliveryService;
    private final RepositoryPathResolver pathResolver;

    public EditController(EditPlannerService editPlannerService,
                          EditApplier editApplier,
                          RepositoryIndexingService indexingService,
                          EditDeliveryService editDeliveryService,
                          RepositoryPathResolver pathResolver) {
        this.editPlannerService = editPlannerService;
        this.editApplier = editApplier;
        this.indexingService = indexingService;
        this.editDeliveryService = editDeliveryService;
        this.pathResolver = pathResolver;
    }

    /** Plans edits and returns previews. Writes NOTHING to disk. */
    @PostMapping("/plan")
    public ResponseEntity<EditPlanResponse> plan(@Valid @RequestBody EditPlanRequest request) {
        // Planning now accepts a repositoryId too. Requires adding `UUID repositoryId` to
        // EditPlanRequest and relaxing @NotBlank on its repositoryRoot, same as
        // ApplyEditsRequest - otherwise a client can plan by id but not apply by it.
        String root = pathResolver.resolve(request.repositoryId(), request.repositoryRoot());
        return ResponseEntity.ok(editPlannerService.planEdits(root, request.instruction()));
    }

    /**
     * Applies approved edits atomically.
     *
     * Where they land depends on the repository's origin:
     *   LOCAL  - written to the user's folder, then the changed files are re-indexed.
     *   GITHUB - branch, commit, push, pull request. main is untouched.
     *
     * A request carrying only repositoryRoot takes the legacy direct-write path, so
     * everything that worked before this change still works.
     */
    @PostMapping("/apply")
    public ResponseEntity<ApplyEditsResponse> apply(@Valid @RequestBody ApplyEditsRequest request) {
        try {
            if (request.repositoryId() != null) {
                return ResponseEntity.ok(deliver(request));
            }

            String root = pathResolver.resolve(null, request.repositoryRoot());
            return ResponseEntity.ok(applyLocally(root, request));

        } catch (EditApplier.EditApplyException e) {
            // 409: the request was well-formed but conflicts with the current state of the files.
            // The problems list is the whole point of this branch - it tells the user WHICH
            // file failed and why, which a generic 500 would discard.
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApplyEditsResponse.failed(e.getMessage(), e.getProblems()));
        }
    }

    private ApplyEditsResponse deliver(ApplyEditsRequest request) {

        EditDeliveryResult result = editDeliveryService.deliver(
                request.repositoryId(), request.edits(), request.instruction());

        if (result.sourceType() == SourceType.GITHUB) {
            // NO re-index here, on purpose. EditDeliveryService checks the clone back out to
            // its base branch when it finishes, so these files hold their ORIGINAL content
            // again - the changes live only on the pushed branch. Re-indexing would spend
            // minutes of embedding calls to write back exactly what is already stored.
            //
            // Conceptually: the index tracks the base branch. An unmerged pull request does
            // not belong in it.
            return ApplyEditsResponse.github(
                    result.message(),
                    result.changedFiles(),
                    result.branch(),
                    result.commitSha(),
                    result.pullRequestUrl());
        }

        String root = pathResolver.resolve(request.repositoryId(), null);
        int reindexed = reindexQuietly(root, result.changedFiles());

        return ApplyEditsResponse.local(
                result.message() + "; " + reindexed + " chunk(s) re-indexed",
                result.changedFiles(),
                null);
    }

    /** Legacy path: a raw repositoryRoot always means a direct write. */
    private ApplyEditsResponse applyLocally(String root, ApplyEditsRequest request) {

        EditApplier.ApplyResult result = editApplier.apply(root, request.edits());
        int reindexed = reindexQuietly(root, result.changedFiles());

        return ApplyEditsResponse.local(
                "Applied edits to " + result.changedFiles().size() + " file(s); "
                        + reindexed + " chunk(s) re-indexed",
                result.changedFiles(),
                result.backupLocation());
    }

    /**
     * Re-index AFTER a successful write. Deliberately not fatal: the edit is already safely
     * on disk, and a stale index is a much smaller problem than reporting failure for a
     * change that actually succeeded.
     */
    private int reindexQuietly(String root, List<String> changedFiles) {
        if (changedFiles.isEmpty()) {
            return 0;
        }
        try {
            return indexingService.reindexFiles(root, changedFiles);
        } catch (Exception e) {
            log.warn("Edits applied but re-indexing failed - the index is stale for {}",
                    changedFiles, e);
            return 0;
        }
    }
}