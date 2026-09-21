import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { EditService } from '../../../core/services/edit.service';
import { ChatStateService } from './chat-state.service';
import {
  ApplyEditsRequest,
  ApplyEditsResponse,
  EditFlowState,
  EditPlanResponse,
} from '../../../core/models/edit.model';

/**
 * Single owner of all code-editing UI state, built on Angular Signals.
 *
 * Why a SEPARATE service instead of extending ChatStateService: the edit flow
 * is a self-contained request → review → approve → write lifecycle that has
 * nothing to do with chat streaming. It has its own multi-step state machine
 * (see {@link EditFlowState}), its own slow request, and its own destructive
 * final step. Folding it into ChatStateService would nearly double that file
 * and interleave two unrelated lifecycles — exactly the "cross-cutting state"
 * situation its own header comment says to keep out. The one thing the two
 * share is the attached repository, and that already has a single owner:
 * ChatStateService. This service READS `repositoryRoot()` from it rather than
 * re-deriving it from localStorage.
 *
 * Components only ever read the signals below or call the methods — they never
 * mutate state directly.
 */
@Injectable({ providedIn: 'root' })
export class EditStateService {
  private readonly editService = inject(EditService);
  private readonly chatState = inject(ChatStateService);

  // ---- Flow state ----
  private readonly _state = signal<EditFlowState>('idle');
  readonly state = this._state.asReadonly();

  readonly isBusy = computed(
    () => this._state() === 'planning' || this._state() === 'applying'
  );

  /** The plan currently under review. Null unless `state()` is 'reviewing'. */
  private readonly _plan = signal<EditPlanResponse | null>(null);
  readonly plan = this._plan.asReadonly();

  /** The instruction behind the current/last plan — reused by "Plan again". */
  private readonly _instruction = signal<string>('');
  readonly instruction = this._instruction.asReadonly();

  /** Human-readable message when `state()` is 'error' (plan or non-409 apply failure). */
  private readonly _errorMessage = signal<string | null>(null);
  readonly errorMessage = this._errorMessage.asReadonly();

  /** Every conflict reason from a 409 apply. Non-empty only when `state()` is 'conflict'. */
  private readonly _conflictProblems = signal<string[]>([]);
  readonly conflictProblems = this._conflictProblems.asReadonly();

  /** The success payload. Non-null only when `state()` is 'applied'. */
  private readonly _applyResult = signal<ApplyEditsResponse | null>(null);
  readonly applyResult = this._applyResult.asReadonly();

  // ---- Derived ----

  /** The attached repository, owned by ChatStateService. Editing needs one. */
  readonly repositoryRoot = this.chatState.repositoryRoot;
  readonly hasRepository = this.chatState.repositoryAttached;

  /** Id of the selected registry repository (null for a legacy path-only attachment). */
  readonly repositoryId = this.chatState.repositoryId;

  /**
   * True when applying will open a pull request instead of writing files in
   * place. Drives the confirmation and progress wording so the user is never
   * told "write to disk" for a change that is really a branch + PR.
   */
  readonly isGithubTarget = computed(() => this.chatState.repositorySourceType() === 'GITHUB');

  /**
   * Whether Apply may be offered. Mirrors the backend's own gate: a plan is
   * under review AND every preview validated. Client-side only — the backend
   * re-validates everything — but the UI must not knowingly POST a doomed
   * apply (`applicable === false`).
   */
  readonly canApply = computed(
    () => this._state() === 'reviewing' && this._plan()?.applicable === true
  );

  private planSubscription: { unsubscribe: () => void } | null = null;
  private applySubscription: { unsubscribe: () => void } | null = null;

  // ---------------------------------------------------------------------
  // Planning
  // ---------------------------------------------------------------------

  /**
   * Sends an instruction to the planner. No-op if a request is already in
   * flight, the instruction is blank, or no repository is attached (the panel
   * shows an explanation in that last case rather than calling this).
   */
  planEdits(instruction: string): void {
    const trimmed = instruction.trim();
    const repositoryRoot = this.repositoryRoot();
    if (!trimmed || !repositoryRoot || this.isBusy()) {
      return;
    }

    this.planSubscription?.unsubscribe();
    this._instruction.set(trimmed);
    this._plan.set(null);
    this._applyResult.set(null);
    this._conflictProblems.set([]);
    this._errorMessage.set(null);
    this._state.set('planning');

    this.planSubscription = this.editService.planEdits(repositoryRoot, trimmed).subscribe({
      next: (plan) => {
        this._plan.set(plan);
        this._state.set('reviewing');
      },
      error: (err: HttpErrorResponse) => {
        this._state.set('error');
        this._errorMessage.set(this.describePlanError(err));
      },
    });
  }

