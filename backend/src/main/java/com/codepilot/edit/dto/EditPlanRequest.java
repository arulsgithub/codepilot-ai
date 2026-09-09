package com.codepilot.edit.dto;

import jakarta.validation.constraints.NotBlank;

public record EditPlanRequest(
        @NotBlank(message = "repositoryRoot must not be blank")
        String repositoryRoot,

        @NotBlank(message = "instruction must not be blank")
        String instruction   // e.g. "add null-checking to ChatService.chat"
) {
}