package com.codepilot.symbols.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record ImpactRequest(

        UUID repositoryId,

        String repositoryRoot,

        @NotBlank(message = "symbol must not be blank")
        String symbol,

        /** Optional. Null means the service default. */
        Integer maxDepth
) {
}