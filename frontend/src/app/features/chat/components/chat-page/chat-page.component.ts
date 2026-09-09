import { ChangeDetectionStrategy, Component, OnInit, ViewChild, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ChatStateService } from '../../services/chat-state.service';
import { ConversationSidebarComponent } from '../../../conversations/components/conversation-sidebar/conversation-sidebar.component';
import { ChatHeaderComponent } from '../chat-header/chat-header.component';
import { MessageListComponent } from '../message-list/message-list.component';
import { MessageComposerComponent } from '../message-composer/message-composer.component';
import { WelcomeScreenComponent } from '../welcome-screen/welcome-screen.component';
import { ModeSelectorComponent } from '../mode-selector/mode-selector.component';
import { RepositoryPanelComponent } from '../repository-panel/repository-panel.component';
import { ConversationRename } from '../../../conversations/components/conversation-item/conversation-item.component';
import { Conversation } from '../../../../core/models/conversation.model';
import { ModeSelection } from '../../../../core/models/chat.model';

const THEME_STORAGE_KEY = 'codepilot-theme';

/**
 * Top-level page component for Phase 1. Owns:
 *  - the mobile sidebar open/closed flag (pure UI state, not chat state)
 *  - the light/dark theme flag, persisted via the memory-safe key below
 *
 * Everything about conversations/messages/streaming is delegated to
 * ChatStateService — this component only reads signals and forwards user
 * intents (send, select, delete, new chat) to it.
 */
@Component({
  selector: 'app-chat-page',
  standalone: true,
  imports: [
    CommonModule,
    ConversationSidebarComponent,
    ChatHeaderComponent,
    MessageListComponent,
    MessageComposerComponent,
    WelcomeScreenComponent,
    ModeSelectorComponent,
    RepositoryPanelComponent,
  ],
  templateUrl: './chat-page.component.html',
  styleUrl: './chat-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChatPageComponent implements OnInit {
  readonly state = inject(ChatStateService);

  @ViewChild(MessageComposerComponent) composer?: MessageComposerComponent;

  sidebarOpen = signal(false);
  repositoryPanelOpen = signal(false);
  theme = signal<'dark' | 'light'>(this.readStoredTheme());

  ngOnInit(): void {
    this.applyTheme(this.theme());
    this.state.loadConversations();
  }

  onNewChat(): void {
    this.state.startNewConversation();
    this.sidebarOpen.set(false);
  }

  onSelectConversation(conversation: Conversation): void {
    this.state.selectConversation(conversation);
    this.sidebarOpen.set(false);
  }

  onDeleteConversation(conversation: Conversation): void {
    this.state.deleteConversation(conversation.id);
  }

  onRenameConversation(rename: ConversationRename): void {
    this.state.renameConversation(rename.id, rename.title);
  }

  onSendMessage(text: string): void {
    if (!this.state.selectedConversation()) {
      // No conversation yet (e.g. user typed straight from the welcome
      // screen) — create one first, then send once it's selected.
      this.createConversationThenSend(text);
      return;
    }
    this.state.sendMessage(text);
  }

  onExamplePromptSelected(text: string): void {
    if (!this.state.selectedConversation()) {
      this.createConversationThenSend(text);
    } else {
      this.state.sendMessage(text);
    }
  }

  onRetry(): void {
    this.state.retryLastMessage();
  }

  onStopStreaming(): void {
    this.state.stopStreaming();
  }

  onModeChange(mode: ModeSelection): void {
    this.state.setChatMode(mode);
  }

  openRepositoryPanel(): void {
    this.repositoryPanelOpen.set(true);
  }

  closeRepositoryPanel(): void {
    this.repositoryPanelOpen.set(false);
    // Drop any transient "failed"/"done" banner so a stale message doesn't
    // greet the user next time they open the panel; the attachment stays.
    this.state.dismissIndexingStatus();
  }

  onIndexRepository(path: string): void {
    this.state.indexRepository(path);
  }

  onDetachRepository(): void {
    this.state.detachRepository();
  }

  toggleSidebar(): void {
    this.sidebarOpen.update((open) => !open);
  }

  toggleTheme(): void {
    const next = this.theme() === 'dark' ? 'light' : 'dark';
    this.theme.set(next);
    this.applyTheme(next);
    try {
      localStorage.setItem(THEME_STORAGE_KEY, next);
    } catch {
      // Storage can throw in private-browsing contexts — theme just won't
      // persist across reloads, which is a harmless degradation.
    }
  }

    private createConversationThenSend(text: string): void {
      this.state.createConversation('New Conversation', (conversation) =>
        this.state.sendMessageToConversation(conversation.id, text)
      );
    }

  private applyTheme(theme: 'dark' | 'light'): void {
    document.documentElement.setAttribute('data-theme', theme);
  }

  private readStoredTheme(): 'dark' | 'light' {
    try {
      const stored = localStorage.getItem(THEME_STORAGE_KEY);
      return stored === 'light' ? 'light' : 'dark';
    } catch {
      return 'dark';
    }
  }
}
