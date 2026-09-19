package com.codepilot.symbols.dto;

import java.util.List;

/**
 * Response shapes for the three symbol queries, grouped in one file because they share the same
 * vocabulary (SymbolMatch, ReferenceHit) and are always read together.
 *
 * The recurring design point: CONFIRMED and POSSIBLE results are never merged into one list.
 * Roughly a third of extracted references are name-only, and presenting those as certain would
 * make "find usages" quietly untrustworthy - which is the exact failure this feature exists to fix.
 */
public final class SymbolQueryResponses {

    private SymbolQueryResponses() {
    }

    /** One declaration in the codebase. */
    public record SymbolMatch(
            SymbolKind kind,
            String qualifiedName,
            String simpleName,
            String signature,
            String relativeFilePath,
            int startLine,
            int endLine
    ) {
    }

    /** One place where something is used. */
    public record ReferenceHit(
            ReferenceKind kind,
            String relativeFilePath,
            int lineNumber,
            String fromQualifiedName,   // the member containing this usage - i.e. the caller
            String targetQualifiedName, // null when unresolved
            String targetSimpleName
    ) {
    }

    /** Result of "where is this used?". */
    public record UsagesResponse(
            String status,              // FOUND | AMBIGUOUS | NOT_FOUND
            String message,
            SymbolMatch symbol,         // null unless status == FOUND
            List<SymbolMatch> candidates, // populated when status == AMBIGUOUS
            List<ReferenceHit> confirmed, // resolved to exactly this symbol
            List<ReferenceHit> possible,  // name matches only - may include false positives
            int confirmedCount,
            int possibleCount
    ) {
    }

    /** One thing a file depends on. */
    public record Dependency(
            String targetQualifiedName,
            String targetSimpleName,
            ReferenceKind kind,
            boolean internal,   // true = declared in this repository; false = library/JDK
            boolean resolved,
            int occurrences,
            int firstLine
    ) {
    }

    /** Result of "what does this file use?". */
    public record DependenciesResponse(
            String relativeFilePath,
            List<SymbolMatch> declares,     // what this file itself provides
            List<Dependency> internalDeps,  // other code in your repository
            List<Dependency> externalDeps,  // libraries, JDK, or unresolved
            int internalCount,
            int externalCount
    ) {
    }

    /** One member reached by impact analysis. */
    public record ImpactedMember(
            String qualifiedName,
            String relativeFilePath,
            int depth,          // 1 = calls the symbol directly, 2 = calls a caller, ...
            String reachedVia   // which member at the previous depth led here
    ) {
    }

    /** Result of "what breaks if I change this?". */
    public record ImpactResponse(
            String status,                   // FOUND | AMBIGUOUS | NOT_FOUND
            String message,
            SymbolMatch symbol,
            List<SymbolMatch> candidates,
            List<ImpactedMember> impacted,
            List<String> impactedFiles,
            int maxDepthReached,
            boolean truncated,               // true = the traversal hit the depth limit
            String caveat
    ) {
    }
}