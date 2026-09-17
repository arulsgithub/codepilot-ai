-- What the code DECLARES.
CREATE TABLE code_symbols (
    id                 UUID PRIMARY KEY,
    repository_root    VARCHAR(1024) NOT NULL,
    relative_file_path VARCHAR(1024) NOT NULL,
    symbol_kind        VARCHAR(32)   NOT NULL,   -- CLASS / INTERFACE / ENUM / METHOD / CONSTRUCTOR / FIELD
    qualified_name     VARCHAR(1024) NOT NULL,   -- com.codepilot.chat.service.ChatService#chat
    simple_name        VARCHAR(512)  NOT NULL,   -- chat
    signature          TEXT,
    start_line         INTEGER       NOT NULL,
    end_line           INTEGER       NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL
);

-- Lookup by exact qualified name is the precise path ("usages of THIS method").
CREATE INDEX idx_code_symbols_repo_qname ON code_symbols (repository_root, qualified_name);
-- Lookup by simple name is the fallback path, used when resolution failed.
CREATE INDEX idx_code_symbols_repo_sname ON code_symbols (repository_root, simple_name);
-- Used to delete/refresh one file's symbols after an edit.
CREATE INDEX idx_code_symbols_repo_file  ON code_symbols (repository_root, relative_file_path);

-- What the code USES.
CREATE TABLE code_references (
    id                    UUID PRIMARY KEY,
    repository_root       VARCHAR(1024) NOT NULL,
    relative_file_path    VARCHAR(1024) NOT NULL,
    reference_kind        VARCHAR(32)   NOT NULL,  -- METHOD_CALL / CONSTRUCTOR_CALL / TYPE_USE
    target_qualified_name VARCHAR(1024),           -- NULL when resolution failed
    target_simple_name    VARCHAR(512)  NOT NULL,  -- always present
    from_qualified_name   VARCHAR(1024),           -- the member containing this reference
    resolved              BOOLEAN       NOT NULL,  -- false = name-only match, may be imprecise
    line_number           INTEGER       NOT NULL,
    created_at            TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_code_refs_repo_target ON code_references (repository_root, target_qualified_name);
CREATE INDEX idx_code_refs_repo_simple ON code_references (repository_root, target_simple_name);
CREATE INDEX idx_code_refs_repo_file   ON code_references (repository_root, relative_file_path);