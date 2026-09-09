/**
 * Contracts for `POST /api/v1/indexing` — scanning a local folder, parsing
 * its Java files, embedding them and storing the chunks in pgvector so chat
 * turns can be grounded in that repository.
 */

/** Request body for `POST /api/v1/indexing`. */
export interface IndexRepositoryRequest {
  /** Absolute path to the repository root on the machine running the backend. */
  repositoryRoot: string;
}

/** Response body for `POST /api/v1/indexing`. */
export interface IndexRepositoryResponse {
  repositoryRoot: string;
  /** How many code chunks were embedded and stored for this path. */
  chunksIndexed: number;
}

/**
 * UI-only lifecycle of an indexing request.
 *  - `idle`     — nothing attached, or a previous attempt was dismissed
 *  - `indexing` — request in flight (can take ~30-90s); UI must stay live
 *  - `success`  — chunks stored; the path is now the attached repository
 *  - `error`    — request failed; the previous attachment (if any) is kept
 */
export type IndexingStatus = 'idle' | 'indexing' | 'success' | 'error';
