package com.codepilot.auth.service;

import com.codepilot.auth.entity.UserEntity;
import com.codepilot.auth.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * The one place that answers "who is making this request?".
 *
 * Every service that needs an owner calls this rather than reaching into SecurityContextHolder
 * itself - so if authentication is ever swapped (API keys, a different provider), one class
 * changes instead of a dozen.
 */
@Service
public class CurrentUserService {

    private final UserRepository userRepository;
    private final TokenCipher tokenCipher;

    public CurrentUserService(UserRepository userRepository, TokenCipher tokenCipher) {
        this.userRepository = userRepository;
        this.tokenCipher = tokenCipher;
    }

    public UUID requireUserId() {
        return currentUserId().orElseThrow(() ->
                new IllegalStateException("No authenticated user on this request"));
    }

    public Optional<UUID> currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof OAuth2User principal)) {
            return Optional.empty();
        }
        String id = principal.getAttribute("codepilotUserId");
        return id == null ? Optional.empty() : Optional.of(UUID.fromString(id));
    }

    public UserEntity requireUser() {
        return userRepository.findById(requireUserId())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
    }

    /**
     * The user's own GitHub token, decrypted, for clone and pull-request calls.
     *
     * Fetched per use and never cached in a field: a cached token would outlive a logout and
     * could end up acting for the wrong user on a shared bean.
     */
    public Optional<String> currentGitHubToken() {
        return Optional.ofNullable(requireUser().getEncryptedToken()).map(tokenCipher::decrypt);
    }
}