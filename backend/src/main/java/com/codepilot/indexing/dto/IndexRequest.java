package com.codepilot.indexing.dto;

import jakarta.validation.constraints.NotBlank;

public record IndexRequest(
        @NotBlank(message = "repositoryRoot must not be blank")
        String repositoryRoot
) {
}