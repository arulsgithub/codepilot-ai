package com.codepilot.chat.dto;

import java.util.List;

public record StreamEvent(
        String type,
        String content,
        List<SourceReference> sources
) {

    /**
     * Convenience constructor for the existing event types (START/TOKEN/COMPLETE/ERROR),
     * which carry no sources. Keeps every current call site unchanged - only the new
     * SOURCES event uses the 3-arg form.
     */
    public StreamEvent(String type, String content) {
        this(type, content, null);
    }
}