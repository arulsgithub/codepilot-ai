package com.codepilot.repo.service;

import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.UUID;

/**
 * The single place that turns "which repository?" into "which directory?".
 *
 * Callers may identify a repository two ways:
 *   repositoryId   - preferred. Works for GitHub clones, whose path CodePilot chose.
 *   repositoryRoot - the original way. Still supported so nothing that works today breaks.
 *
 * Every path leaves here in ONE canonical form. That matters more than it looks:
 * code_chunks.repository_root and code_symbols.repository_root are matched with plain
 * string equality, so "E:/x/y" and "E:\x\y" are two different repositories as far as
 * Postgres is concerned - and a mismatch produces zero rows rather than an error,
 * which is silent and very hard to spot.
 */
@Service
public class RepositoryPathResolver {

    private final RepositoryRegistryService registry;

    public RepositoryPathResolver(RepositoryRegistryService registry) {
        this.registry = registry;
    }

    /**
     * @param repositoryId   may be null
     * @param repositoryRoot may be null or blank
     * @return the canonical absolute directory for this repository
     */
    public String resolve(UUID repositoryId, String repositoryRoot) {
        if (repositoryId != null) {
            // Already canonical - the registry normalized it at registration time.
            return registry.resolvePath(repositoryId);
        }

        if (repositoryRoot == null || repositoryRoot.isBlank()) {
            throw new IllegalArgumentException(
                    "Provide either repositoryId or repositoryRoot.");
        }

        return canonicalize(repositoryRoot);
    }

    /**
     * Normalize a user-supplied path exactly the way RepositoryRegistryService does.
     * Both call sites MUST use this method - if they ever drift apart, ids and raw
     * paths will point at "different" repositories that are actually the same folder.
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