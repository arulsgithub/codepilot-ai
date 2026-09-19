package com.codepilot.retrieval.service;

import com.codepilot.indexing.entity.CodeChunkEntity;
import com.codepilot.indexing.repository.CodeChunkRepository;
import com.codepilot.retrieval.dto.CallSite;
import com.codepilot.retrieval.dto.EnrichedContext;
import com.codepilot.symbols.entity.CodeReferenceEntity;
import com.codepilot.symbols.repository.CodeReferenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Combines semantic retrieval with the symbol index.
 *
 * Semantic search answers "what code is this instruction ABOUT?" - excellent for finding the
 * method to change, useless as a guarantee that every caller was found. The symbol index answers
 * "what code TOUCHES this?" exactly. Editing needs both: change a signature without its call
 * sites in context and the model produces a plan that compiles in one file and breaks three others.
 */
@Service
public class SymbolAwareRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(SymbolAwareRetrievalService.class);

    private final RetrievalService retrievalService;
    private final CodeReferenceRepository referenceRepository;
    private final CodeChunkRepository codeChunkRepository;

    public SymbolAwareRetrievalService(RetrievalService retrievalService,
                                       CodeReferenceRepository referenceRepository,
                                       CodeChunkRepository codeChunkRepository) {
        this.retrievalService = retrievalService;
        this.referenceRepository = referenceRepository;
        this.codeChunkRepository = codeChunkRepository;
    }

    /**
     * @param seedTopK           how many chunks semantic search contributes
     * @param maxCallSiteChunks  cap on added call-site chunks - each one costs prompt budget,
     *                           and a widely-used utility method can have dozens of callers
     */
    @Transactional(readOnly = true)
    public EnrichedContext retrieveWithCallSites(String repositoryRoot, String instruction,
                                                 int seedTopK, int maxCallSiteChunks) {

        List<CodeChunkEntity> seed =
                retrievalService.retrieveRelevantChunks(instruction, repositoryRoot, seedTopK);

        if (seed.isEmpty()) {
            return new EnrichedContext(List.of(), List.of(), List.of());
        }

        // Only Java chunks have real symbol names. Non-Java chunks use their file path as the
        // qualified name (see TextFileParser), which would produce nonsense lookups.
        Set<String> seedNames = new LinkedHashSet<>();
        for (CodeChunkEntity chunk : seed) {
            String name = chunk.getQualifiedName();
            if (name != null && !name.contains("/") && !name.contains("\\")) {
                seedNames.add(name);
            }
        }
        if (seedNames.isEmpty()) {
            return new EnrichedContext(seed, List.of(), List.of());
        }

        // --- confirmed callers: the solver proved these point at a seed symbol ---
        List<CodeReferenceEntity> confirmed =
                referenceRepository.findByTargetQualifiedNameIn(repositoryRoot, seedNames);

        // --- possible callers: name matches but resolution failed ---
        Set<String> simpleNames = new LinkedHashSet<>();
        for (String name : seedNames) {
            String simple = simpleNameOf(name);
            if (simple != null && !simple.isBlank()) {
                simpleNames.add(simple);
            }
        }
        List<CodeReferenceEntity> possible = simpleNames.isEmpty()
                ? List.of()
                : referenceRepository.findUnresolvedByTargetSimpleNameIn(repositoryRoot, simpleNames);

        // --- build the call-site list, deduped ---
        List<CallSite> callSites = new ArrayList<>();
        Set<String> seenSites = new HashSet<>();
        Set<String> callerNames = new LinkedHashSet<>();

        collect(confirmed, true, seed, seedNames, callSites, seenSites, callerNames);
        collect(possible, false, seed, seedNames, callSites, seenSites, callerNames);

        if (callerNames.isEmpty()) {
            return new EnrichedContext(seed, List.of(), callSites);
        }

        // --- fetch the calling methods' own code ---
        Set<UUID> seedIds = new HashSet<>();
        seed.forEach(c -> seedIds.add(c.getId()));

        List<CodeChunkEntity> callSiteChunks = codeChunkRepository
                .findByQualifiedNameIn(repositoryRoot, callerNames)
                .stream()
                .filter(c -> !seedIds.contains(c.getId())) // already in the seed set
                .limit(maxCallSiteChunks)
                .toList();

        log.info("Symbol-aware retrieval: {} seed chunk(s), {} call site(s) "
                        + "({} confirmed), {} extra chunk(s) added",
                seed.size(), callSites.size(),
                callSites.stream().filter(CallSite::confirmed).count(), callSiteChunks.size());

        return new EnrichedContext(seed, callSiteChunks, callSites);
    }

    private void collect(List<CodeReferenceEntity> references, boolean confirmed,
                         List<CodeChunkEntity> seed, Set<String> seedNames,
                         List<CallSite> callSites, Set<String> seenSites, Set<String> callerNames) {

        for (CodeReferenceEntity reference : references) {
            String caller = reference.getFromQualifiedName();
            if (caller == null) {
                continue;
            }
            // A seed symbol calling itself or a sibling already in the seed adds nothing.
            if (seedNames.contains(caller)) {
                continue;
            }

            String key = caller + "@" + reference.getRelativeFilePath() + ":" + reference.getLineNumber();
            if (!seenSites.add(key)) {
                continue;
            }

            callSites.add(new CallSite(caller, reference.getRelativeFilePath(),
                    reference.getLineNumber(), reference.getTargetQualifiedName(),
                    reference.getTargetSimpleName(), confirmed));
            callerNames.add(caller);
        }
    }

    /** "com.foo.Bar#baz" -> "baz"; "com.foo.Bar" -> "Bar". */
    private String simpleNameOf(String qualifiedName) {
        int hash = qualifiedName.lastIndexOf('#');
        if (hash >= 0) {
            return qualifiedName.substring(hash + 1);
        }
        int dot = qualifiedName.lastIndexOf('.');
        return dot >= 0 ? qualifiedName.substring(dot + 1) : qualifiedName;
    }
}