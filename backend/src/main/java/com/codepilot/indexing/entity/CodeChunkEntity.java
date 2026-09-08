package com.codepilot.indexing.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "code_chunks")
@Getter
@Setter
@NoArgsConstructor
public class CodeChunkEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "repository_root", nullable = false, length = 1024)
    private String repositoryRoot;

    @Column(name = "relative_file_path", nullable = false, length = 1024)
    private String relativeFilePath;

    @Column(name = "qualified_name", nullable = false, length = 1024)
    private String qualifiedName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "start_line", nullable = false)
    private int startLine;

    @Column(name = "end_line", nullable = false)
    private int endLine;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "total_chunks", nullable = false)
    private int totalChunks;

    // Maps to Postgres pgvector's vector(1024). hibernate-vector handles float[] <-> vector;
    // without an explicit type Hibernate serializes the field as bytea and the insert fails.
    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 1024)
    @Column(nullable = false, columnDefinition = "vector(1024)")
    private float[] embedding;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}