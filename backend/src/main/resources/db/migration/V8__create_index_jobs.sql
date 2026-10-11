-- An indexing job. Persisted rather than held in a map so that a restart mid-run leaves
-- an honest record ("RUNNING" with an old started_at) instead of the job vanishing and the
-- UI polling an id that no longer exists.
CREATE TABLE index_jobs (
    id              UUID         PRIMARY KEY,
    repository_id   UUID         NOT NULL REFERENCES repositories(id),
    user_id         UUID         NOT NULL REFERENCES users(id),
    status          VARCHAR(16)  NOT NULL,
    progress        VARCHAR(255),
    chunk_count     INTEGER,
    error_message   TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    started_at      TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ
);

-- Polling queries hit these constantly.
CREATE INDEX idx_index_jobs_repository ON index_jobs (repository_id, created_at DESC);
CREATE INDEX idx_index_jobs_user ON index_jobs (user_id, created_at DESC);