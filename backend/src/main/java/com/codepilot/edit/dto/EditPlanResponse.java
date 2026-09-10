package com.codepilot.edit.dto;

import java.util.List;

/**
 * The reviewable result. `applicable` is false if ANY edit failed validation - the UI should
 * refuse to offer "Apply" in that case, since Stage B applies all-or-nothing.
 */
public record EditPlanResponse(
        String summary,
        boolean applicable,
        List<EditPreview> previews
) {

    public record EditPreview(
            String relativeFilePath,
            boolean valid,
            String problem,
            String unifiedDiff,
            String searchText,
            String replaceText,
            long lastModifiedMs   // captured at plan time; apply rejects if the file changed since
    ) {
    }
}