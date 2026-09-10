import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IndexingStatus } from '../../../../core/models/indexing.model';

@Component({
  selector: 'app-chat-header',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './chat-header.component.html',
  styleUrl: './chat-header.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChatHeaderComponent {
  @Input() theme: 'dark' | 'light' = 'dark';

  /** Absolute path of the attached repository, or null when none is attached. */
  @Input() repositoryRoot: string | null = null;
  /** Drives the small "indexing…" hint on the repo chip. */
  @Input() indexingStatus: IndexingStatus = 'idle';

  @Output() themeToggled = new EventEmitter<void>();
  @Output() menuToggled = new EventEmitter<void>();
  /** User wants to open the attach-repository modal. */
  @Output() repositoryPanelRequested = new EventEmitter<void>();
  /** User clicked the detach (✕) affordance on the repo chip. */
  @Output() repositoryDetached = new EventEmitter<void>();
  /** User wants to open the AI code-editing modal. */
  @Output() editPanelRequested = new EventEmitter<void>();

  get repositoryName(): string {
    if (!this.repositoryRoot) {
      return '';
    }
    const parts = this.repositoryRoot.split(/[\\/]/).filter(Boolean);
    return parts[parts.length - 1] || this.repositoryRoot;
  }

  get isIndexing(): boolean {
    return this.indexingStatus === 'indexing';
  }
}
