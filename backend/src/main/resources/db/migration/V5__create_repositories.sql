-- A repository is "a body of code CodePilot knows about".
-- It can come from two places:
--   LOCAL  - a folder already on the machine (what we have been doing all along)
--   GITHUB - a remote we clone into a managed workspace directory
--
-- local_path is the bridge to everything we already built: code_chunks.repository_root
-- and code_symbols.repository_root are keyed on this exact string, so indexing,
-- retrieval and symbol analysis need no schema change whatsoever.

CREATE TABLE repositories (
    id                  UUID           PRIMARY KEY,
    name                VARCHAR(512)   NOT NULL,
    source_type         VARCHAR(16)    NOT NULL,
    remote_url          VARCHAR(1024),
    branch              VARCHAR(255),
    local_path          VARCHAR(1024)  NOT NULL,
    last_synced_commit  VARCHAR(64),
    last_synced_at      TIMESTAMPTZ,
    last_indexed_at     TIMESTAMPTZ,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

-- One row per path. Registering the same folder twice is a mistake, not a feature:
-- two rows pointing at one path would fight over the same chunks and symbols.
CREATE UNIQUE INDEX idx_repositories_local_path ON repositories (local_path);

-- Fast lookup when someone registers a GitHub repo that is already cloned.
CREATE INDEX idx_repositories_remote_url ON repositories (remote_url)
    WHERE remote_url IS NOT NULL;