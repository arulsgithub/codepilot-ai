package com.codepilot.ingestion.dto;

import java.util.List;

public record ScanResponse(
        String rootPath,
        int fileCount,
        List<SourceFile> files
) {
}