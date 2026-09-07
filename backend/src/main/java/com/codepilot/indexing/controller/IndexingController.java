package com.codepilot.indexing.controller;

import com.codepilot.indexing.dto.IndexRequest;
import com.codepilot.indexing.dto.IndexResponse;
import com.codepilot.indexing.service.RepositoryIndexingService;
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

    public IndexingController(RepositoryIndexingService indexingService) {
        this.indexingService = indexingService;
    }

    @PostMapping
    public ResponseEntity<IndexResponse> index(@Valid @RequestBody IndexRequest request) {
        int chunksIndexed = indexingService.indexRepository(request.repositoryRoot());
        return ResponseEntity.ok(new IndexResponse(request.repositoryRoot(), chunksIndexed));
    }
}