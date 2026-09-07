-- pgvector extension: adds the "vector" column type and similarity operators (<=>, <#>, <->).
-- Safe to run even if another migration/tool already created it.
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE code_chunks (
    id                 UUID PRIMARY KEY,
    repository_root    VARCHAR(1024)  NOT NULL, -- which scanned repo this chunk belongs to
    relative_file_path VARCHAR(1024)  NOT NULL,
    qualified_name     VARCHAR(1024)  NOT NULL,
    content            TEXT           NOT NULL,
    start_line         INTEGER        NOT NULL,
    end_line           INTEGER        NOT NULL,
    chunk_index        INTEGER        NOT NULL,
    total_chunks       INTEGER        NOT NULL,
    embedding          vector(1024)   NOT NULL, -- must match EmbeddingClient.dimensions()
    created_at         TIMESTAMPTZ    NOT NULL
);

-- Re-indexing a repo should replace old chunks for the same file, not accumulate duplicates.
CREATE INDEX idx_code_chunks_repo_file ON code_chunks (repository_root, relative_file_path);

-- ivfflat: an approximate-nearest-neighbor index type pgvector provides - required for fast
-- similarity search once the table has more than a few thousand rows. "lists = 100" is a
-- reasonable starting value for a small/medium codebase; we can tune it later based on row count.
CREATE INDEX idx_code_chunks_embedding ON code_chunks
    USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);