package com.codepilot.repo.service;

import com.codepilot.auth.service.CurrentUserService;
import com.codepilot.common.config.SecurityProperties;
import com.codepilot.repo.dto.SourceType;
import com.codepilot.repo.entity.CodeRepositoryEntity;
import com.codepilot.repo.repository.CodeRepositoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The lookup layer: turns a repository id into a filesystem path, and keeps the repositories
 * table in step with what is actually on disk.
 *
 * Note what this class deliberately does NOT do: it never touches code_chunks or code_symbols.
 * Those tables key on the path string, and this service hands out path strings. That is the
 * whole integration.
 *
 * Since B2 every method is scoped to the signed-in user. The important part is that the SCOPING
 * LIVES IN require(), which every other method funnels through - so a new method added later
 * inherits the ownership check instead of having to remember it. An id comes from the client,
 * so an unscoped lookup by id would let anyone read anyone else's code.
 */
@Service
public class RepositoryRegistryService {

    private static final Logger log = LoggerFactory.getLogger(RepositoryRegistryService.class);

    private final CodeRepositoryRepository repositories;
    private final GitRepositoryService gitService;
    private final CurrentUserService currentUserService;
    private final SecurityProperties securityProperties;

    public RepositoryRegistryService(CodeRepositoryRepository repositories,
                                     GitRepositoryService gitService,
                                     CurrentUserService currentUserService,
                                     SecurityProperties securityProperties) {
        this.repositories = repositories;
        this.gitService = gitService;
        this.currentUserService = currentUserService;
        this.securityProperties = securityProperties;
    }

    /**
     * Register a folder that already exists on this machine. Nothing is copied or cloned - we
     * only record that CodePilot may look at it.
     */
    @Transactional
    public CodeRepositoryEntity registerLocal(String rootPath, String name) {
        if (!securityProperties.isAllowLocalRepositories()) {
            // On a public deployment this endpoint is an arbitrary-file-read: a visitor could
            // register "/app" or "/etc" and then read the server's own files through chat.
            // Off by default; local development turns it on explicitly.
            throw new IllegalArgumentException(
                    "Registering local filesystem paths is disabled on this deployment. "
                            + "Add a GitHub repository instead.");
        }

        UUID userId = currentUserService.requireUserId();
        String canonical = RepositoryPathResolver.canonicalize(rootPath);

        // Idempotent PER USER: registering the same folder twice returns the existing row
        // rather than creating a duplicate that would compete for the same chunks. Scoped by
        // user because two people may legitimately register the same path independently.
        return repositories.findByLocalPathAndUserId(canonical, userId).orElseGet(() -> {
            String resolvedName = (name == null || name.isBlank())
                    ? Path.of(canonical).getFileName().toString()
                    : name;
            CodeRepositoryEntity entity = new CodeRepositoryEntity(
                    UUID.randomUUID(), userId, resolvedName, SourceType.LOCAL,
                    null, null, canonical, OffsetDateTime.now());
            log.info("Registered local repository '{}' at {} for user {}",
                    resolvedName, canonical, userId);
            return repositories.save(entity);
        });
    }

    /**
     * Register a GitHub repository: clone it into the managed workspace, then record where it
     * landed. If it is already registered, this fetches the latest commits.
     *
     * Not @Transactional as a whole - cloning can take minutes, and holding a database
     * connection open that long is exactly the mistake fixed in RepositoryIndexingService.
     * Git work happens outside any transaction; only the save is transactional.
     */
    public CodeRepositoryEntity registerGithub(String remoteUrl, String branch, String name) {

        UUID userId = currentUserService.requireUserId();
        String directoryName = GitRepositoryService.directoryNameFor(remoteUrl, branch);

        // One workspace subdirectory PER USER. Without this, two users cloning the same repo
        // would share one directory on disk - and since sync does a hard reset, one user's
        // sync would silently change what the other user's index was built from.
        Path target = gitService.workspaceRoot().toPath()
                .toAbsolutePath().normalize()
                .resolve(userId.toString())
                .resolve(directoryName);

        GitRepositoryService.SyncResult result = gitService.cloneOrUpdate(remoteUrl, branch, target);

        return persistGithub(userId, remoteUrl, name, target.toString(), result);
    }

