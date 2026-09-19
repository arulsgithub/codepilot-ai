package com.codepilot.repo.service;

import com.codepilot.repo.dto.SourceType;
import com.codepilot.repo.entity.CodeRepositoryEntity;
import com.codepilot.repo.repository.CodeRepositoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The lookup layer: turns a repository id into a filesystem path, and keeps the
 * repositories table in step with what is actually on disk.
 *
 * Note what this class deliberately does NOT do: it never touches code_chunks or
 * code_symbols. Those tables key on the path string, and this service hands out
 * path strings. That is the whole integration.
 */
@Service
public class RepositoryRegistryService {

    private static final Logger log = LoggerFactory.getLogger(RepositoryRegistryService.class);

    private final CodeRepositoryRepository repositories;
    private final GitRepositoryService gitService;

    public RepositoryRegistryService(CodeRepositoryRepository repositories,
                                     GitRepositoryService gitService) {
        this.repositories = repositories;
        this.gitService = gitService;
    }

    /**
     * Register a folder that already exists on this machine. Nothing is copied or
     * cloned - we only record that CodePilot is allowed to look at it.
     */
    @Transactional
    public CodeRepositoryEntity registerLocal(String rootPath, String name) {
        // Same normalization as every other entry point - see RepositoryPathResolver.
        String canonical = RepositoryPathResolver.canonicalize(rootPath);
        Path path = Path.of(canonical);

        // Idempotent: registering the same folder twice returns the existing row rather
        // than creating a duplicate that would compete for the same chunks.
        return repositories.findByLocalPath(canonical).orElseGet(() -> {
            String resolvedName = (name == null || name.isBlank())
                    ? path.getFileName().toString()
                    : name;
            CodeRepositoryEntity entity = new CodeRepositoryEntity(
                    UUID.randomUUID(), resolvedName, SourceType.LOCAL,
                    null, null, canonical, OffsetDateTime.now());
            log.info("Registered local repository '{}' at {}", resolvedName, canonical);
            return repositories.save(entity);
        });
    }

    /**
     * Register a GitHub repository: clone it into the managed workspace, then record
     * where it landed. If it is already registered, this fetches the latest commits.
     *
     * Not @Transactional as a whole - cloning can take minutes and holding a database
     * connection open that long is exactly the mistake we fixed in the indexing
     * service. Git work happens outside any transaction; only the save is transactional.
     */
    public CodeRepositoryEntity registerGithub(String remoteUrl, String branch, String name) {
        String directoryName = GitRepositoryService.directoryNameFor(remoteUrl, branch);
        Path target = gitService.workspaceRoot().toPath()
                .toAbsolutePath().normalize().resolve(directoryName);

        GitRepositoryService.SyncResult result = gitService.cloneOrUpdate(remoteUrl, branch, target);

        return persistGithub(remoteUrl, name, target.toString(), result);
    }

    @Transactional
    protected CodeRepositoryEntity persistGithub(String remoteUrl, String name, String localPath,
                                                 GitRepositoryService.SyncResult result) {
        CodeRepositoryEntity entity = repositories.findByLocalPath(localPath).orElseGet(() -> {
            String resolvedName = (name == null || name.isBlank())
                    ? deriveName(remoteUrl)
                    : name;
            return new CodeRepositoryEntity(UUID.randomUUID(), resolvedName, SourceType.GITHUB,
                    remoteUrl, result.branch(), localPath, OffsetDateTime.now());
        });

        entity.setBranch(result.branch());
        entity.setLastSyncedCommit(result.commitSha());
        entity.setLastSyncedAt(OffsetDateTime.now());
        return repositories.save(entity);
    }

    /** Pull the latest commits for an already-registered GitHub repository. */
    public CodeRepositoryEntity sync(UUID id) {
        CodeRepositoryEntity entity = require(id);

        if (entity.getSourceType() != SourceType.GITHUB) {
            throw new IllegalArgumentException(
                    "Repository '" + entity.getName() + "' is LOCAL - there is no remote to sync from.");
        }

        GitRepositoryService.SyncResult result = gitService.cloneOrUpdate(
                entity.getRemoteUrl(), entity.getBranch(), Path.of(entity.getLocalPath()));

        return persistGithub(entity.getRemoteUrl(), entity.getName(), entity.getLocalPath(), result);
    }

    /**
     * The method every other part of the system will call: id in, path out.
     * Indexing, retrieval and symbol queries all take a path today, so this single
     * method is what lets them accept a repository id instead.
     */
    @Transactional(readOnly = true)
    public String resolvePath(UUID id) {
        return require(id).getLocalPath();
    }

    @Transactional
    public void markIndexed(UUID id) {
        CodeRepositoryEntity entity = require(id);
        entity.setLastIndexedAt(OffsetDateTime.now());
        repositories.save(entity);
    }

    @Transactional(readOnly = true)
    public List<CodeRepositoryEntity> findAll() {
        return repositories.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public CodeRepositoryEntity require(UUID id) {
        return repositories.findById(id).orElseThrow(() ->
                new IllegalArgumentException("No repository with id " + id));
    }

    private String deriveName(String remoteUrl) {
        String cleaned = remoteUrl.replaceAll("\\.git$", "").replaceAll("/+$", "");
        int slash = cleaned.lastIndexOf('/');
        return slash >= 0 ? cleaned.substring(slash + 1) : cleaned;
    }
}