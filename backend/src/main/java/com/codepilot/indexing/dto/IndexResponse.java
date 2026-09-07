package com.codepilot.indexing.dto;

public record IndexResponse(
        String repositoryRoot,
        int chunksIndexed
) {
}