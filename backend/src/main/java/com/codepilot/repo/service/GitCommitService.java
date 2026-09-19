package com.codepilot.repo.service;

import com.codepilot.repo.config.GitWorkspaceProperties;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Writes: branch, stage, commit, push.
 *
 * Separate from GitRepositoryService on purpose. That class only ever READS from the
 * remote (clone, fetch, hard reset). This one changes history and pushes. Keeping the
 * read path and the write path in different classes means a bug in edit delivery can
 * never accidentally reach into the sync logic, and the blast radius of each is obvious
 * from the imports alone.
 */
@Service
public class GitCommitService {

    private static final Logger log = LoggerFactory.getLogger(GitCommitService.class);

    private static final DateTimeFormatter BRANCH_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

    private final GitWorkspaceProperties properties;

    public GitCommitService(GitWorkspaceProperties properties) {
        this.properties = properties;
    }

    /**
     * Create and check out a new branch off the current HEAD.
     * The caller applies its edits AFTER this, so everything lands on the new branch.
     */
    public String createBranch(Path repositoryDirectory, String slug) {
        String branch = properties.getBranchPrefix()
                + sanitize(slug) + "-" + OffsetDateTime.now().format(BRANCH_STAMP);

        try (Git git = Git.open(repositoryDirectory.toFile())) {
            git.checkout().setCreateBranch(true).setName(branch).call();
            log.info("Created branch {} in {}", branch, repositoryDirectory);
            return branch;
        } catch (GitAPIException e) {
            throw new GitRepositoryService.GitOperationException(
                    "Could not create branch " + branch + ": " + e.getMessage(), e);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not open repository " + repositoryDirectory, e);
        }
    }

    /**
     * Stage every change and commit.
     *
     * @return the new commit SHA, or empty when there was nothing to commit - which is a
     *         normal outcome, not an error: an edit plan whose changes were already
     *         present produces no diff.
     */
    public Optional<String> commitAll(Path repositoryDirectory, String message) {
        try (Git git = Git.open(repositoryDirectory.toFile())) {

            // setUpdate(false) so NEW files are picked up too, not only modified ones.
            git.add().addFilepattern(".").call();

            if (git.status().call().isClean()) {
                log.info("Nothing to commit in {}", repositoryDirectory);
                return Optional.empty();
            }

            RevCommit commit = git.commit()
                    .setMessage(message)
                    .setAuthor(properties.getAuthorName(), properties.getAuthorEmail())
                    .setCommitter(properties.getAuthorName(), properties.getAuthorEmail())
                    .call();

            log.info("Committed {} in {}", commit.getName().substring(0, 8), repositoryDirectory);
            return Optional.of(commit.getName());

        } catch (GitAPIException e) {
            throw new GitRepositoryService.GitOperationException(
                    "Commit failed: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not open repository " + repositoryDirectory, e);
        }
    }

    /** Push a branch to origin. Requires a token with write access. */
    public void push(Path repositoryDirectory, String branch) {
        if (!properties.hasToken()) {
            throw new GitRepositoryService.GitOperationException(
                    "Pushing requires a GITHUB_TOKEN with write access to the repository. "
                            + "None is configured.", null);
        }

        try (Git git = Git.open(repositoryDirectory.toFile())) {

            CredentialsProvider credentials =
                    new UsernamePasswordCredentialsProvider(properties.getToken(), "");

            Iterable<PushResult> results = git.push()
                    .setCredentialsProvider(credentials)
                    .setRemote("origin")
                    .setRefSpecs(new RefSpec("refs/heads/" + branch + ":refs/heads/" + branch))
                    .call();

            // JGit does NOT throw on a rejected push - it reports per-ref status. Skipping
            // this check is how you end up believing a push succeeded when the server
            // refused it (protected branch, no permission, non-fast-forward).
            for (PushResult result : results) {
                for (RemoteRefUpdate update : result.getRemoteUpdates()) {
                    RemoteRefUpdate.Status status = update.getStatus();
                    if (status != RemoteRefUpdate.Status.OK
                            && status != RemoteRefUpdate.Status.UP_TO_DATE) {
                        throw new GitRepositoryService.GitOperationException(
                                "Push of " + branch + " was rejected: " + status
                                        + (update.getMessage() == null ? "" : " - " + update.getMessage()),
                                null);
                    }
                }
            }

            log.info("Pushed branch {} to origin", branch);

        } catch (GitAPIException e) {
            throw new GitRepositoryService.GitOperationException(
                    "Push failed: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not open repository " + repositoryDirectory, e);
        }
    }

    /** Return to the repository's tracking branch, so the clone is left in a clean state. */
    public void checkout(Path repositoryDirectory, String branch) {
        try (Git git = Git.open(repositoryDirectory.toFile())) {
            git.checkout().setName(branch).call();
        } catch (GitAPIException e) {
            throw new GitRepositoryService.GitOperationException(
                    "Could not check out " + branch + ": " + e.getMessage(), e);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not open repository " + repositoryDirectory, e);
        }
    }

    public String currentCommit(Path repositoryDirectory) {
        try (Git git = Git.open(repositoryDirectory.toFile())) {
            ObjectId head = git.getRepository().resolve("HEAD");
            return head == null ? null : head.getName();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read HEAD", e);
        }
    }

    /** Branch names cannot contain spaces, '~', '^', ':', '?', '*', '[' or '..'. */
    private String sanitize(String text) {
        if (text == null || text.isBlank()) {
            return "edit";
        }
        String slug = text.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return slug.length() > 40 ? slug.substring(0, 40) : (slug.isEmpty() ? "edit" : slug);
    }

    /**
     * Stage the named files and commit. Only these files - never "git add .".
     *
     * Two reasons this matters here. EditApplier writes backups into
     * root/.codepilot-backups/, so a blanket add would put a full copy of every original
     * file into the pull request. And a clone is a shared working directory: anything else
     * that happens to be lying around must not get swept into a commit it has nothing to
     * do with.
     *
     * @param relativePaths as EditApplier reports them
     * @return the new commit SHA, or empty when there was nothing to commit - a normal
     *         outcome, not an error: an edit whose changes were already present has no diff.
     */
    public Optional<String> commitFiles(Path repositoryDirectory, List<String> relativePaths,
                                        String message) {
        try (Git git = Git.open(repositoryDirectory.toFile())) {

            for (String relativePath : relativePaths) {
                // Git filepatterns always use forward slashes, on every platform. Passing a
                // Windows path with backslashes matches nothing - and JGit does not complain,
                // it just stages zero files and you get an empty commit.
                git.add().addFilepattern(relativePath.replace('\\', '/')).call();
            }

            if (git.status().call().isClean()) {
                log.info("Nothing to commit in {}", repositoryDirectory);
                return Optional.empty();
            }

            RevCommit commit = git.commit()
                    .setMessage(message)
                    .setAuthor(properties.getAuthorName(), properties.getAuthorEmail())
                    .setCommitter(properties.getAuthorName(), properties.getAuthorEmail())
                    .call();

            log.info("Committed {} ({} file(s)) in {}",
                    commit.getName().substring(0, 8), relativePaths.size(), repositoryDirectory);
            return Optional.of(commit.getName());

        } catch (GitAPIException e) {
            throw new GitRepositoryService.GitOperationException(
                    "Commit failed: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not open repository " + repositoryDirectory, e);
        }
    }
}