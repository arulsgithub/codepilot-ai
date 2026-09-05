package com.codepilot.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ChatRequest(
        @NotNull
        UUID conversationId,

        @NotBlank
        String message,

        @NotNull
        UUID requestId
) {
}
