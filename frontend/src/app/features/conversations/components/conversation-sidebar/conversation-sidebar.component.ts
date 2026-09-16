import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Conversation } from '../../../../core/models/conversation.model';
import {
  ConversationItemComponent,
  ConversationRename,
} from '../conversation-item/conversation-item.component';

/** A labeled group of conversations, e.g. "Today", "Yesterday", "Older". */
interface ConversationGroup {
  label: string;
  conversations: Conversation[];
}

@Component({
  selector: 'app-conversation-sidebar',
  standalone: true,
  imports: [CommonModule, FormsModule, ConversationItemComponent],
  templateUrl: './conversation-sidebar.component.html',
  styleUrl: './conversation-sidebar.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConversationSidebarComponent {
  @Input() set conversations(value: Conversation[]) {
    this._conversations = value;
    this.refreshGroups();
  }
  get conversations(): Conversation[] {
    return this._conversations;
  }
  private _conversations: Conversation[] = [];

  @Input() selectedConversationId: string | null = null;
  @Input() loading = false;
  /** Whether the sidebar is shown as an open mobile drawer. */
  @Input() open = false;
  /**
   * Desktop-only collapsed ("rail") state. Independent of `open` — on mobile
   * the sidebar is always either an off-canvas drawer (open) or hidden
   * entirely, collapse never applies there.
   */
  @Input() collapsed = false;

  @Output() newChatRequested = new EventEmitter<void>();
  @Output() conversationSelected = new EventEmitter<Conversation>();
  @Output() conversationDeleted = new EventEmitter<Conversation>();
  @Output() conversationRenamed = new EventEmitter<ConversationRename>();
  @Output() closeRequested = new EventEmitter<void>();
  @Output() collapseToggled = new EventEmitter<void>();

  groups: ConversationGroup[] = [];

  pendingDeleteConversation: Conversation | null = null;

  /** Client-side title filter — conversation history is small enough that a server round trip isn't warranted. */
  searchQuery = '';

  onSearchChange(): void {
    this.refreshGroups();
  }

  clearSearch(): void {
    this.searchQuery = '';
    this.refreshGroups();
  }

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

  private refreshGroups(): void {
    const query = this.searchQuery.trim().toLowerCase();
    const filtered = query
      ? this._conversations.filter((c) => c.title.toLowerCase().includes(query))
      : this._conversations;
    this.groups = this.groupByRecency(filtered);
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
