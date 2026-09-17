package com.codepilot.symbols.entity;

import com.codepilot.symbols.dto.SymbolKind;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "code_symbols")
@Getter
@Setter
@NoArgsConstructor
public class CodeSymbolEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "repository_root", nullable = false, length = 1024)
    private String repositoryRoot;

    @Column(name = "relative_file_path", nullable = false, length = 1024)
    private String relativeFilePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "symbol_kind", nullable = false, length = 32)
    private SymbolKind symbolKind;

    @Column(name = "qualified_name", nullable = false, length = 1024)
    private String qualifiedName;

    @Column(name = "simple_name", nullable = false, length = 512)
    private String simpleName;

    @Column(columnDefinition = "TEXT")
    private String signature;

    @Column(name = "start_line", nullable = false)
    private int startLine;

    @Column(name = "end_line", nullable = false)
    private int endLine;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}