import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  Output,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { IndexingStatus } from '../../../../core/models/indexing.model';

/**
 * Modal for attaching a local repository to the chat.
 *
 * The user types an absolute path, indexes it (30-90s — the modal shows a
 * live "working" state and stays open and interactive throughout), and on
 * success the path becomes the attached repository for every following chat
 * turn. An attached repository can be detached from here too.
 *
 * This component is purely presentational: it owns only the text-field draft.
 * All real state (status, chunk count, errors, the attached path) is passed
 * in, and every action is emitted upward to ChatStateService via ChatPage.
 */
@Component({
  selector: 'app-repository-panel',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './repository-panel.component.html',
  styleUrl: './repository-panel.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RepositoryPanelComponent {
  @Input() set open(value: boolean) {
    this._open = value;
    if (value) {
      // Reseed the field each time the modal opens so it reflects whatever
      // is currently attached rather than a stale earlier draft.
      this.draftPath = this.repositoryRoot ?? '';
    }
  }
  get open(): boolean {
    return this._open;
  }
  private _open = false;

  @Input() repositoryRoot: string | null = null;
  @Input() indexingStatus: IndexingStatus = 'idle';
  @Input() indexedChunkCount: number | null = null;
  @Input() indexingError: string | null = null;

  @Output() indexRequested = new EventEmitter<string>();
  @Output() detachRequested = new EventEmitter<void>();
  @Output() closeRequested = new EventEmitter<void>();

  draftPath = '';

  get isIndexing(): boolean {
    return this.indexingStatus === 'indexing';
  }

  get canSubmit(): boolean {
    return !this.isIndexing && this.draftPath.trim().length > 0;
  }

  submit(): void {
    if (!this.canSubmit) {
      return;
    }
    this.indexRequested.emit(this.draftPath.trim());
  }

  detach(): void {
    this.detachRequested.emit();
  }

  close(): void {
    if (this.isIndexing) {
      // Don't let a stray backdrop click abandon an in-flight index; the
      // request keeps running regardless, this just avoids a confusing UI.
      return;
    }
    this.closeRequested.emit();
  }
}
