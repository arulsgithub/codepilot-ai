package com.codepilot.parsing.dto;

import jakarta.validation.constraints.NotBlank;

public record ParseRequest(
        @NotBlank(message = "rootPath must not be blank")
        String rootPath
) {
}