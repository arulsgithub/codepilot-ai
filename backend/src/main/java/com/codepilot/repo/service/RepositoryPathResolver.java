package com.codepilot.repo.service;

import com.codepilot.common.config.SecurityProperties;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.UUID;

/**
 * The single place that turns "which repository?" into "which directory?" - and, since B2,
 * the place that enforces you may only name a repository you own.
 */
@Service
public class RepositoryPathResolver {

    private final RepositoryRegistryService registry;
    private final SecurityProperties securityProperties;

    public RepositoryPathResolver(RepositoryRegistryService registry,
                                  SecurityProperties securityProperties) {
        this.registry = registry;
        this.securityProperties = securityProperties;
    }

    public String resolve(UUID repositoryId, String repositoryRoot) {
        if (repositoryId != null) {
            // registry.resolvePath goes through require(), which scopes by the current user.
            // So an id belonging to someone else resolves to "not found", not to their code.
            return registry.resolvePath(repositoryId);
        }

        if (repositoryRoot == null || repositoryRoot.isBlank()) {
            throw new IllegalArgumentException("Provide a repositoryId.");
        }

        // A raw filesystem path from the client is an arbitrary-file-read: nothing stops
        // "/etc" or "C:\Users". It stays available for local development because it is
        // convenient, and is off by default everywhere else.
        if (!securityProperties.isAllowRawRepositoryPaths()) {
            throw new IllegalArgumentException(
                    "Raw repositoryRoot paths are disabled on this deployment. "
                            + "Register the repository first and pass its repositoryId.");
        }

        return canonicalize(repositoryRoot);
    }

    /**
     * Normalize a user-supplied path exactly the way RepositoryRegistryService does.
     * Static so the two can never drift apart - if they did, ids and raw paths would point
     * at "different" repositories that are the same folder, and retrieval would silently
     * return nothing.
     */
    public static String canonicalize(String rawPath) {
        Path path;
        try {
            path = Path.of(rawPath).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("Not a valid path: " + rawPath, e);
        }
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("Not a directory: " + path);
        }
        return path.toString();
    }
}