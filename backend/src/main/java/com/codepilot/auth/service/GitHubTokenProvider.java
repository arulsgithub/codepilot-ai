package com.codepilot.auth.service;

import com.codepilot.repo.config.GitWorkspaceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Decides which GitHub token a git operation acts with.
 *
 * Order: the signed-in user's own token, then the server-wide configured token, then none.
 *
 * The user's own token is strongly preferred, and not only for tidiness. With a shared server
 * token every visitor would act with ITS permissions - unable to reach their own private
 * repositories, and able to open pull requests that appear to come from whoever owns the
 * server. Using each user's token makes permissions and attribution correct by construction.
 *
 * The configured fallback exists for local development (and for any future background job with
 * no signed-in user). On a public deployment GITHUB_TOKEN should simply be unset.
 */
@Service
public class GitHubTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(GitHubTokenProvider.class);

    private final CurrentUserService currentUserService;
    private final GitWorkspaceProperties properties;

    public GitHubTokenProvider(CurrentUserService currentUserService,
                               GitWorkspaceProperties properties) {
        this.currentUserService = currentUserService;
        this.properties = properties;
    }

    /**
     * @return the token to use, or empty for an anonymous operation (fine for cloning a
     *         public repository; never enough to push or open a pull request)
     *
     * NOTE: this reads SecurityContextHolder, which is thread-local and bound to the request
     * thread. If a git operation is ever moved onto an @Async executor or a scheduled job, the
     * context will NOT follow it and this silently falls back to the configured token. Resolve
     * the token on the request thread and pass it down if that day comes.
     */
    public Optional<String> resolve() {
        try {
            Optional<String> userToken = currentUserService.currentGitHubToken();
            if (userToken.isPresent() && !userToken.get().isBlank()) {
                return userToken;
            }
            log.debug("Signed-in user has no stored GitHub token; falling back to configuration");
        } catch (IllegalStateException e) {
            // No authenticated user on this thread - expected for a background operation.
            log.debug("No authenticated user; falling back to the configured GitHub token");
        }

        return properties.hasToken()
                ? Optional.of(properties.getToken())
                : Optional.empty();
    }

    /** For operations that cannot work anonymously: pushing, and opening pull requests. */
    public String require(String operation) {
        return resolve().orElseThrow(() -> new IllegalStateException(
                operation + " needs a GitHub token. Sign in with GitHub so CodePilot can act "
                        + "on your behalf, and make sure you granted the 'repo' scope."));
    }
}