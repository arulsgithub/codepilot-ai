package com.codepilot.indexing.service;

import com.codepilot.auth.service.CurrentUserService;
import com.codepilot.indexing.dto.IndexJobStatus;
import com.codepilot.indexing.entity.IndexJobEntity;
import com.codepilot.indexing.repository.IndexJobRepository;
import com.codepilot.repo.service.RepositoryRegistryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

/**
 * Turns indexing from a blocking HTTP call into a background job.
 *
 * Why: indexing takes minutes, and platform proxies (Render, Fly, most load balancers) kill a
 * request that holds open past ~30-60s. The work would continue server-side while the client
 * saw a gateway timeout and had no way to learn the outcome.
 */
@Service
public class IndexJobService {

    private static final Logger log = LoggerFactory.getLogger(IndexJobService.class);

    private final IndexJobRepository jobs;
    private final RepositoryIndexingService indexingService;
    private final RepositoryRegistryService registry;
    private final CurrentUserService currentUserService;
    private final ThreadPoolTaskExecutor indexingExecutor;

    public IndexJobService(IndexJobRepository jobs,
                           RepositoryIndexingService indexingService,
                           RepositoryRegistryService registry,
                           CurrentUserService currentUserService,
                           ThreadPoolTaskExecutor indexingExecutor) {
        this.jobs = jobs;
        this.indexingService = indexingService;
        this.registry = registry;
        this.currentUserService = currentUserService;
        this.indexingExecutor = indexingExecutor;
    }

    /**
     * Called ON THE REQUEST THREAD. Everything the background job needs is resolved here,
     * because SecurityContextHolder is thread-local and the worker thread has no authenticated
     * user — currentUserService would throw there, and the ownership check would never run.
     */
    public IndexJobEntity submit(UUID repositoryId) {

        UUID userId = currentUserService.requireUserId();

        // Ownership check happens HERE, while we still have a security context. require()
        // throws for a repository the user does not own, so an unauthorised job is never queued.
        String repositoryRoot = registry.require(repositoryId).getLocalPath();

        if (jobs.existsByRepositoryIdAndStatusIn(repositoryId,
                List.of(IndexJobStatus.QUEUED, IndexJobStatus.RUNNING))) {
            throw new IllegalArgumentException(
                    "This repository is already being indexed. Wait for that job to finish.");
        }

        IndexJobEntity job = jobs.save(new IndexJobEntity(UUID.randomUUID(), repositoryId, userId));

        try {
            indexingExecutor.execute(() -> run(job.getId(), repositoryId, repositoryRoot));
        } catch (RejectedExecutionException e) {
            // Queue full. Mark the job failed immediately rather than leaving it QUEUED
            // forever — a job that will never run must not look like one that is waiting.
            markFailed(job.getId(), "The indexing queue is full. Try again in a few minutes.");
            throw new IllegalStateException(
                    "Too many indexing jobs are queued. Try again in a few minutes.", e);
        }

        log.info("Queued indexing job {} for repository {}", job.getId(), repositoryId);
        return job;
    }

    /** Runs on the indexing worker thread. No security context here — see submit(). */
    private void run(UUID jobId, UUID repositoryId, String repositoryRoot) {
        markRunning(jobId);
        try {
            int chunks = indexingService.indexRepository(repositoryRoot,
                    progress -> updateProgress(jobId, progress));

            markSucceeded(jobId, chunks);
            registry.markIndexedUnscoped(repositoryId);
            log.info("Indexing job {} succeeded: {} chunks", jobId, chunks);

        } catch (Exception e) {
            // Catch Exception, not just RuntimeException: an uncaught throwable on an executor
            // thread disappears silently, leaving the job RUNNING forever and the UI polling a
            // status that never changes.
            log.error("Indexing job {} failed", jobId, e);
            markFailed(jobId, e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    @Transactional
    public void updateProgress(UUID jobId, String progress) {
        jobs.findById(jobId).ifPresent(job -> {
            job.setProgress(progress);
            jobs.save(job);
        });
    }

    @Transactional
    public void markRunning(UUID jobId) {
        jobs.findById(jobId).ifPresent(job -> {
            job.setStatus(IndexJobStatus.RUNNING);
            job.setStartedAt(OffsetDateTime.now());
            jobs.save(job);
        });
    }

    @Transactional
    public void markSucceeded(UUID jobId, int chunkCount) {
        jobs.findById(jobId).ifPresent(job -> {
            job.setStatus(IndexJobStatus.SUCCEEDED);
            job.setChunkCount(chunkCount);
            job.setProgress("Complete");
            job.setFinishedAt(OffsetDateTime.now());
            jobs.save(job);
        });
    }

    @Transactional
    public void markFailed(UUID jobId, String message) {
        jobs.findById(jobId).ifPresent(job -> {
            job.setStatus(IndexJobStatus.FAILED);
            job.setErrorMessage(message);
            job.setFinishedAt(OffsetDateTime.now());
            jobs.save(job);
        });
    }

    /** Status lookup, scoped to the caller. */
    @Transactional(readOnly = true)
    public IndexJobEntity require(UUID jobId) {
        return jobs.findByIdAndUserId(jobId, currentUserService.requireUserId())
                .orElseThrow(() -> new IllegalArgumentException("No indexing job with id " + jobId));
    }
}