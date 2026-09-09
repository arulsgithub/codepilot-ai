package com.codepilot.edit.dto;

import java.util.List;

/** What the model proposes to do, across any number of files. */
public record EditPlan(
        String summary,        // one-line description of the change, from the model
        List<FileEdit> edits
) {
}