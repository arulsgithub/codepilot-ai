package com.codepilot.symbols.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record SymbolQueryRequest(

        /** Preferred. A repository registered via /api/v1/repositories. */
        UUID repositoryId,

        /**
         * Legacy alternative to repositoryId. No longer @NotBlank - a caller using
         * repositoryId has no path to send. RepositoryPathResolver enforces that at
         * least one of the two is present, because only it can see both fields.
         */
        String repositoryRoot,

        @NotBlank(message = "symbol must not be blank")
        String symbol
) {
}