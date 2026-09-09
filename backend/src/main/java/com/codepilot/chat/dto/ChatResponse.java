package com.codepilot.chat.dto;

import java.util.List;
import java.util.UUID;

public record ChatResponse(
        UUID conversationId,
        UUID userMessageId,
        UUID assistantMessageId,
        String response,
        List<SourceReference> sources   // null/empty when the answer used no repository context
) {
}