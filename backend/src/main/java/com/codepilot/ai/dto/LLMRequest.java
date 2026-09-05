package com.codepilot.ai.dto;

import java.util.List;

public record LLMRequest(
        String model,
        List<LLMMessage> messages,
        Double temperature,
        Boolean stream
) {
}