import {
  AfterViewChecked,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Input,
  ViewChild,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { MarkdownPipe } from '../../../../shared/pipes/markdown.pipe';
import { ChatMessageViewModel } from '../../../../core/models/message.model';

/**
 * Renders one chat message bubble.
 *
 * ASSISTANT content is rendered as sanitized Markdown (see MarkdownPipe).
 * Because the resulting HTML is injected via [innerHTML] — and therefore
 * lives outside Angular's template binding system — the "Copy" button on
 * each code block cannot use a normal `(click)` binding. Instead, after
 * every view check we scan for freshly-rendered `pre.code-block` elements
 * and attach a plain DOM click listener + inject the button ourselves. This
 * is safe because we're creating and inserting trusted elements/text nodes
 * directly, not interpreting any additional untrusted HTML.
 */
@Component({
  selector: 'app-message-item',
  standalone: true,
  imports: [CommonModule, MarkdownPipe],
  templateUrl: './message-item.component.html',
  styleUrl: './message-item.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MessageItemComponent implements AfterViewChecked {
  @Input({ required: true }) message!: ChatMessageViewModel;

  @ViewChild('contentRef') contentRef?: ElementRef<HTMLElement>;

  private enhancedBlocks = new WeakSet<Element>();

  ngAfterViewChecked(): void {
    this.enhanceCodeBlocks();
  }

  private enhanceCodeBlocks(): void {
    const container = this.contentRef?.nativeElement;
    if (!container) {
      return;
    }

    const codeBlocks = container.querySelectorAll<HTMLElement>('pre.code-block');
    codeBlocks.forEach((block) => {
      if (this.enhancedBlocks.has(block)) {
        return;
      }
      this.enhancedBlocks.add(block);

      const language = block.dataset['language'] || 'text';
      const header = document.createElement('div');
      header.className = 'code-block-header';

      const languageLabel = document.createElement('span');
      languageLabel.className = 'code-block-language';
      languageLabel.textContent = language;

      const copyButton = document.createElement('button');
      copyButton.type = 'button';
      copyButton.className = 'code-block-copy';
      copyButton.setAttribute('aria-label', `Copy ${language} code to clipboard`);
      copyButton.textContent = 'Copy';

      copyButton.addEventListener('click', () => {
        const codeElement = block.querySelector('code');
        const codeText = codeElement?.textContent ?? '';
        navigator.clipboard
          .writeText(codeText)
          .then(() => {
            copyButton.textContent = 'Copied';
            copyButton.classList.add('copied');
            setTimeout(() => {
              copyButton.textContent = 'Copy';
              copyButton.classList.remove('copied');
            }, 1500);
          })
          .catch(() => {
            copyButton.textContent = 'Failed';
            setTimeout(() => (copyButton.textContent = 'Copy'), 1500);
          });
      });

      header.appendChild(languageLabel);
      header.appendChild(copyButton);
      block.prepend(header);
    });
  }
}
