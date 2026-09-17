package com.codepilot.symbols.dto;

import java.util.List;

public record FileSymbols(
        List<ExtractedSymbol> symbols,
        List<ExtractedReference> references
) {

    public record ExtractedSymbol(
            SymbolKind kind,
            String qualifiedName,
            String simpleName,
            String signature,
            int startLine,
            int endLine
    ) {
    }

    public record ExtractedReference(
            ReferenceKind kind,
            String targetQualifiedName,  // null when unresolved
            String targetSimpleName,
            String fromQualifiedName,
            boolean resolved,
            int lineNumber
    ) {
    }
}