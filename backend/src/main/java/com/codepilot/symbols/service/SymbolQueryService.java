package com.codepilot.symbols.service;

import com.codepilot.symbols.dto.SymbolQueryResponses.*;
import com.codepilot.symbols.entity.CodeReferenceEntity;
import com.codepilot.symbols.entity.CodeSymbolEntity;
import com.codepilot.symbols.repository.CodeReferenceRepository;
import com.codepilot.symbols.repository.CodeSymbolRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class SymbolQueryService {

    private static final int DEFAULT_MAX_DEPTH = 3;

    /** Guard against a pathological fan-out consuming the whole heap. */
    private static final int MAX_IMPACTED_MEMBERS = 500;

    private final CodeSymbolRepository symbolRepository;
    private final CodeReferenceRepository referenceRepository;

    public SymbolQueryService(CodeSymbolRepository symbolRepository,
                              CodeReferenceRepository referenceRepository) {
        this.symbolRepository = symbolRepository;
        this.referenceRepository = referenceRepository;
    }

    // ------------------------------------------------------------------
    // Symbol lookup
    // ------------------------------------------------------------------

    /**
     * Result of turning user input into a concrete symbol.
     *
     * `overloads` matters because qualified names are built as Type#memberName with NO parameter
     * types (see JavaNames). Two overloads of the same method therefore share one qualified name.
     * They are not an ambiguity to resolve - they are one address with several declarations, and
     * the reference table cannot tell them apart either, because it stores the same param-less
     * name as its target.
     */
    private record Lookup(CodeSymbolEntity match, int overloads, List<CodeSymbolEntity> candidates) {
        boolean found() { return match != null; }
        boolean ambiguous() { return match == null && !candidates.isEmpty(); }
    }

    /**
     * Three passes, most precise first. We stop at the first pass that yields anything, so a
     * request that names a symbol exactly is never diluted by looser matches elsewhere.
     */
    private Lookup lookup(String repositoryRoot, String input) {
        String query = input.trim();

        // 1. Exact qualified name.
        List<CodeSymbolEntity> exact =
                symbolRepository.findByRepositoryRootAndQualifiedName(repositoryRoot, query);
        if (!exact.isEmpty()) {
            return new Lookup(exact.getFirst(), exact.size(), List.of());
        }

        // 2. Suffix, e.g. "ChatService#chat". LIKE is coarse, so filter for a real name boundary:
        //    the character before the match must be '.' or '#', otherwise "chat" would also match
        //    "...#prechat".
        List<CodeSymbolEntity> suffix = symbolRepository
                .findByQualifiedNameLike(repositoryRoot, "%" + query)
                .stream()
                .filter(s -> isNameBoundary(s.getQualifiedName(), query))
                .toList();
        if (!suffix.isEmpty()) {
            return collapse(suffix);
        }

        // 3. Bare simple name.
        List<CodeSymbolEntity> simple =
                symbolRepository.findByRepositoryRootAndSimpleName(repositoryRoot, query);
        return collapse(simple);
    }

    /**
     * Turns a candidate list into a decision.
     *
     * Group by qualified name first: several rows sharing one name are overloads of a single
     * method, which is a match, not an ambiguity. Only genuinely DIFFERENT qualified names mean
     * the user has to be more specific.
     */
    private Lookup collapse(List<CodeSymbolEntity> candidates) {
        if (candidates.isEmpty()) {
            return new Lookup(null, 0, List.of());
        }

        Map<String, List<CodeSymbolEntity>> byQualifiedName = new LinkedHashMap<>();
        for (CodeSymbolEntity candidate : candidates) {
            byQualifiedName
                    .computeIfAbsent(candidate.getQualifiedName(), k -> new ArrayList<>())
                    .add(candidate);
        }

        if (byQualifiedName.size() == 1) {
            List<CodeSymbolEntity> group = byQualifiedName.values().iterator().next();
            return new Lookup(group.getFirst(), group.size(), List.of());
        }

        // Several distinct names - show one representative per name so the candidate list stays
        // readable instead of repeating every overload of every match.
        List<CodeSymbolEntity> representatives = byQualifiedName.values().stream()
                .map(List::getFirst)
                .toList();
        return new Lookup(null, 0, representatives);
    }

    private boolean isNameBoundary(String qualifiedName, String query) {
        if (qualifiedName.equals(query)) {
            return true;
        }
        int at = qualifiedName.length() - query.length();
        if (at <= 0) {
            return false;
        }
        char before = qualifiedName.charAt(at - 1);
        return before == '.' || before == '#';
    }

    /** Appended to messages when a match covers more than one declaration. */
    private String overloadNote(int overloads) {
        return overloads > 1
                ? " (" + overloads + " overloads share this name; usages cannot be attributed to a"
                + " specific one, because references are recorded without parameter types)"
                : "";
    }

    // ------------------------------------------------------------------
    // 1. Usages
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public UsagesResponse findUsages(String repositoryRoot, String symbolQuery) {

        Lookup lookup = lookup(repositoryRoot, symbolQuery);

        if (lookup.ambiguous()) {
            return new UsagesResponse("AMBIGUOUS",
                    "'" + symbolQuery + "' matches " + lookup.candidates().size()
                            + " different symbols. Use a more specific name from the candidates.",
                    null, toMatches(lookup.candidates()), List.of(), List.of(), 0, 0);
        }
        if (!lookup.found()) {
            return new UsagesResponse("NOT_FOUND",
                    "No symbol named '" + symbolQuery + "' in this repository. "
                            + "Has it been indexed?",
                    null, List.of(), List.of(), List.of(), 0, 0);
        }

        CodeSymbolEntity symbol = lookup.match();

        // Confirmed: the symbol solver resolved these to exactly this declaration.
        List<ReferenceHit> confirmed = referenceRepository
                .findByRepositoryRootAndTargetQualifiedName(repositoryRoot, symbol.getQualifiedName())
                .stream().map(this::toHit).toList();

        // Possible: the name matches but resolution failed, so we cannot prove they point here.
        // References that resolved to a DIFFERENT target are excluded - those are known negatives,
        // not uncertainty.
        List<ReferenceHit> possible = referenceRepository
                .findByRepositoryRootAndTargetSimpleName(repositoryRoot, symbol.getSimpleName())
                .stream()
                .filter(r -> !r.isResolved())
                .map(this::toHit)
                .toList();

        String message = confirmed.size() + " confirmed usage(s)"
                + (possible.isEmpty() ? "" : ", plus " + possible.size()
                + " possible match(es) that could not be resolved precisely")
                + overloadNote(lookup.overloads());

        return new UsagesResponse("FOUND", message, toMatch(symbol), List.of(),
                confirmed, possible, confirmed.size(), possible.size());
    }

    // ------------------------------------------------------------------
    // 2. Dependencies
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public DependenciesResponse findDependencies(String repositoryRoot, String relativeFilePath) {

        List<CodeSymbolEntity> declares = symbolRepository
                .findByRepositoryRootAndRelativeFilePath(repositoryRoot, relativeFilePath);

        List<CodeReferenceEntity> references = referenceRepository
                .findByRepositoryRootAndRelativeFilePath(repositoryRoot, relativeFilePath);

        // Collapse repeats: a file calling the same method eight times is ONE dependency with a
        // count of eight, not eight separate findings.
        Map<String, List<CodeReferenceEntity>> grouped = new LinkedHashMap<>();
        for (CodeReferenceEntity reference : references) {
            String key = reference.getTargetQualifiedName() != null
                    ? reference.getTargetQualifiedName()
                    : "~unresolved~" + reference.getTargetSimpleName();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(reference);
        }

        // One batched query decides internal vs external, instead of one lookup per dependency.
        Set<String> resolvedTargets = new HashSet<>();
        for (CodeReferenceEntity reference : references) {
            if (reference.getTargetQualifiedName() != null) {
                resolvedTargets.add(reference.getTargetQualifiedName());
            }
        }
        Set<String> internalNames = resolvedTargets.isEmpty()
                ? Set.of()
                : new HashSet<>(symbolRepository.findExistingQualifiedNames(repositoryRoot, resolvedTargets));

        Set<String> declaredHere = new HashSet<>();
        declares.forEach(d -> declaredHere.add(d.getQualifiedName()));

        List<Dependency> internal = new ArrayList<>();
        List<Dependency> external = new ArrayList<>();

        for (List<CodeReferenceEntity> group : grouped.values()) {
            CodeReferenceEntity first = group.getFirst();

            // Self-references are noise here - a file obviously uses its own members.
            if (first.getTargetQualifiedName() != null
                    && declaredHere.contains(first.getTargetQualifiedName())) {
                continue;
            }

            boolean isInternal = first.getTargetQualifiedName() != null
                    && internalNames.contains(first.getTargetQualifiedName());

            Dependency dependency = new Dependency(
                    first.getTargetQualifiedName(),
                    first.getTargetSimpleName(),
                    first.getReferenceKind(),
                    isInternal,
                    first.isResolved(),
                    group.size(),
                    group.stream().mapToInt(CodeReferenceEntity::getLineNumber).min().orElse(0));

            if (isInternal) {
                internal.add(dependency);
            } else {
                external.add(dependency);
            }
        }

        internal.sort(Comparator.comparing(Dependency::targetQualifiedName));
        external.sort(Comparator.comparing(Dependency::targetSimpleName));

        return new DependenciesResponse(relativeFilePath, toMatches(declares),
                internal, external, internal.size(), external.size());
    }

    // ------------------------------------------------------------------
    // 3. Impact analysis
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ImpactResponse findImpact(String repositoryRoot, String symbolQuery, Integer requestedDepth) {

        int maxDepth = requestedDepth == null ? DEFAULT_MAX_DEPTH : requestedDepth;
        Lookup lookup = lookup(repositoryRoot, symbolQuery);

        if (lookup.ambiguous()) {
            return new ImpactResponse("AMBIGUOUS",
                    "'" + symbolQuery + "' matches " + lookup.candidates().size()
                            + " different symbols.",
                    null, toMatches(lookup.candidates()), List.of(), List.of(), 0, false, null);
        }
        if (!lookup.found()) {
            return new ImpactResponse("NOT_FOUND",
                    "No symbol named '" + symbolQuery + "' in this repository.",
                    null, List.of(), List.of(), List.of(), 0, false, null);
        }

        CodeSymbolEntity symbol = lookup.match();

        // Breadth-first walk up the call chain. `visited` is not an optimisation - call graphs
        // contain cycles (A calls B, B calls A), and without it this loops forever.
        Set<String> visited = new HashSet<>();
        visited.add(symbol.getQualifiedName());

        List<ImpactedMember> impacted = new ArrayList<>();
        Set<String> impactedFiles = new LinkedHashSet<>();
        Set<String> frontier = Set.of(symbol.getQualifiedName());

        int depth = 0;
        boolean truncated = false;

        while (!frontier.isEmpty() && depth < maxDepth) {
            depth++;

            List<CodeReferenceEntity> incoming =
                    referenceRepository.findByTargetQualifiedNameIn(repositoryRoot, frontier);

            Set<String> nextFrontier = new LinkedHashSet<>();
            for (CodeReferenceEntity reference : incoming) {
                String caller = reference.getFromQualifiedName();
                if (caller == null || !visited.add(caller)) {
                    continue; // already seen at an equal or shallower depth
                }
                if (impacted.size() >= MAX_IMPACTED_MEMBERS) {
                    truncated = true;
                    break;
                }
                impacted.add(new ImpactedMember(caller, reference.getRelativeFilePath(),
                        depth, reference.getTargetQualifiedName()));
                impactedFiles.add(reference.getRelativeFilePath());
                nextFrontier.add(caller);
            }

            if (truncated) {
                break;
            }
            frontier = nextFrontier;
        }

        // Distinguish "nothing else calls these" from "we stopped looking".
        if (!frontier.isEmpty() && depth >= maxDepth) {
            truncated = true;
        }

        String caveat = "Only resolved references are followed. About a third of references in a "
                + "typical Spring project cannot be resolved precisely, so treat this as a strong "
                + "starting point, not a complete list."
                + overloadNote(lookup.overloads());

        String message = impacted.size() + " member(s) across " + impactedFiles.size()
                + " file(s) would be affected, within " + depth + " level(s)"
                + (truncated ? " - stopped early, there is more beyond this depth" : "");

        return new ImpactResponse("FOUND", message, toMatch(symbol), List.of(),
                impacted, new ArrayList<>(impactedFiles), depth, truncated, caveat);
    }

    // ------------------------------------------------------------------
    // mapping
    // ------------------------------------------------------------------

    private SymbolMatch toMatch(CodeSymbolEntity s) {
        return new SymbolMatch(s.getSymbolKind(), s.getQualifiedName(), s.getSimpleName(),
                s.getSignature(), s.getRelativeFilePath(), s.getStartLine(), s.getEndLine());
    }

    private List<SymbolMatch> toMatches(List<CodeSymbolEntity> symbols) {
        return symbols.stream().map(this::toMatch).toList();
    }

    private ReferenceHit toHit(CodeReferenceEntity r) {
        return new ReferenceHit(r.getReferenceKind(), r.getRelativeFilePath(), r.getLineNumber(),
                r.getFromQualifiedName(), r.getTargetQualifiedName(), r.getTargetSimpleName());
    }
}