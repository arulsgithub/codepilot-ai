import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SourceReference } from '../../../../core/models/chat.model';

/**
 * Supporting-evidence list shown under an assistant answer that was grounded
 * in an attached repository.
 *
 * Collapsed by default (a native <details> disclosure) so it never competes
 * with the answer text. Renders nothing at all when there are no sources —
 * which is the normal case for plain chat and for answers loaded from
 * history (the backend does not persist sources with the message).
 */
@Component({
  selector: 'app-message-sources',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './message-sources.component.html',
  styleUrl: './message-sources.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MessageSourcesComponent {
  @Input({ required: true }) sources: SourceReference[] = [];

  /** "3 sources" / "1 source" for the disclosure summary. */
  get summaryLabel(): string {
    const count = this.sources.length;
    return `${count} ${count === 1 ? 'source' : 'sources'}`;
  }

  /** Last path segment, e.g. `ChatService.java`, for the compact row title. */
  fileName(filePath: string): string {
    const parts = filePath.split(/[\\/]/);
    return parts[parts.length - 1] || filePath;
  }

  lineRange(source: SourceReference): string {
    return source.startLine === source.endLine
      ? `line ${source.startLine}`
      : `lines ${source.startLine}–${source.endLine}`;
  }

  trackBySource(_index: number, source: SourceReference): string {
    return `${source.filePath}#${source.startLine}-${source.endLine}`;
  }
}
