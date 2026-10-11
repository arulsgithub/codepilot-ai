package com.codepilot.indexing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class IndexingExecutorConfig {

    /**
     * Deliberately TINY: one worker, a short queue, and caller-runs rejection.
     *
     * Indexing is not CPU-bound, it is quota-bound. Jina allows 100K tokens/minute across the
     * whole account, so running four indexing jobs at once does not finish them four times
     * faster - it makes all four hit 429 and retry. One at a time is genuinely optimal here,
     * and it also bounds memory, since each job holds every chunk of a repository in heap
     * before persisting.
     *
     * The queue cap matters on a public demo: without it, a hundred visitors clicking "index"
     * would queue a hundred jobs and the hundredth would start an hour later having long since
     * been abandoned. Better to refuse it immediately.
     */
    @Bean("indexingExecutor")
    public ThreadPoolTaskExecutor indexingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("indexing-");
        // Abort rather than caller-runs: caller-runs would execute the job on the HTTP request
        // thread, which is exactly the multi-minute blocking request this whole change exists
        // to eliminate.
        executor.setRejectedExecutionHandler(
                new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}