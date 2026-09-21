/**
 * Contracts for the backend code-editing capability:
 *  - `POST /api/v1/edits/plan`  — reasons about an instruction and returns a
 *    reviewable set of diffs. Writes NOTHING to disk. Slow (10–40s).
 *  - `POST /api/v1/edits/apply` — delivers the approved edits atomically
 *    (all-or-nothing): a LOCAL repository is written in place and re-indexed;
 *    a GITHUB one gets a branch, a commit and a pull request instead.
 *
 * These mirror the Java records in `com.codepilot.edit.dto` 1:1. Nothing here
 * holds application state — the edit lifecycle lives in
 * features/chat/services/edit-state.service.ts.
 */

import { RepositorySourceType } from './repository.model';

/** Request body for `POST /api/v1/edits/plan`. */
export interface EditPlanRequest {
  /** Absolute path of an already-indexed repository on the backend machine. */
  repositoryRoot: string;
  /** Natural-language change request, e.g. "add null-checking to ChatService.chat". */
  instruction: string;
}

/**
 * One proposed file change.
 *
 * A VALID preview carries a `unifiedDiff` to render and a null `problem`.
 * An INVALID preview carries a human-readable `problem`, a null `unifiedDiff`,
 * and must be shown as blocked — there is no diff to display. If ANY preview
 * in a plan is invalid the whole plan's {@link EditPlanResponse.applicable} is
 * false and Apply must not be offered (apply is all-or-nothing).
 */
export interface EditPreview {
  relativeFilePath: string;
  valid: boolean;
  /** Non-null only when `valid` is false. */
  problem: string | null;
  /** Non-null only when `valid` is true. A standard unified diff. */
  unifiedDiff: string | null;
  /** Exact existing text the backend will search for — echoed back verbatim on apply. */
  searchText: string;
  /** Replacement text — echoed back verbatim on apply. */
  replaceText: string;
  /**
   * File mtime captured at plan time. Sent back as `expectedLastModifiedMs`
   * so apply can reject (409) if the file changed on disk since planning.
   * 0 for invalid previews.
   */
  lastModifiedMs: number;
}

/** Response body for `POST /api/v1/edits/plan`. */
export interface EditPlanResponse {
  summary: string;
  /** False if any preview is invalid, or if the model produced nothing usable. */
  applicable: boolean;
  /** May be empty — the model declined or produced nothing usable; show `summary`. */
  previews: EditPreview[];
}

/** One approved edit, echoed back to the backend verbatim. */
export interface ApprovedEdit {
  relativeFilePath: string;
  searchText: string;
  replaceText: string;
  /** The preview's `lastModifiedMs`, used for conflict detection. */
  expectedLastModifiedMs: number;
}

/**
 * Request body for `POST /api/v1/edits/apply`.
 *
 * Identify the repository ONE of two ways:
 *  - `repositoryId` (a registered repository) — the backend then chooses the
 *    delivery from the repository's origin: a LOCAL one is written in place,
 *    a GITHUB one gets a branch, a commit, a push and a pull request.
 *  - `repositoryRoot` alone (a hand-attached path) — the legacy direct-write
 *    path. It can NEVER produce a pull request.
 *
 * So a GitHub repository only reaches the pull-request flow if the id is sent.
 */
export interface ApplyEditsRequest {
  repositoryId?: string;
  repositoryRoot?: string;
  /**
   * The user's original request, in their own words. For a GitHub repository
   * it becomes the branch name, commit message and pull request title, so it
   * must be sent along with `repositoryId`.
   */
  instruction?: string;
  edits: ApprovedEdit[];
}

/**
 * Response body for `POST /api/v1/edits/apply`. One shape for both delivery
 * modes; fields that do not apply to the mode in use are null/absent.
 *
 * On HTTP 200: `success` is true. A LOCAL apply fills `changedFiles` (and
 * usually `backupLocation`). A GITHUB apply fills `branch`, `commitSha` and
 * `pullRequestUrl` instead. GITHUB with an empty `changedFiles` and no
 * `pullRequestUrl` means the edits produced no diff — the code already
 * matched — so nothing was pushed and no pull request exists.
 *
 * On HTTP 409 (a conflict — EXPECTED, not a crash): the same shape comes back
 * in the error body with `success: false`, `changedFiles: []`,
 * `backupLocation: null`, and every reason in `problems`. NOTHING was written.
 *
 * The git fields are optional in the type because a 409 body and a LOCAL
 * response carry them as null, and older responses omit them entirely.
 */
export interface ApplyEditsResponse {
  success: boolean;
  message: string;
  /** Relative paths actually written. Empty on a conflict. */
  changedFiles: string[];
  /** Where the originals were backed up, for manual undo. Null on a conflict and for GITHUB. */
  backupLocation: string | null;
  /** Populated when `success` is false — show every entry. */
  problems: string[];
  /** Null on a failure. */
  sourceType?: RepositorySourceType | null;
  /** GITHUB only: the branch the changes were pushed to. */
  branch?: string | null;
  /** GITHUB only: the commit that holds the changes. */
  commitSha?: string | null;
  /** GITHUB only: the pull request that was opened. Absent when nothing was pushed. */
  pullRequestUrl?: string | null;
}

/**
 * UI-only lifecycle of the edit flow. One instruction runs through:
 *
 *   idle ─► planning ─► reviewing ─► applying ─► applied
 *              │            │            │
 *              ▼            ▼            ▼
 *            error        (stay)     conflict | error
 *
 *  - `idle`      — no plan; the instruction form is shown.
 *  - `planning`  — plan request in flight (10–40s); form is locked.
 *  - `reviewing` — a plan came back; diffs are shown, awaiting Apply/Discard.
 *  - `applying`  — apply request in flight; writing to disk.
 *  - `applied`   — success; the plan is consumed and cannot be re-applied.
 *  - `conflict`  — apply returned 409; nothing written; offer "Plan again".
 *  - `error`     — plan failed, or apply failed for a non-conflict reason.
 */
export type EditFlowState =
  | 'idle'
  | 'planning'
  | 'reviewing'
  | 'applying'
  | 'applied'
  | 'conflict'
  | 'error';
