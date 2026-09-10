package com.codepilot.edit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * What the user approved. The client echoes back the exact searchText/replaceText it was shown,
 * plus the lastModifiedMs from planning - everything is re-validated server-side, so a tampered
 * request can't reach a file outside the repository or apply an edit that no longer matches.
 */
public record ApplyEditsRequest(

        @NotBlank(message = "repositoryRoot must not be blank")
        String repositoryRoot,

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