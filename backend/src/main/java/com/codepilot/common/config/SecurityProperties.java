package com.codepilot.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Deployment-shape switches. Both default to FALSE - the safe value - so a deployment that
 * forgets to configure them is locked down rather than wide open. Local development turns
 * them on explicitly.
 */
@ConfigurationProperties(prefix = "codepilot.security")
public class SecurityProperties {

    /** POST /api/v1/repositories/local. Arbitrary-file-read if enabled publicly. */
    private boolean allowLocalRepositories = false;

    /** The legacy repositoryRoot field on chat/index/edit requests. Same risk. */
    private boolean allowRawRepositoryPaths = false;

    public boolean isAllowLocalRepositories() { return allowLocalRepositories; }
    public void setAllowLocalRepositories(boolean v) { this.allowLocalRepositories = v; }
    public boolean isAllowRawRepositoryPaths() { return allowRawRepositoryPaths; }
    public void setAllowRawRepositoryPaths(boolean v) { this.allowRawRepositoryPaths = v; }
}