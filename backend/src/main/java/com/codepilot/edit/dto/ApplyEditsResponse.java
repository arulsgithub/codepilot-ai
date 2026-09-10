package com.codepilot.edit.dto;

import java.util.List;

public record ApplyEditsResponse(
        boolean applied,
        String message,
        List<String> changedFiles,   // relative paths actually written
        String backupLocation,       // where originals were saved, for manual undo
        List<String> problems        // populated when applied == false
) {
}