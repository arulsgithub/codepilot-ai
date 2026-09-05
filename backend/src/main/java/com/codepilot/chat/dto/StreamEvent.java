package com.codepilot.chat.dto;

public record StreamEvent(
        String type,
        String content
) {
}