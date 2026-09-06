package com.codepilot.ingestion.controller;

import com.codepilot.ingestion.dto.ScanRequest;
import com.codepilot.ingestion.dto.ScanResponse;
import com.codepilot.ingestion.dto.SourceFile;
import com.codepilot.ingestion.service.RepositoryIngestionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/v1/ingestion")
public class IngestionController {

    private final RepositoryIngestionService ingestionService;

    public IngestionController(RepositoryIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/scan")
    public ResponseEntity<ScanResponse> scan(@Valid @RequestBody ScanRequest request) {
        boolean includeTestSources = Boolean.TRUE.equals(request.includeTestSources());

        List<SourceFile> files = ingestionService.scan(
                Path.of(request.rootPath()),
                includeTestSources
        );

        return ResponseEntity.ok(new ScanResponse(request.rootPath(), files.size(), files));
    }
}