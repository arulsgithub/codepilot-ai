package com.codepilot.indexing.controller;

import com.codepilot.indexing.dto.IndexRequest;
import com.codepilot.indexing.dto.IndexResponse;
import com.codepilot.indexing.service.RepositoryIndexingService;
import com.codepilot.repo.service.RepositoryPathResolver;
import com.codepilot.repo.service.RepositoryRegistryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/indexing")
public class IndexingController {

    private final RepositoryIndexingService indexingService;
    private final RepositoryPathResolver pathResolver;
    private final RepositoryRegistryService registry;

    public IndexingController(RepositoryIndexingService indexingService, RepositoryPathResolver pathResolver, RepositoryRegistryService registry) {
        this.indexingService = indexingService;
        this.pathResolver = pathResolver;
        this.registry = registry;
    }


    @PostMapping
    public ResponseEntity<IndexResponse> index(@Valid @RequestBody IndexRequest request) {

        String repositoryRoot = pathResolver.resolve(request.repositoryId(), request.repositoryRoot());

        int chunkCount = indexingService.indexRepository(repositoryRoot);

        if (request.repositoryId() != null) {
            registry.markIndexed(request.repositoryId());
        }

        return ResponseEntity.ok(new IndexResponse(repositoryRoot, chunkCount));
    }

}