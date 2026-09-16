import {
  AfterViewChecked,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Input,
  OnChanges,
  Output,
  EventEmitter,
  SimpleChanges,
  ViewChild,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { MessageItemComponent } from '../message-item/message-item.component';
import { TypingIndicatorComponent } from '../typing-indicator/typing-indicator.component';
import { ChatMessageViewModel } from '../../../../core/models/message.model';
import { ChatState } from '../../../../core/models/chat.model';

/**
 * Scrollable message history for the selected conversation.
 *
 * Auto-scroll behavior: pins to the bottom whenever new content arrives
 * (new message, streaming token) UNLESS the user has manually scrolled up
 * to read earlier history — in which case we leave their scroll position
 * alone rather than yanking them back down mid-read.
 */
@Component({
  selector: 'app-message-list',
  standalone: true,
  imports: [CommonModule, MessageItemComponent, TypingIndicatorComponent],
  templateUrl: './message-list.component.html',
  styleUrl: './message-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MessageListComponent implements AfterViewChecked, OnChanges {
  @Input() messages: ChatMessageViewModel[] = [];
  @Input() loading = false;
  @Input() chatState: ChatState = 'idle';
  @Input() error: string | null = null;

  @Output() retryRequested = new EventEmitter<void>();

  @ViewChild('scrollContainer') scrollContainer?: ElementRef<HTMLDivElement>;

  private isPinnedToBottom = true;
  private lastMessageCount = 0;
  private lastRenderedContentLength = 0;

  /** Drives the "scroll to latest" affordance — visible only once the user has scrolled away from the bottom. */
  showScrollToBottom = false;

  /** Announced once per completed/errored turn — never per streamed token, so screen readers aren't spammed. */
  liveRegionMessage = '';

  get showThinkingIndicator(): boolean {
    return this.chatState === 'sending';
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['chatState']) {
      const previous = changes['chatState'].previousValue as ChatState | undefined;
      if (previous !== this.chatState) {
        if (this.chatState === 'completed') {
          this.liveRegionMessage = 'CodePilot replied.';
        } else if (this.chatState === 'error') {
          this.liveRegionMessage = 'CodePilot could not generate a response.';
        }
      }
    }
  }

  onScroll(): void {
    const el = this.scrollContainer?.nativeElement;
    if (!el) {
      return;
    }
    const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
    this.isPinnedToBottom = distanceFromBottom < 80;
    this.showScrollToBottom = !this.isPinnedToBottom;
  }

  scrollToBottomClicked(): void {
    this.isPinnedToBottom = true;
    this.showScrollToBottom = false;
    this.scrollToBottom();
  }

  ngAfterViewChecked(): void {
    const totalContentLength = this.messages.reduce((sum, m) => sum + m.content.length, 0);
    const contentGrew = totalContentLength !== this.lastRenderedContentLength;
    const messageCountChanged = this.messages.length !== this.lastMessageCount;

    if ((contentGrew || messageCountChanged) && this.isPinnedToBottom) {
      this.scrollToBottom();
    }

    this.lastMessageCount = this.messages.length;
    this.lastRenderedContentLength = totalContentLength;
  }

  trackByClientId(_index: number, message: ChatMessageViewModel): string {
    return message.clientId;
  }

  private scrollToBottom(): void {
    const el = this.scrollContainer?.nativeElement;
    if (el) {
      el.scrollTop = el.scrollHeight;
    }
  }
}