    /**
     * ⚠️ Called from registerGithub in THIS class, so Spring's proxy is bypassed and
     * @Transactional does not actually apply (proxy-based AOP cannot intercept a self-call).
     * It works because the single save() opens its own transaction. If a second write is ever
     * added here, move this method into its own bean - the way ChunkPersistenceService was
     * split out - or the two writes will stop being atomic, silently.
     */
    @Transactional
    protected CodeRepositoryEntity persistGithub(UUID userId, String remoteUrl, String name,
                                                 String localPath,
                                                 GitRepositoryService.SyncResult result) {

        CodeRepositoryEntity entity = repositories.findByLocalPathAndUserId(localPath, userId)
                .orElseGet(() -> {
                    String resolvedName = (name == null || name.isBlank())
                            ? deriveName(remoteUrl)
                            : name;
                    return new CodeRepositoryEntity(UUID.randomUUID(), userId, resolvedName,
                            SourceType.GITHUB, remoteUrl, result.branch(), localPath,
                            OffsetDateTime.now());
                });

        entity.setBranch(result.branch());
        entity.setLastSyncedCommit(result.commitSha());
        entity.setLastSyncedAt(OffsetDateTime.now());
        return repositories.save(entity);
    }

    /** Pull the latest commits for an already-registered GitHub repository. */
    public CodeRepositoryEntity sync(UUID id) {
        // require() enforces ownership, so syncing someone else's repository is "not found".
        CodeRepositoryEntity entity = require(id);

        if (entity.getSourceType() != SourceType.GITHUB) {
            throw new IllegalArgumentException(
                    "Repository '" + entity.getName() + "' is LOCAL - there is no remote to sync from.");
        }

        GitRepositoryService.SyncResult result = gitService.cloneOrUpdate(
                entity.getRemoteUrl(), entity.getBranch(), Path.of(entity.getLocalPath()));

        return persistGithub(entity.getUserId(), entity.getRemoteUrl(), entity.getName(),
                entity.getLocalPath(), result);
    }

    /**
     * The method every other part of the system calls: id in, path out.
     *
     * Indexing, retrieval and symbol queries all take a path, so this one method is what lets
     * them accept a repository id instead - and because it goes through require(), they all
     * get the ownership check without knowing it exists.
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
        return repositories.findAllByUserIdOrderByCreatedAtDesc(
                currentUserService.requireUserId());
    }

    /**
     * The single ownership gate.
     *
     * The message says "no repository with id X" rather than "that is not yours" on purpose:
     * distinguishing the two tells a caller which ids exist, turning a guess into a confirmed
     * target.
     */
    @Transactional(readOnly = true)
    public CodeRepositoryEntity require(UUID id) {
        return repositories.findByIdAndUserId(id, currentUserService.requireUserId())
                .orElseThrow(() -> new IllegalArgumentException("No repository with id " + id));
    }

    /** Looks up without throwing - for callers that treat "absent" as a normal outcome. */
    @Transactional(readOnly = true)
    public Optional<CodeRepositoryEntity> find(UUID id) {
        return repositories.findByIdAndUserId(id, currentUserService.requireUserId());
    }

    private String deriveName(String remoteUrl) {
        String cleaned = remoteUrl.replaceAll("\\.git$", "").replaceAll("/+$", "");
        int slash = cleaned.lastIndexOf('/');
        return slash >= 0 ? cleaned.substring(slash + 1) : cleaned;
    }

    /**
     * Stamp last_indexed_at from a background thread, which has no security context so cannot
     * use the scoped require(). Safe because the caller (IndexJobService.submit) already
     * verified ownership on the request thread before the job was ever queued.
     */
    @Transactional
    public void markIndexedUnscoped(UUID id) {
        repositories.findById(id).ifPresent(entity -> {
            entity.setLastIndexedAt(OffsetDateTime.now());
            repositories.save(entity);
        });
    }

}