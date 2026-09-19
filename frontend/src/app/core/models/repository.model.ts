/**
 * Contracts for the repository registry endpoints:
 *  - `GET  /api/v1/repositories`, `GET /api/v1/repositories/{id}`
 *  - `POST /api/v1/repositories/local`   — register a folder already on the backend machine
 *  - `POST /api/v1/repositories/github`  — clone a GitHub repo server-side (10-60s)
 *  - `POST /api/v1/repositories/{id}/sync` — fetch new commits (does NOT re-index)
 *
 * These mirror the Java records in `com.codepilot.repo.dto` 1:1.
 */

export type RepositorySourceType = 'LOCAL' | 'GITHUB';

/** A registered repository as returned by the backend (`RepositoryResponse`). */
export interface Repository {
  id: string;
  name: string;
  sourceType: RepositorySourceType;
  remoteUrl: string | null;
  branch: string | null;
  /** Canonical absolute path on the machine running the backend. */
  localPath: string;
  lastSyncedCommit: string | null;
  /** ISO-8601, or null if never synced (always null for LOCAL repositories). */
  lastSyncedAt: string | null;
  /** ISO-8601, or null if never indexed. */
  lastIndexedAt: string | null;
  /**
   * True when the index is missing or older than the last sync. A stale index
   * makes RAG answers confidently describe OLD code, so the UI flags it loudly.
   */
  indexStale: boolean;
}

/** Request body for `POST /api/v1/repositories/local`. */
export interface RegisterLocalRepositoryRequest {
  rootPath: string;
  name?: string;
}

/** Request body for `POST /api/v1/repositories/github`. */
export interface RegisterGithubRepositoryRequest {
  /** Must start with `https://` — the backend rejects anything else. */
  remoteUrl: string;
  branch?: string;
  name?: string;
}

/**
 * UI lifecycle of one async action: nothing running, running, or failed.
 * Every repository operation exposes one of these so no failure is silent.
 */
export type OperationStatus = 'idle' | 'in-progress' | 'error';

/** An error tied to a specific repository (sync / index failures). */
export interface RepositoryOperationError {
  repositoryId: string;
  message: string;
}

/** Outcome of the most recent successful indexing run. */
export interface IndexResult {
  repositoryId: string;
  chunksIndexed: number;
}

/**
 * What an index-stale badge should say. A repository that has NEVER been
 * indexed is a different situation from one whose index has merely aged, and
 * the UI says so rather than calling both "stale".
 */
export function staleLabel(repository: Repository): string {
  return repository.lastIndexedAt === null ? 'not indexed' : 'stale';
}
