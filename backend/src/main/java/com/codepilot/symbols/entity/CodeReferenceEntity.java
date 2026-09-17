package com.codepilot.symbols.entity;

import com.codepilot.symbols.dto.ReferenceKind;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "code_references")
@Getter
@Setter
@NoArgsConstructor
public class CodeReferenceEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "repository_root", nullable = false, length = 1024)
    private String repositoryRoot;

    @Column(name = "relative_file_path", nullable = false, length = 1024)
    private String relativeFilePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_kind", nullable = false, length = 32)
    private ReferenceKind referenceKind;

    /** Null when the symbol solver could not resolve the target - see `resolved`. */
    @Column(name = "target_qualified_name", length = 1024)
    private String targetQualifiedName;

    @Column(name = "target_simple_name", nullable = false, length = 512)
    private String targetSimpleName;

    /** The method/constructor/type that contains this reference - i.e. the caller. */
    @Column(name = "from_qualified_name", length = 1024)
    private String fromQualifiedName;

    /**
     * false means we only have a name, not a resolved declaring type. Such a reference may be a
     * false positive (same method name on a different class). Callers MUST surface this rather
     * than presenting name-only matches as certain.
     */
    @Column(nullable = false)
    private boolean resolved;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}