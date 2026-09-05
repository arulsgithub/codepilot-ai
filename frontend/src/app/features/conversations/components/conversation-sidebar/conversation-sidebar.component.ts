import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Conversation } from '../../../../core/models/conversation.model';
import { ConversationItemComponent } from '../conversation-item/conversation-item.component';

/** A labeled group of conversations, e.g. "Today", "Yesterday", "Older". */
interface ConversationGroup {
  label: string;
  conversations: Conversation[];
}

@Component({
  selector: 'app-conversation-sidebar',
  standalone: true,
  imports: [CommonModule, ConversationItemComponent],
  templateUrl: './conversation-sidebar.component.html',
  styleUrl: './conversation-sidebar.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConversationSidebarComponent {
  @Input() set conversations(value: Conversation[]) {
    this._conversations = value;
    this.groups = this.groupByRecency(value);
  }
  get conversations(): Conversation[] {
    return this._conversations;
  }
  private _conversations: Conversation[] = [];

  @Input() selectedConversationId: string | null = null;
  @Input() loading = false;
  /** Whether the sidebar is shown as an open mobile drawer. */
  @Input() open = false;

  @Output() newChatRequested = new EventEmitter<void>();
  @Output() conversationSelected = new EventEmitter<Conversation>();
  @Output() conversationDeleted = new EventEmitter<Conversation>();
  @Output() closeRequested = new EventEmitter<void>();

  groups: ConversationGroup[] = [];

  pendingDeleteConversation: Conversation | null = null;

  requestDelete(conversation: Conversation): void {
    this.pendingDeleteConversation = conversation;
  }

  confirmDelete(): void {
    if (this.pendingDeleteConversation) {
      this.conversationDeleted.emit(this.pendingDeleteConversation);
      this.pendingDeleteConversation = null;
    }
  }

  cancelDelete(): void {
    this.pendingDeleteConversation = null;
  }

  private groupByRecency(conversations: Conversation[]): ConversationGroup[] {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const yesterday = new Date(today);
    yesterday.setDate(yesterday.getDate() - 1);

    const todayList: Conversation[] = [];
    const yesterdayList: Conversation[] = [];
    const olderList: Conversation[] = [];

    for (const conversation of conversations) {
      const updated = new Date(conversation.updatedAt || conversation.createdAt);
      const updatedDay = new Date(updated);
      updatedDay.setHours(0, 0, 0, 0);

      if (updatedDay.getTime() === today.getTime()) {
        todayList.push(conversation);
      } else if (updatedDay.getTime() === yesterday.getTime()) {
        yesterdayList.push(conversation);
      } else {
        olderList.push(conversation);
      }
    }

    const groups: ConversationGroup[] = [];
    if (todayList.length) groups.push({ label: 'Today', conversations: todayList });
    if (yesterdayList.length) groups.push({ label: 'Yesterday', conversations: yesterdayList });
    if (olderList.length) groups.push({ label: 'Older', conversations: olderList });
    return groups;
  }
}
