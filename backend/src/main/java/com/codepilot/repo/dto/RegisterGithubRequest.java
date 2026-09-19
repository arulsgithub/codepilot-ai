package com.codepilot.repo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RegisterGithubRequest(

        @NotBlank(message = "remoteUrl must not be blank")
        @Pattern(regexp = "^https://.*", message = "remoteUrl must be an https:// URL")
        String remoteUrl,

        /** Optional. Blank means "use the remote's default branch". */
        String branch,

        /** Optional. Defaults to the repository name from the URL. */
        String name
) {
}