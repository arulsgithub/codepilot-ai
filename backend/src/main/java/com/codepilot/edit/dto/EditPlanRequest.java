package com.codepilot.edit.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record EditPlanRequest(

        /** Preferred. A repository registered via /api/v1/repositories. */
        UUID repositoryId,

        /**
         * Legacy alternative to repositoryId. No longer @NotBlank - a caller using
         * repositoryId has no path to send. RepositoryPathResolver enforces that one of
         * the two is present, since only it can see both fields.
         */
        String repositoryRoot,

        @NotBlank(message = "instruction must not be blank")
        String instruction   // e.g. "add null-checking to ChatService.chat"
) {
}