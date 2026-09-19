package com.codepilot.repo.service;

import com.codepilot.repo.config.GitWorkspaceProperties;
import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Owns the on-disk clone of a GitHub repository.
 *
 * Everything downstream (ingestion, parsing, chunking, embedding, symbols) already
 * works on a plain directory, so this class's entire job is: put the code in a
 * directory, keep it up to date, and report which commit is currently checked out.
 */
@Service
public class GitRepositoryService {

    private static final Logger log = LoggerFactory.getLogger(GitRepositoryService.class);

    private final GitWorkspaceProperties properties;

    public GitRepositoryService(GitWorkspaceProperties properties) {
        this.properties = properties;
    }

    /**
     * Clone the repository if we do not have it yet, otherwise fetch and fast-forward.
     *
     * @return the commit SHA now checked out
     */
    public SyncResult cloneOrUpdate(String remoteUrl, String requestedBranch, Path targetDirectory) {
        boolean alreadyCloned = Files.isDirectory(targetDirectory.resolve(".git"));
        return alreadyCloned
                ? update(remoteUrl, requestedBranch, targetDirectory)
                : clone(remoteUrl, requestedBranch, targetDirectory);
    }

    private SyncResult clone(String remoteUrl, String requestedBranch, Path targetDirectory) {
        log.info("Cloning {} (branch={}) into {}", remoteUrl,
                requestedBranch == null ? "<default>" : requestedBranch, targetDirectory);

        try {
            Files.createDirectories(targetDirectory.getParent());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create workspace directory", e);
        }

        var command = Git.cloneRepository()
                .setURI(remoteUrl)
                .setDirectory(targetDirectory.toFile())
                .setCloneAllBranches(false);

        // Passing null lets git pick the remote's default branch (main, master, trunk...).
        // We read back what it actually chose after the clone, rather than guessing.
        if (requestedBranch != null && !requestedBranch.isBlank()) {
            command.setBranch(requestedBranch);
        }

        if (properties.getCloneDepth() > 0) {
            command.setDepth(properties.getCloneDepth());
        }

        credentials().ifPresent(command::setCredentialsProvider);

        try (Git git = command.call()) {
            String branch = git.getRepository().getBranch();
            String commit = headCommit(git);
            log.info("Cloned {} at branch {} commit {}", remoteUrl, branch, shortSha(commit));
            return new SyncResult(branch, commit, true);
        } catch (GitAPIException e) {
            throw new GitOperationException(friendlyMessage("clone", remoteUrl, e), e);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read cloned repository state", e);
        }
    }

    private SyncResult update(String remoteUrl, String requestedBranch, Path targetDirectory) {
        log.info("Updating existing clone at {}", targetDirectory);

        try (Git git = Git.open(targetDirectory.toFile())) {

            var fetch = git.fetch().setRemoveDeletedRefs(true);
            credentials().ifPresent(fetch::setCredentialsProvider);
            fetch.call();

            String branch = (requestedBranch == null || requestedBranch.isBlank())
                    ? git.getRepository().getBranch()
                    : requestedBranch;

            // If we are not already on the requested branch, create a local branch
            // tracking origin/<branch>. TRACK is what makes future fetches meaningful.
            if (!branch.equals(git.getRepository().getBranch())) {
                boolean localExists = git.branchList().call().stream()
                        .map(Ref::getName)
                        .anyMatch(name -> name.equals("refs/heads/" + branch));

                var checkout = git.checkout().setName(branch);
                if (!localExists) {
                    checkout.setCreateBranch(true)
                            .setStartPoint("origin/" + branch)
                            .setUpstreamMode(SetupUpstreamMode.TRACK);
                }
                checkout.call();
            }

            // Hard reset rather than pull/merge. This directory is CodePilot's working
            // copy, not the user's - there is nothing here worth preserving, and a merge
            // conflict in a background sync would be a genuinely awful failure mode.
            git.reset()
                    .setMode(ResetCommand.ResetType.HARD)
                    .setRef("origin/" + branch)
                    .call();

            String commit = headCommit(git);
            log.info("Updated {} to branch {} commit {}", remoteUrl, branch, shortSha(commit));
            return new SyncResult(branch, commit, false);

        } catch (GitAPIException e) {
            throw new GitOperationException(friendlyMessage("update", remoteUrl, e), e);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not open repository at " + targetDirectory, e);
        }
    }

    private String headCommit(Git git) throws IOException {
        ObjectId head = git.getRepository().resolve("HEAD");
        return head == null ? null : head.getName();
    }

    /**
     * Token auth over HTTPS. GitHub accepts the token as the *username* with an empty
     * password.
     *
     * We use a CredentialsProvider rather than embedding the token in the clone URL
     * (https://TOKEN@github.com/...). That matters: an embedded token gets written
     * into .git/config on disk, where it survives forever and leaks into any backup
     * or container image. The CredentialsProvider lives only in memory.
     */
    private java.util.Optional<CredentialsProvider> credentials() {
        if (!properties.hasToken()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(
                new UsernamePasswordCredentialsProvider(properties.getToken(), ""));
    }

    private String friendlyMessage(String operation, String remoteUrl, Exception e) {
        String raw = String.valueOf(e.getMessage()).toLowerCase(Locale.ROOT);
        if (raw.contains("not authorized") || raw.contains("authentication")
                || raw.contains("401") || raw.contains("403")) {
            return properties.hasToken()
                    ? "Git " + operation + " failed: the configured GITHUB_TOKEN was rejected for "
                    + remoteUrl + ". Check the token has 'repo' scope and has not expired."
                    : "Git " + operation + " failed: " + remoteUrl + " requires authentication. "
                    + "Set the GITHUB_TOKEN environment variable to access private repositories.";
        }
        if (raw.contains("not found") || raw.contains("repository not found")) {
            return "Git " + operation + " failed: " + remoteUrl + " was not found. "
                    + "Private repositories also report 'not found' when the token lacks access.";
        }
        return "Git " + operation + " failed for " + remoteUrl + ": " + e.getMessage();
    }

    private String shortSha(String sha) {
        return sha == null ? "unknown" : sha.substring(0, Math.min(8, sha.length()));
    }

    /** Branch and commit after a successful sync. */
    public record SyncResult(String branch, String commitSha, boolean freshClone) {
    }

    /** Thrown for any git failure so the controller can map it to a clean HTTP response. */
    public static class GitOperationException extends RuntimeException {
        public GitOperationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Turn a remote URL into a safe, stable directory name.
     * https://github.com/arulsgithub/codepilot-ai.git -> arulsgithub__codepilot-ai
     */
    public static String directoryNameFor(String remoteUrl, String branch) {
        String cleaned = remoteUrl.replaceAll("\\.git$", "").replaceAll("/+$", "");
        String[] parts = cleaned.split("/");
        String repo = parts.length > 0 ? parts[parts.length - 1] : "repo";
        String owner = parts.length > 1 ? parts[parts.length - 2] : "unknown";
        String base = (owner + "__" + repo).replaceAll("[^A-Za-z0-9._-]", "_");
        if (branch != null && !branch.isBlank() && !branch.equals("main") && !branch.equals("master")) {
            base = base + "__" + branch.replaceAll("[^A-Za-z0-9._-]", "_");
        }
        return base;
    }

    public File workspaceRoot() {
        return new File(properties.getWorkspaceRoot());
    }
}