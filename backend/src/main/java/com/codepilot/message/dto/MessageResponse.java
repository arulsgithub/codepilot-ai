package com.codepilot.message.dto;

import com.codepilot.message.entity.MessageRole;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MessageResponse(
        UUID id,
        UUID conversationId,
        MessageRole role,
        String content,
        Integer sequenceNumber,
        OffsetDateTime createdAt
) {
}