package com.codepilot.repo.controller;

import com.codepilot.repo.dto.RegisterGithubRequest;
import com.codepilot.repo.dto.RegisterLocalRequest;
import com.codepilot.repo.dto.RepositoryResponse;
import com.codepilot.repo.service.RepositoryRegistryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/repositories")
public class RepositoryController {

    private final RepositoryRegistryService registry;

    public RepositoryController(RepositoryRegistryService registry) {
        this.registry = registry;
    }

    /** Register a folder already on this machine. */
    @PostMapping("/local")
    public ResponseEntity<RepositoryResponse> registerLocal(@Valid @RequestBody RegisterLocalRequest request) {
        return ResponseEntity.ok(RepositoryResponse.from(
                registry.registerLocal(request.rootPath(), request.name())));
    }

    /** Clone a GitHub repository into the managed workspace and register it. */
    @PostMapping("/github")
    public ResponseEntity<RepositoryResponse> registerGithub(@Valid @RequestBody RegisterGithubRequest request) {
        return ResponseEntity.ok(RepositoryResponse.from(
                registry.registerGithub(request.remoteUrl(), request.branch(), request.name())));
    }

    /** Fetch the newest commits for a GitHub repository. Does not re-index. */
    @PostMapping("/{id}/sync")
    public ResponseEntity<RepositoryResponse> sync(@PathVariable UUID id) {
        return ResponseEntity.ok(RepositoryResponse.from(registry.sync(id)));
    }

    @GetMapping
    public ResponseEntity<List<RepositoryResponse>> list() {
        List<RepositoryResponse> body = registry.findAll().stream()
                .map(RepositoryResponse::from)
                .toList();
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RepositoryResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(RepositoryResponse.from(registry.require(id)));
    }
}