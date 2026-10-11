package com.codepilot.auth.controller;

import com.codepilot.auth.entity.UserEntity;
import com.codepilot.auth.service.CurrentUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final CurrentUserService currentUserService;

    public AuthController(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    /**
     * Who am I? The frontend calls this on load: 200 means signed in, 401 means show the
     * sign-in button. Note it never returns the GitHub token - that stays server-side.
     */
    @GetMapping("/me")
    public ResponseEntity<CurrentUser> me() {
        UserEntity user = currentUserService.requireUser();
        return ResponseEntity.ok(new CurrentUser(
                user.getId(), user.getLogin(), user.getName(), user.getAvatarUrl()));
    }

    public record CurrentUser(UUID id, String login, String name, String avatarUrl) {
    }
}