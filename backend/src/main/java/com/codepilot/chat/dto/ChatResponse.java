package com.codepilot.chat.dto;

import java.util.UUID;

public record ChatResponse(
        UUID conversationId,
        UUID userMessageId,
        UUID assistantMessageId,
        String response
) {
}