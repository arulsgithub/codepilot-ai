import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  EventEmitter,
  Input,
  Output,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { EditStateService } from '../../services/edit-state.service';
import { EditDiffComponent } from '../edit-diff/edit-diff.component';
import { EditPreview } from '../../../../core/models/edit.model';

/**
 * Modal for the AI code-editing flow: describe a change, wait for the plan,
 * review the diffs, and apply them to disk.
 *
 * WHY ITS OWN PANEL, NOT THE CHAT COMPOSER:
 * The composer is tuned for a fast type → Enter → stream loop. An edit request
 * is the opposite shape — a slow (10–40s) reasoning call whose result is a
 * multi-file diff review followed by a destructive, confirmed write. Cramming
 * that into the composer would either bury chat or entangle with the SSE
 * pipeline we are told not to touch. A dedicated modal (opened from the
 * header, exactly like the attach-repository panel) keeps chat byte-identical
 * and gives the review the room it needs.
 *
 * WHY IT READS EditStateService DIRECTLY (unlike the purely-presentational
 * repository panel): the edit flow exposes ~8 pieces of state and 4 actions.
 * Prop-drilling all of that through ChatPage would add noise without adding
 * safety. This is a feature-level component, not a generic one, so it injects
 * the state service and only ever reads its signals / calls its methods —
 * never mutates state — which is the rule the architecture actually cares
 * about. The panel's own open/closed flag stays owned by ChatPage.
 */
@Component({
  selector: 'app-edit-panel',
  standalone: true,
  imports: [CommonModule, FormsModule, EditDiffComponent],
  templateUrl: './edit-panel.component.html',
  styleUrl: './edit-panel.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditPanelComponent {
  readonly state = inject(EditStateService);
  private readonly destroyRef = inject(DestroyRef);

  @Input() set open(value: boolean) {
    this._open = value;
    if (value && this.state.state() === 'idle') {
      // Reseed the field when opening on a fresh flow so a stale draft from a
      // discarded run doesn't greet the user.
      this.draft = '';
    }
    if (!value) {
      this.pendingConfirm.set(false);
    }
  }
  get open(): boolean {
    return this._open;
  }
  private _open = false;

  /** User has no repository attached and wants to fix that. */
  @Output() attachRepoRequested = new EventEmitter<void>();
  @Output() closeRequested = new EventEmitter<void>();

  draft = '';

  /** The confirmation step shown after the user presses Apply, before the write. */
  readonly pendingConfirm = signal(false);

  /** Seconds elapsed while planning — shown so a 40s wait never looks frozen. */
  readonly planElapsedSeconds = signal(0);
  private planTimer: ReturnType<typeof setInterval> | null = null;

  readonly validPreviewCount = computed(
    () => this.state.plan()?.previews.filter((p) => p.valid).length ?? 0
  );

  readonly hasBlockedPreview = computed(
    () => this.state.plan()?.previews.some((p) => !p.valid) ?? false
  );

  constructor() {
    // Drive the elapsed-time counter off the flow state signal: start the
    // interval when planning begins, stop it otherwise. `allowSignalWrites` is
    // needed because entering 'planning' also resets the counter to 0 — a
    // deliberate, contained write that syncs an imperative timer to state,
    // which is exactly what the option exists for.
    effect(
      () => {
        if (this.state.state() === 'planning') {
          this.startPlanTimer();
        } else {
          this.stopPlanTimer();
        }
      },
      { allowSignalWrites: true }
    );
    this.destroyRef.onDestroy(() => this.stopPlanTimer());
  }

  submitInstruction(): void {
    const text = this.draft.trim();
    if (!text || this.state.isBusy() || !this.state.hasRepository()) {
      return;
    }
    this.state.planEdits(text);
  }

  /** Step 1 of applying: reveal the confirmation. */
  requestApply(): void {
    if (this.state.canApply()) {
      this.pendingConfirm.set(true);
    }
  }

  /** Step 2 of applying: the user confirmed the write to disk. */
  confirmApply(): void {
    this.pendingConfirm.set(false);
    this.state.applyPlan();
  }

  cancelApply(): void {
    this.pendingConfirm.set(false);
  }

  discard(): void {
    this.pendingConfirm.set(false);
    this.state.discard();
    this.draft = '';
  }

  planAgain(): void {
    this.pendingConfirm.set(false);
    this.state.planAgain();
  }

  requestAttachRepo(): void {
    this.attachRepoRequested.emit();
  }

  close(): void {
    // Closing never discards an in-flight or completed flow — the header
    // button reopens it right where it was. Only drop the confirmation.
    this.pendingConfirm.set(false);
    this.closeRequested.emit();
  }

  /** Finish after a successful apply: clear the flow and close. */
  finishAfterApply(): void {
    this.discard();
    this.closeRequested.emit();
  }

  trackByPreview(index: number, preview: EditPreview): string {
    return `${index}:${preview.relativeFilePath}`;
  }

  private startPlanTimer(): void {
    if (this.planTimer !== null) {
      return;
    }
    this.planElapsedSeconds.set(0);
    this.planTimer = setInterval(() => {
      this.planElapsedSeconds.update((s) => s + 1);
    }, 1000);
  }

  private stopPlanTimer(): void {
    if (this.planTimer !== null) {
      clearInterval(this.planTimer);
      this.planTimer = null;
    }
  }
}
