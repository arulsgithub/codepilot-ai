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
        UUID requestId,

        String repositoryRoot // nullable - when set, this chat uses RAG against that indexed repo
) {
}
