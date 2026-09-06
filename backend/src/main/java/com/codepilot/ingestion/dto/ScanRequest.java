package com.codepilot.ingestion.dto;

import jakarta.validation.constraints.NotBlank;

public record ScanRequest(

        @NotBlank(message = "rootPath must not be blank")
        String rootPath,

        // Boxed Boolean (not primitive) so null means "use default" - handled in the controller.
        Boolean includeTestSources
) {
}