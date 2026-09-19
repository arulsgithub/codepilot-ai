package com.codepilot.edit.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/**
 * What the user approved. The client echoes back the exact searchText/replaceText it was shown,
 * plus the lastModifiedMs from planning - everything is re-validated server-side, so a tampered
 * request can't reach a file outside the repository or apply an edit that no longer matches.
 */
public record ApplyEditsRequest(

        /**
         * Preferred. A repository registered via /api/v1/repositories.
         *
         * On the outer record, not on each edit: one request applies to ONE repository. Per-edit
         * ids would allow a batch spanning two repositories, which the atomic all-or-nothing
         * guarantee in EditApplier could not honour - a rollback cannot span two working trees.
         */
        UUID repositoryId,

        /**
         * Legacy alternative to repositoryId. No longer @NotBlank - a caller using repositoryId
         * has no path to send. RepositoryPathResolver enforces that one of the two is present.
         */
        String repositoryRoot,

        /**
         * The user's original request, in their own words ("rename findUser to findUserById").
         *
         * Carried through to delivery because for a GitHub repository it becomes three
         * user-visible things: the branch name, the commit message and the pull request title.
         * A PR titled "CodePilot: edit" tells a reviewer nothing.
         */
        String instruction,

        @NotEmpty(message = "at least one edit is required")
        List<ApprovedEdit> edits
) {

    public record ApprovedEdit(
            String relativeFilePath,
            String searchText,
            String replaceText,
            long expectedLastModifiedMs
    ) {
    }
}