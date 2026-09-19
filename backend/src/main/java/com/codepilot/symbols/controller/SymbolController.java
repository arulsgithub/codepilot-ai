package com.codepilot.symbols.controller;

import com.codepilot.repo.service.RepositoryPathResolver;
import com.codepilot.symbols.dto.FileQueryRequest;
import com.codepilot.symbols.dto.ImpactRequest;
import com.codepilot.symbols.dto.SymbolQueryRequest;
import com.codepilot.symbols.dto.SymbolQueryResponses.DependenciesResponse;
import com.codepilot.symbols.dto.SymbolQueryResponses.ImpactResponse;
import com.codepilot.symbols.dto.SymbolQueryResponses.UsagesResponse;
import com.codepilot.symbols.service.SymbolQueryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST rather than GET for all three: the inputs are Windows paths and qualified names full of
 * backslashes, dots and '#', which are awkward and error-prone as query parameters.
 *
 * Each method now resolves the repository before querying. The resolver accepts either a
 * repositoryId (registered repo, works for GitHub clones) or the original repositoryRoot
 * path, and always returns the SAME canonical spelling that indexing wrote into
 * code_symbols.repository_root - which is what makes the string equality in those queries
 * actually match.
 */
@RestController
@RequestMapping("/api/v1/symbols")
public class SymbolController {

    private final SymbolQueryService symbolQueryService;
    private final RepositoryPathResolver pathResolver;

    public SymbolController(SymbolQueryService symbolQueryService,
                            RepositoryPathResolver pathResolver) {
        this.symbolQueryService = symbolQueryService;
        this.pathResolver = pathResolver;
    }

    /** "Where is this called from?" */
    @PostMapping("/usages")
    public ResponseEntity<UsagesResponse> usages(@Valid @RequestBody SymbolQueryRequest request) {
        String repositoryRoot = pathResolver.resolve(request.repositoryId(), request.repositoryRoot());
        return ResponseEntity.ok(
                symbolQueryService.findUsages(repositoryRoot, request.symbol()));
    }

    /** "What does this file depend on?" */
    @PostMapping("/dependencies")
    public ResponseEntity<DependenciesResponse> dependencies(@Valid @RequestBody FileQueryRequest request) {
        String repositoryRoot = pathResolver.resolve(request.repositoryId(), request.repositoryRoot());
        return ResponseEntity.ok(
                symbolQueryService.findDependencies(repositoryRoot, request.relativeFilePath()));
    }

    /** "What breaks if I change this?" */
    @PostMapping("/impact")
    public ResponseEntity<ImpactResponse> impact(@Valid @RequestBody ImpactRequest request) {
        String repositoryRoot = pathResolver.resolve(request.repositoryId(), request.repositoryRoot());
        return ResponseEntity.ok(symbolQueryService.findImpact(
                repositoryRoot, request.symbol(), request.maxDepth()));
    }
}