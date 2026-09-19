package com.codepilot.indexing.dto;

import java.util.UUID;

/**
 * Identify the repository to index in EITHER of two ways.
 *
 * repositoryRoot is no longer @NotBlank - it cannot be, because a caller supplying
 * repositoryId legitimately has no path. Validation moved from the annotation into
 * RepositoryPathResolver, which is the only place that can judge "is at least one
 * of these two present?".
 *
 * Keeping the old field working is deliberate: your existing Postman requests,
 * any scripts, and the frontend all continue to work untouched.
 */
public record IndexRequest(

        /** Preferred. A repository registered via /api/v1/repositories. */
        UUID repositoryId,

        /** Legacy. An absolute path on this machine. */
        String repositoryRoot,

        /** Optional. Null means "use the service default". */
        Boolean includeTestSources
) {
}