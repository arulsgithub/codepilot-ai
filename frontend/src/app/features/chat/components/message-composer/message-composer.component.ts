import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  Output,
  ViewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';

/**
 * The message input area at the bottom of the chat.
 *
 * Keyboard contract: Enter sends, Shift+Enter inserts a newline. The
 * textarea auto-grows with content (capped by max-height in SCSS) and is
 * cleared immediately after a successful send. While `disabled` (streaming
 * in progress), the send button and Enter-to-send are both blocked to
 * prevent duplicate in-flight requests.
 */
@Component({
  selector: 'app-message-composer',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './message-composer.component.html',
  styleUrl: './message-composer.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MessageComposerComponent {
  @Input() disabled = false;
  /** True while a response is streaming — swaps the Send button for Stop. */
  @Input() streaming = false;
  @Output() messageSubmitted = new EventEmitter<string>();
  @Output() stopRequested = new EventEmitter<void>();

  @ViewChild('textareaRef') textareaRef?: ElementRef<HTMLTextAreaElement>;

  draft = '';

  get canSend(): boolean {
    return !this.disabled && this.draft.trim().length > 0;
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.submit();
    }
    // Shift+Enter falls through to the textarea's default behavior (newline).
  }

  onInput(): void {
    this.autoGrow();
  }

  submit(): void {
    if (!this.canSend) {
      return;
    }
    const text = this.draft.trim();
    this.draft = '';
    this.autoGrow();
    this.messageSubmitted.emit(text);
  }

  /** Programmatically fill and focus the composer, e.g. from an example prompt. */
  setDraft(text: string): void {
    this.draft = text;
    this.autoGrow();
    this.textareaRef?.nativeElement.focus();
  }

  private autoGrow(): void {
    const el = this.textareaRef?.nativeElement;
    if (!el) {
      return;
    }
    el.style.height = 'auto';
    el.style.height = `${Math.min(el.scrollHeight, 200)}px`;
  }
}
