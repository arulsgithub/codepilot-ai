import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';

interface ShortcutEntry {
  keys: string[];
  description: string;
}

/**
 * Read-only reference for the app's keyboard shortcuts, opened with `?`
 * (see ChatPageComponent's document-level keydown handler). Exists purely so
 * the shortcuts added for power users in this phase are discoverable —
 * nothing here is a hidden feature.
 */
@Component({
  selector: 'app-shortcuts-dialog',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './shortcuts-dialog.component.html',
  styleUrl: './shortcuts-dialog.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ShortcutsDialogComponent {
  @Input() open = false;
  @Output() closeRequested = new EventEmitter<void>();

  readonly shortcuts: ShortcutEntry[] = [
    { keys: ['Ctrl/⌘', 'K'], description: 'Open the command palette' },
    { keys: ['Ctrl/⌘', 'B'], description: 'Collapse or expand the sidebar' },
    { keys: ['?'], description: 'Show this shortcut reference' },
    { keys: ['Esc'], description: 'Close a dialog or panel' },
    { keys: ['Enter'], description: 'Send the current message' },
    { keys: ['Shift', 'Enter'], description: 'Insert a newline in the message box' },
  ];

  close(): void {
    this.closeRequested.emit();
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      event.preventDefault();
      this.close();
    }
  }
}
