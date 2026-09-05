package com.codepilot.ai.dto;

public record LLMMessage(
        String role,
        String content
) {
}