package com.codepilot.repo.service;

import com.codepilot.auth.service.GitHubTokenProvider;
import com.codepilot.repo.config.GitWorkspaceProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

/**
 * The GitHub REST API - specifically, creating pull requests.
 *
 * This exists because a pull request is a GitHub concept, not a git one. Git's protocol
 * knows about refs and objects; "please review this branch" is something GitHub layered
 * on top. JGit can push the branch and then can go no further.
 */
@Service
public class GitHubApiClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubApiClient.class);

    private final GitWorkspaceProperties properties;
    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GitHubTokenProvider tokenProvider;

    public GitHubApiClient(GitWorkspaceProperties properties,
                           GitHubTokenProvider tokenProvider,
                           WebClient.Builder builder) {
        this.properties = properties;
        this.tokenProvider = tokenProvider;
        // No default Authorization header: the token is per-user now, so it must be set per
        // request. A default header here would be a cross-user leak waiting to happen - one
        // user's token baked into a singleton bean and used for everyone else's pull requests.
        this.webClient = builder.baseUrl(properties.getApiBaseUrl()).build();
    }

    /**
     * @param headBranch the branch containing the changes
     * @param baseBranch the branch to merge into
     * @return the HTML URL of the created pull request
     */
    public String createPullRequest(String remoteUrl, String headBranch, String baseBranch,
                                    String title, String body) {

        String token = tokenProvider.require("Opening a pull request");
        OwnerRepo target = parse(remoteUrl);

        try {
            String response = webClient.post()
                    .uri("/repos/{owner}/{repo}/pulls", target.owner(), target.repo())
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .bodyValue(Map.of(
                            "title", title,
                            "head", headBranch,
                            "base", baseBranch,
                            "body", body
                    ))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode node = objectMapper.readTree(response);
            String url = node.path("html_url").asText();
            log.info("Opened pull request {}", url);
            return url;

        } catch (WebClientResponseException e) {
            throw new GitRepositoryService.GitOperationException(friendly(e, target), e);
        } catch (Exception e) {
            throw new GitRepositoryService.GitOperationException(
                    "Could not create pull request: " + e.getMessage(), e);
        }
    }

    private String friendly(WebClientResponseException e, OwnerRepo target) {
        int status = e.getStatusCode().value();
        return switch (status) {
            case 401 -> "GitHub rejected your access token (401). Sign out and sign in again.";
            case 403 -> "GitHub refused the request (403). Your account may not have write "
                    + "access to " + target.owner() + "/" + target.repo() + ".";
            case 404 -> "GitHub returned 404 for " + target.owner() + "/" + target.repo()
                    + ". For a private repository this usually means your account cannot "
                    + "see it, or the 'repo' scope was not granted at sign-in.";
            case 422 -> "GitHub rejected the pull request (422): " + e.getResponseBodyAsString()
                    + ". Common causes: the branch was never pushed, or a PR already exists for it.";
            default -> "GitHub returned " + status + ": " + e.getResponseBodyAsString();
        };
    }

    /** https://github.com/arulsgithub/codepilot-ai.git -> (arulsgithub, codepilot-ai) */
    static OwnerRepo parse(String remoteUrl) {
        String cleaned = remoteUrl.replaceAll("\\.git$", "").replaceAll("/+$", "");
        String[] parts = cleaned.split("/");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Cannot determine owner/repo from " + remoteUrl);
        }
        return new OwnerRepo(parts[parts.length - 2], parts[parts.length - 1]);
    }

    record OwnerRepo(String owner, String repo) {
    }
}