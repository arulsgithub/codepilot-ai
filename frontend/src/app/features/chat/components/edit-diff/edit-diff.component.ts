import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

/** One classified line of a unified diff, ready to render. */
export interface DiffLine {
  /** Visual/semantic kind — drives the colour AND the gutter sign. */
  kind: 'add' | 'del' | 'hunk' | 'file' | 'meta' | 'context';
  /** The single character shown in the always-present gutter (+ / - / space). */
  sign: string;
  /** The line text with its leading +/-/space marker removed. */
  text: string;
}

/**
 * Renders a backend-produced unified diff with familiar +/- formatting.
 *
 * This does NOT run a diff algorithm or rebuild the patch into a structural
 * model — the backend already produced a correct unified diff. All it does is
 * classify each line by its first character so it can be coloured and given a
 * gutter sign. Additions and removals are distinguished by BOTH colour and a
 * +/- prefix (in a dedicated gutter column), so the diff stays readable for
 * colour-blind users and if styles fail to load.
 *
 * Layout: monospace, `white-space: pre` (never wraps mid-line), the whole
 * block scrolls horizontally for long lines and vertically past a max height
 * so one large file can't bury the previews below it.
 */
@Component({
  selector: 'app-edit-diff',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './edit-diff.component.html',
  styleUrl: './edit-diff.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditDiffComponent {
  @Input({ required: true })
  set unifiedDiff(value: string) {
    this.lines = this.classify(value ?? '');
  }

  lines: DiffLine[] = [];

  trackByIndex(index: number): number {
    return index;
  }

  private classify(diff: string): DiffLine[] {
    // split(/\r?\n/) — a trailing newline yields one empty context line, which
    // renders harmlessly as a blank row.
    return diff.split(/\r?\n/).map((raw): DiffLine => {
      if (raw.startsWith('+++') || raw.startsWith('---')) {
        return { kind: 'file', sign: ' ', text: raw };
      }
      if (raw.startsWith('@@')) {
        return { kind: 'hunk', sign: ' ', text: raw };
      }
      if (raw.startsWith('\\')) {
        // e.g. "\ No newline at end of file"
        return { kind: 'meta', sign: ' ', text: raw };
      }
      if (raw.startsWith('+')) {
        return { kind: 'add', sign: '+', text: raw.slice(1) };
      }
      if (raw.startsWith('-')) {
        return { kind: 'del', sign: '-', text: raw.slice(1) };
      }
      // Context lines from a unified diff are prefixed with a single space.
      return { kind: 'context', sign: ' ', text: raw.startsWith(' ') ? raw.slice(1) : raw };
    });
  }
}
