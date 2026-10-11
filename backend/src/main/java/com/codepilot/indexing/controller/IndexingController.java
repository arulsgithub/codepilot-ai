package com.codepilot.indexing.controller;

import com.codepilot.indexing.dto.IndexJobResponse;
import com.codepilot.indexing.dto.IndexRequest;
import com.codepilot.indexing.dto.IndexResponse;
import com.codepilot.indexing.entity.IndexJobEntity;
import com.codepilot.indexing.service.IndexJobService;
import com.codepilot.indexing.service.RepositoryIndexingService;
import com.codepilot.repo.service.RepositoryPathResolver;
import com.codepilot.repo.service.RepositoryRegistryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/indexing")
public class IndexingController {

    private final RepositoryIndexingService indexingService;
    private final RepositoryPathResolver pathResolver;
    private final RepositoryRegistryService registry;
    private final IndexJobService indexJobService;

    public IndexingController(RepositoryIndexingService indexingService, RepositoryPathResolver pathResolver, RepositoryRegistryService registry, IndexJobService indexJobService) {
        this.indexingService = indexingService;
        this.pathResolver = pathResolver;
        this.registry = registry;
        this.indexJobService = indexJobService;
    }


//    @PostMapping
//    public ResponseEntity<IndexResponse> index(@Valid @RequestBody IndexRequest request) {
//
//        String repositoryRoot = pathResolver.resolve(request.repositoryId(), request.repositoryRoot());
//
//        int chunkCount = indexingService.indexRepository(repositoryRoot);
//
//        if (request.repositoryId() != null) {
//            registry.markIndexed(request.repositoryId());
//        }
//
//        return ResponseEntity.ok(new IndexResponse(repositoryRoot, chunkCount));
//    }

    /**
     * Queues an index. Returns 202 Accepted with a job id — not the result.
     *
     * 202 rather than 200 is the honest status: the work has been accepted, not completed.
     */
    @PostMapping
    public ResponseEntity<IndexJobResponse> index(@Valid @RequestBody IndexRequest request) {
        if (request.repositoryId() == null) {
            throw new IllegalArgumentException(
                    "repositoryId is required. Register the repository first.");
        }
        IndexJobEntity job = indexJobService.submit(request.repositoryId());
        return ResponseEntity.accepted().body(IndexJobResponse.from(job));
    }

    /** Poll this until status is SUCCEEDED or FAILED. */
    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<IndexJobResponse> jobStatus(@PathVariable UUID jobId) {
        return ResponseEntity.ok(IndexJobResponse.from(indexJobService.require(jobId)));
    }

}