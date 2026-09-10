/**
 * Contracts for the backend code-editing capability:
 *  - `POST /api/v1/edits/plan`  — reasons about an instruction and returns a
 *    reviewable set of diffs. Writes NOTHING to disk. Slow (10–40s).
 *  - `POST /api/v1/edits/apply` — writes the approved edits to disk atomically
 *    (all-or-nothing) and re-indexes the changed files.
 *
 * These mirror the Java records in `com.codepilot.edit.dto` 1:1. Nothing here
 * holds application state — the edit lifecycle lives in
 * features/chat/services/edit-state.service.ts.
 */

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

/** Request body for `POST /api/v1/edits/apply`. */
export interface ApplyEditsRequest {
  repositoryRoot: string;
  edits: ApprovedEdit[];
}

/**
 * Response body for `POST /api/v1/edits/apply`.
 *
 * On HTTP 200: `applied` is true, `message`/`changedFiles`/`backupLocation`
 * describe what was written, `problems` is empty.
 *
 * On HTTP 409 (a conflict — EXPECTED, not a crash): the same shape comes back
 * in the error body with `applied: false`, `changedFiles: []`,
 * `backupLocation: null`, and every reason in `problems`. NOTHING was written.
 */
export interface ApplyEditsResponse {
  applied: boolean;
  message: string;
  /** Relative paths actually written. Empty on a conflict. */
  changedFiles: string[];
  /** Where the originals were backed up, for manual undo. Null on a conflict. */
  backupLocation: string | null;
  /** Populated when `applied` is false — show every entry. */
  problems: string[];
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
