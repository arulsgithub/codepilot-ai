package com.codepilot.repo.dto;

import jakarta.validation.constraints.NotBlank;

public record RegisterLocalRequest(
        @NotBlank(message = "rootPath must not be blank")
        String rootPath,

        /** Optional. Defaults to the folder name. */
        String name
) {
}