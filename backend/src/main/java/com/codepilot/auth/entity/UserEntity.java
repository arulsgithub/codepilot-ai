package com.codepilot.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    private UUID id;

    /**
     * GitHub's numeric account id - immutable. The login is NOT the key: a user can
     * rename themselves, and someone else can then claim the old name.
     */
    @Column(name = "github_id", nullable = false, unique = true)
    private Long githubId;

    @Column(name = "login", nullable = false)
    private String login;

    @Column(name = "name")
    private String name;

    @Column(name = "avatar_url", length = 1024)
    private String avatarUrl;

    /** AES-GCM ciphertext. Read it only through TokenCipher. */
    @Column(name = "encrypted_token", columnDefinition = "TEXT")
    private String encryptedToken;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    protected UserEntity() {
    }

    public UserEntity(UUID id, Long githubId, String login, OffsetDateTime createdAt) {
        this.id = id;
        this.githubId = githubId;
        this.login = login;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public Long getGithubId() { return githubId; }
    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getEncryptedToken() { return encryptedToken; }
    public void setEncryptedToken(String encryptedToken) { this.encryptedToken = encryptedToken; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(OffsetDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
}