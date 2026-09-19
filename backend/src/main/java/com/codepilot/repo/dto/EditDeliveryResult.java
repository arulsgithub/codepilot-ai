package com.codepilot.repo.dto;

import java.util.List;

/**
 * What happened to an approved edit.
 *
 * One record covers both delivery modes, with nulls marking the fields that do not apply:
 * a LOCAL delivery has no branch and no PR, a GITHUB delivery has both.
 *
 * changedFiles is present for BOTH, because the caller needs it either way - LOCAL uses it
 * to re-index, and GITHUB lists it in the pull request body.
 */
public record EditDeliveryResult(
        SourceType sourceType,
        List<String> changedFiles,
        String branch,
        String commitSha,
        String pullRequestUrl,
        String message
) {
    public int filesChanged() {
        return changedFiles.size();
    }

    public static EditDeliveryResult local(List<String> changedFiles, String backupLocation) {
        return new EditDeliveryResult(SourceType.LOCAL, changedFiles, null, null, null,
                "Applied " + changedFiles.size() + " file(s) to the working directory. "
                        + "Backups at " + backupLocation);
    }

    public static EditDeliveryResult github(List<String> changedFiles, String branch,
                                            String commitSha, String pullRequestUrl) {
        return new EditDeliveryResult(SourceType.GITHUB, changedFiles, branch, commitSha,
                pullRequestUrl, "Opened pull request " + pullRequestUrl);
    }

    public static EditDeliveryResult noChanges() {
        return new EditDeliveryResult(SourceType.GITHUB, List.of(), null, null, null,
                "The edits produced no changes - the code already matched.");
    }
}