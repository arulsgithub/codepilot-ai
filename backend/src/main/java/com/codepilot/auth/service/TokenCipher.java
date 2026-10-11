package com.codepilot.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts GitHub access tokens before they touch the database.
 *
 * AES-GCM, not AES-CBC: GCM is authenticated, so tampering with the ciphertext fails loudly
 * instead of silently decrypting to garbage. A random 12-byte IV is generated per encryption
 * and stored alongside the ciphertext - reusing an IV with GCM is catastrophic, it leaks the
 * key stream, so it must never be a fixed value.
 *
 * Format stored: base64(iv || ciphertext+tag)
 */
@Component
public class TokenCipher {

    private static final int IV_LENGTH = 12;      // GCM standard
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public TokenCipher(@Value("${codepilot.security.token-key:}") String configuredKey) {
        if (configuredKey == null || configuredKey.isBlank() || configuredKey.startsWith("${")) {
            throw new IllegalStateException(
                    "TOKEN_ENCRYPTION_KEY is not set. Generate one with:\n"
                            + "  [Convert]::ToBase64String((1..32|%{Get-Random -Max 256}))\n"
                            + "and set it as an environment variable. Changing this key later makes "
                            + "every stored GitHub token unreadable - users would simply log in again.");
        }

        byte[] keyBytes = Base64.getDecoder().decode(configuredKey);
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "TOKEN_ENCRYPTION_KEY must decode to 32 bytes (AES-256), got " + keyBytes.length);
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            // Deliberately does NOT include the plaintext in the message - that would put a
            // GitHub token into the log file, which is exactly what this class prevents.
            throw new IllegalStateException("Failed to encrypt token", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(stored);
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            byte[] plaintext = cipher.doFinal(combined, IV_LENGTH, combined.length - IV_LENGTH);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Usually means the encryption key changed. Recoverable: the user logs in again.
            throw new IllegalStateException(
                    "Failed to decrypt a stored token - has TOKEN_ENCRYPTION_KEY changed? "
                            + "Affected users need to sign in again.", e);
        }
    }
}