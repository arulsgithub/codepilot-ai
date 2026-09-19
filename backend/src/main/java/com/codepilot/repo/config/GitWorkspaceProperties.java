package com.codepilot.repo.config;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where cloned repositories live, and the credentials used to reach private ones.
 *
 * The token is read from an environment variable via a ${GITHUB_TOKEN:} placeholder
 * in application.properties. The empty default means "no token configured", which is
 * a perfectly valid state - public repos clone fine without one.
 */
@Getter
@ConfigurationProperties(prefix = "codepilot.git")
public class GitWorkspaceProperties {

    /** Root directory under which every cloned repository gets its own folder. */
    private String workspaceRoot = "./codepilot-workspace";

    /** GitHub personal access token. Blank means anonymous (public repos only). */
    private String token = "";

    /** Guard against someone pointing CodePilot at the Linux kernel by accident. */
    private int cloneDepth = 0; // 0 = full history

    public void setWorkspaceRoot(String workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public void setCloneDepth(int cloneDepth) {
        this.cloneDepth = cloneDepth;
    }

    public boolean hasToken() {
        return token != null && !token.isBlank() && !token.startsWith("${");
    }

    /** GitHub REST API root. Configurable so GitHub Enterprise works too. */
    private String apiBaseUrl = "https://api.github.com";

    /** Identity stamped on commits CodePilot makes. */
    private String authorName = "CodePilot AI";
    private String authorEmail = "codepilot@localhost";

    /** Prefix for generated branch names. */
    private String branchPrefix = "codepilot/";

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public String getAuthorEmail() {
        return authorEmail;
    }

    public void setAuthorEmail(String authorEmail) {
        this.authorEmail = authorEmail;
    }

    public String getBranchPrefix() {
        return branchPrefix;
    }

    public void setBranchPrefix(String branchPrefix) {
        this.branchPrefix = branchPrefix;
    }
}