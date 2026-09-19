package com.codepilot.symbols.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record FileQueryRequest(

        UUID repositoryId,

        String repositoryRoot,

        @NotBlank(message = "relativeFilePath must not be blank")
        String relativeFilePath
) {
}