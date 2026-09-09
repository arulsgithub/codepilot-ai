package com.codepilot.chat.dto;

public record SourceReference(
        String filePath,       // e.g. "com/codepilot/chat/service/ChatService.java"
        String qualifiedName,  // e.g. "com.codepilot.chat.service.ChatService#sendMessage"
        int startLine,
        int endLine
) {
}