  /** Re-runs planning with the same instruction — the fix offered after a 409. */
  planAgain(): void {
    const instruction = this._instruction();
    if (instruction) {
      this.planEdits(instruction);
    }
  }

  // ---------------------------------------------------------------------
  // Applying
  // ---------------------------------------------------------------------

  /**
   * Writes the reviewed plan to disk. Guarded by {@link canApply} so a plan
   * with any invalid preview can never be submitted. The component is
   * responsible for the confirmation step that precedes this call.
   */
  applyPlan(): void {
    const plan = this._plan();
    const repositoryRoot = this.repositoryRoot();
    if (!this.canApply() || !plan || !repositoryRoot) {
      return;
    }

    const request: ApplyEditsRequest = {
      repositoryRoot,
      // Every preview is valid here (canApply === true); echo each one back verbatim.
      edits: plan.previews.map((preview) => ({
        relativeFilePath: preview.relativeFilePath,
        searchText: preview.searchText,
        replaceText: preview.replaceText,
        expectedLastModifiedMs: preview.lastModifiedMs,
      })),
    };

    // For a registered repository, identify it BY ID and pass the user's own
    // words along. The backend only takes the branch/commit/pull-request route
    // when it sees a repositoryId — a path alone always means "write in place",
    // even for a GitHub clone — and for GitHub the instruction becomes the
    // branch name, commit message and PR title. A legacy path-only attachment
    // has no id, so its request stays exactly as before.
    const repositoryId = this.repositoryId();
    if (repositoryId) {
      request.repositoryId = repositoryId;
      request.instruction = this._instruction();
    }

    this.applySubscription?.unsubscribe();
    this._state.set('applying');
    this._errorMessage.set(null);
    this._conflictProblems.set([]);

    this.applySubscription = this.editService.applyEdits(request).subscribe({
      next: (result) => {
        this._applyResult.set(result);
        // The plan is consumed: drop it so the UI cannot offer Apply again
        // (a second attempt would fail anyway — the SEARCH text is gone).
        this._plan.set(null);
        this._state.set('applied');
      },
      error: (err: HttpErrorResponse) => {
        if (err.status === 409) {
          // Expected outcome, not a crash — most often the user edited the
          // file in their IDE between planning and applying.
          const body = err.error as ApplyEditsResponse | null;
          const problems = body?.problems?.length
            ? body.problems
            : ['The files changed since this edit was planned. Plan again to get a fresh diff.'];
          this._conflictProblems.set(problems);
          this._state.set('conflict');
          return;
        }
        this._state.set('error');
        this._errorMessage.set(this.describeApplyError(err));
      },
    });
  }

  // ---------------------------------------------------------------------
  // Teardown
  // ---------------------------------------------------------------------

  /** Drops the plan/result without applying anything — the Discard action. */
  discard(): void {
    this.planSubscription?.unsubscribe();
    this.planSubscription = null;
    this.applySubscription?.unsubscribe();
    this.applySubscription = null;
    this._state.set('idle');
    this._plan.set(null);
    this._applyResult.set(null);
    this._conflictProblems.set([]);
    this._errorMessage.set(null);
    this._instruction.set('');
  }

  // ---------------------------------------------------------------------
  // Error copy
  // ---------------------------------------------------------------------

  private describePlanError(err: HttpErrorResponse): string {
    if (err.status === 0) {
      return 'Unable to reach the CodePilot backend. Please make sure it is running.';
    }
    if (err.status === 400) {
      return 'That instruction could not be planned. Try describing the change differently.';
    }
    return 'Planning the edit failed. Please try again.';
  }

  private describeApplyError(err: HttpErrorResponse): string {
    if (err.status === 0) {
      return 'Unable to reach the CodePilot backend. The edit was not applied.';
    }
    // The backend writes human-readable messages on purpose for the failures
    // it anticipates (e.g. "Sync it before applying edits", a push or
    // pull-request failure), so surface those rather than a generic line. A bare
    // 500 only carries the catch-all "An unexpected error occurred.", which is
    // less useful than the reassurance below, so it keeps the fallback.
    const body = err.error as { message?: unknown } | null;
    if (err.status !== 500 && body && typeof body.message === 'string' && body.message.trim()) {
      return body.message;
    }
    return this.isGithubTarget()
      ? 'Opening the pull request failed. The base branch was not changed.'
      : 'Applying the edit failed. Your files were not changed.';
  }
}
