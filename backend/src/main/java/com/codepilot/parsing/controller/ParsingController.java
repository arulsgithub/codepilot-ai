package com.codepilot.parsing.controller;

import com.codepilot.ingestion.dto.SourceFile;
import com.codepilot.ingestion.service.RepositoryIngestionService;
import com.codepilot.parsing.dto.CodeUnit;
import com.codepilot.parsing.dto.ParseRequest;
import com.codepilot.parsing.dto.ParseResponse;
import com.codepilot.parsing.service.SourceFileParsingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/v1/parsing")
public class ParsingController {

    private final RepositoryIngestionService ingestionService;
    private final SourceFileParsingService parsingService;

    public ParsingController(RepositoryIngestionService ingestionService,
                             SourceFileParsingService parsingService) {
        this.ingestionService = ingestionService;
        this.parsingService = parsingService;
    }

    /**
     * Convenience endpoint that chains ingestion -> parsing so we can eyeball extracted
     * CodeUnits end-to-end. Once embeddings/pgvector exist (step 3+), this pipeline moves
     * into a single "index this repo" service instead of being called from a controller.
     */
    @PostMapping("/preview")
    public ResponseEntity<ParseResponse> preview(@Valid @RequestBody ParseRequest request) {
        List<SourceFile> sourceFiles = ingestionService.scan(Path.of(request.rootPath()), false);
        List<CodeUnit> units = parsingService.parseAll(sourceFiles);
        return ResponseEntity.ok(new ParseResponse(request.rootPath(), units.size(), units));
    }
}