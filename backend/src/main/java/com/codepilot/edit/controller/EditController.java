package com.codepilot.edit.controller;

import com.codepilot.edit.dto.ApplyEditsRequest;
import com.codepilot.edit.dto.ApplyEditsResponse;
import com.codepilot.edit.dto.EditPlanRequest;
import com.codepilot.edit.dto.EditPlanResponse;
import com.codepilot.edit.service.EditApplier;
import com.codepilot.edit.service.EditPlannerService;
import com.codepilot.indexing.service.RepositoryIndexingService;
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

    public EditController(EditPlannerService editPlannerService,
                          EditApplier editApplier,
                          RepositoryIndexingService indexingService) {
        this.editPlannerService = editPlannerService;
        this.editApplier = editApplier;
        this.indexingService = indexingService;
    }

    /** Plans edits and returns previews. Writes NOTHING to disk. */
    @PostMapping("/plan")
    public ResponseEntity<EditPlanResponse> plan(@Valid @RequestBody EditPlanRequest request) {
        return ResponseEntity.ok(
                editPlannerService.planEdits(request.repositoryRoot(), request.instruction()));
    }

    /** Applies approved edits atomically, then refreshes the index for the changed files. */
    @PostMapping("/apply")
    public ResponseEntity<ApplyEditsResponse> apply(@Valid @RequestBody ApplyEditsRequest request) {
        try {
            EditApplier.ApplyResult result = editApplier.apply(request);

            // Re-index AFTER a successful write. Deliberately not fatal: the edit is already
            // safely on disk, and a stale index is a much smaller problem than reporting
            // failure for a change that actually succeeded.
            int reindexed = 0;
            try {
                reindexed = indexingService.reindexFiles(request.repositoryRoot(), result.changedFiles());
            } catch (Exception e) {
                log.warn("Edits applied but re-indexing failed - the index is stale for {}",
                        result.changedFiles(), e);
            }

            return ResponseEntity.ok(new ApplyEditsResponse(
                    true,
                    "Applied edits to " + result.changedFiles().size() + " file(s); "
                            + reindexed + " chunk(s) re-indexed",
                    result.changedFiles(),
                    result.backupLocation(),
                    List.of()));

        } catch (EditApplier.EditApplyException e) {
            // 409: the request was well-formed but conflicts with the current state of the files.
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApplyEditsResponse(
                    false, e.getMessage(), List.of(), null, e.getProblems()));
        }
    }
}