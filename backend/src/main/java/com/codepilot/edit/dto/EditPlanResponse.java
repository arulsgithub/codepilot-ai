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
            String problem,      // null when valid; explains the rejection otherwise
            String unifiedDiff,  // null when invalid
            String searchText,   // echoed back so Stage B can re-validate what was approved
            String replaceText
    ) {
    }
}