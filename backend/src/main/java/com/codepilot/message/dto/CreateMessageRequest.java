package com.codepilot.message.dto;

import com.codepilot.message.entity.MessageRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateMessageRequest(

        @NotNull(message = "Role must not be null")
        MessageRole role,

        @NotBlank(message = "Content must not be blank")
        String content
) {
}