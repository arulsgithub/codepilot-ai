package com.codepilot.ai.dto;

import java.util.List;

public record LLMResponse(
        String id,
        String object,
        List<Choice> choices
) {

    public record Choice(
            Integer index,
            Message message
    ) {
    }

    public record Message(
            String role,
            String content
    ) {
    }
}