package com.codepilot.chat.dto;

import com.codepilot.ai.model.ModelMode;
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

        /**
         * Preferred way to attach a repository: an id from /api/v1/repositories.
         *
         * THIS FIELD'S ABSENCE CAUSED A REAL BUG. Requests were already sending repositoryId;
         * with no matching component, Jackson discarded it silently, hasRepository() returned
         * false, retrieval never ran, and the model answered confidently from training data.
         * Nothing errored. The only symptom was a wrong answer that looked right.
         */
        UUID repositoryId,

        /** Legacy: an absolute path on the server. Still honoured so old clients keep working. */
        String repositoryRoot,

        ModelMode mode         // nullable - explicit mode override; null = chosen automatically
) {
}