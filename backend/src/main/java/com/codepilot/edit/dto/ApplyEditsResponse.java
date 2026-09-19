package com.codepilot.edit.dto;

import com.codepilot.repo.dto.SourceType;

import java.util.List;

/**
 * One response shape for both delivery modes.
 *
 * A LOCAL apply fills changedFiles/backupLocation and leaves the git fields null.
 * A GITHUB apply fills branch/commitSha/pullRequestUrl and leaves backupLocation null
 * (backups still exist in the clone, but they are meaningless to the user - the pull
 * request is the record of what changed).
 *
 * Deliberately NOT two different response types: the frontend has one "apply" call and
 * should not have to branch on the response shape to know whether it succeeded.
 */
public record ApplyEditsResponse(
        boolean success,
        String message,
        List<String> changedFiles,
        String backupLocation,
        List<String> problems,

        /** Null for LOCAL. */
        SourceType sourceType,
        String branch,
        String commitSha,
        String pullRequestUrl
) {

    /** Local success. */
    public static ApplyEditsResponse local(String message, List<String> changedFiles,
                                           String backupLocation) {
        return new ApplyEditsResponse(true, message, changedFiles, backupLocation, List.of(),
                SourceType.LOCAL, null, null, null);
    }

    /** GitHub success - a pull request was opened. */
    public static ApplyEditsResponse github(String message, List<String> changedFiles,
                                            String branch, String commitSha, String pullRequestUrl) {
        return new ApplyEditsResponse(true, message, changedFiles, null, List.of(),
                SourceType.GITHUB, branch, commitSha, pullRequestUrl);
    }

    /** Validation failed - nothing was written. */
    public static ApplyEditsResponse failed(String message, List<String> problems) {
        return new ApplyEditsResponse(false, message, List.of(), null, problems,
                null, null, null, null);
    }
}