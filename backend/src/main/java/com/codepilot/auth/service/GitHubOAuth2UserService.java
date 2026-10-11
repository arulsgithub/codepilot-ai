package com.codepilot.auth.service;

import com.codepilot.auth.entity.UserEntity;
import com.codepilot.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs once per login, after GitHub has authenticated the user and issued an access token.
 *
 * Two jobs: create-or-update our own user row, and store the GitHub access token (encrypted)
 * so later clone and pull-request calls act as THAT user rather than as one shared server
 * token. That distinction matters - a shared token would give every visitor the same GitHub
 * permissions, which for a public demo means your account's permissions.
 */
@Service
public class GitHubOAuth2UserService extends DefaultOAuth2UserService {

    private static final Logger log = LoggerFactory.getLogger(GitHubOAuth2UserService.class);

    private final UserRepository userRepository;
    private final TokenCipher tokenCipher;

    public GitHubOAuth2UserService(UserRepository userRepository, TokenCipher tokenCipher) {
        this.userRepository = userRepository;
        this.tokenCipher = tokenCipher;
    }

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest request) {

        // super.loadUser calls GitHub's /user endpoint with the freshly issued token.
        OAuth2User githubUser = super.loadUser(request);

        Long githubId = ((Number) githubUser.getAttribute("id")).longValue();
        String login = githubUser.getAttribute("login");
        String name = githubUser.getAttribute("name");
        String avatarUrl = githubUser.getAttribute("avatar_url");

        UserEntity user = userRepository.findByGithubId(githubId)
                .orElseGet(() -> new UserEntity(UUID.randomUUID(), githubId, login,
                        OffsetDateTime.now()));

        // Refresh on every login: users rename themselves and change avatars.
        user.setLogin(login);
        user.setName(name);
        user.setAvatarUrl(avatarUrl);
        user.setLastLoginAt(OffsetDateTime.now());
        user.setEncryptedToken(tokenCipher.encrypt(request.getAccessToken().getTokenValue()));

        userRepository.save(user);
        log.info("GitHub login: {} (github id {})", login, githubId);

        // Carry OUR user id in the principal so the rest of the app never has to look it up
        // by GitHub id on every request. Note the token itself is deliberately NOT here -
        // it lives encrypted in the database and is fetched only when a git operation needs it.
        Map<String, Object> attributes = new HashMap<>(githubUser.getAttributes());
        attributes.put("codepilotUserId", user.getId().toString());

        return new DefaultOAuth2User(
                List.of(() -> "ROLE_USER"),
                attributes,
                "login"   // which attribute Principal.getName() returns
        );
    }
}