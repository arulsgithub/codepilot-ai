import {
  ChangeDetectionStrategy,
  Component,
  HostListener,
  OnInit,
  ViewChild,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ChatStateService } from '../../services/chat-state.service';
import { ConversationSidebarComponent } from '../../../conversations/components/conversation-sidebar/conversation-sidebar.component';
import { ChatHeaderComponent } from '../chat-header/chat-header.component';
import { MessageListComponent } from '../message-list/message-list.component';
import { MessageComposerComponent } from '../message-composer/message-composer.component';
import { WelcomeScreenComponent } from '../welcome-screen/welcome-screen.component';
import { ModeSelectorComponent } from '../mode-selector/mode-selector.component';
import { RepositoryPanelComponent } from '../repository-panel/repository-panel.component';
import { EditPanelComponent } from '../edit-panel/edit-panel.component';
import { CommandPaletteComponent } from '../command-palette/command-palette.component';
import { ShortcutsDialogComponent } from '../shortcuts-dialog/shortcuts-dialog.component';
import { AddRepositoryDialogComponent } from '../../../repositories/components/add-repository-dialog/add-repository-dialog.component';
import { StaleIndexBannerComponent } from '../../../repositories/components/stale-index-banner/stale-index-banner.component';
import { RepositoryService } from '../../../../core/services/repository.service';
import { ConversationRename } from '../../../conversations/components/conversation-item/conversation-item.component';
import { Conversation } from '../../../../core/models/conversation.model';
import { ModeSelection } from '../../../../core/models/chat.model';

const THEME_STORAGE_KEY = 'codepilot-theme';
const SIDEBAR_COLLAPSED_STORAGE_KEY = 'codepilot-sidebar-collapsed';
const HINTS_DISMISSED_STORAGE_KEY = 'codepilot-onboarding-hints-dismissed';

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
    EditPanelComponent,
    CommandPaletteComponent,
    ShortcutsDialogComponent,
    AddRepositoryDialogComponent,
    StaleIndexBannerComponent,
  ],
  templateUrl: './chat-page.component.html',
  styleUrl: './chat-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChatPageComponent implements OnInit {
  readonly state = inject(ChatStateService);
  readonly repositories = inject(RepositoryService);

  @ViewChild(MessageComposerComponent) composer?: MessageComposerComponent;

  sidebarOpen = signal(false);
  addRepositoryOpen = signal(false);
  sidebarCollapsed = signal(this.readStoredSidebarCollapsed());
  repositoryPanelOpen = signal(false);
  editPanelOpen = signal(false);
  commandPaletteOpen = signal(false);
  shortcutsDialogOpen = signal(false);
  theme = signal<'dark' | 'light'>(this.readStoredTheme());
  hintsDismissed = signal(this.readStoredHintsDismissed());

  ngOnInit(): void {
    this.applyTheme(this.theme());
    this.state.loadConversations();
    this.repositories.load();
  }

  openAddRepository(): void {
    this.addRepositoryOpen.set(true);
    this.sidebarOpen.set(false);
  }

  closeAddRepository(): void {
    this.addRepositoryOpen.set(false);
  }

  /**
   * The user picked a registry repository (or "none") in the sidebar. Forget
   * any legacy path attachment so it cannot resurface behind the new choice.
   */
  onRepositorySelected(): void {
    this.state.clearLegacyAttachment();
  }

  /**
   * App-wide keyboard shortcuts. Deliberately narrow: only combinations that
   * browsers don't already own (Ctrl/Cmd+K, Ctrl/Cmd+B) plus the bare `?`
   * and `Escape`, and `?` is ignored while the user is typing anywhere so it
   * never hijacks a literal question mark in the composer or a rename field.
   */
  @HostListener('document:keydown', ['$event'])
  onGlobalKeydown(event: KeyboardEvent): void {
    const isMod = event.ctrlKey || event.metaKey;

    if (isMod && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      this.commandPaletteOpen.set(true);
      return;
    }
    if (isMod && event.key.toLowerCase() === 'b') {
      event.preventDefault();
      this.toggleSidebarCollapsed();
      return;
    }
    if (event.key === '?' && !this.isTypingTarget(event.target) && !this.commandPaletteOpen()) {
      event.preventDefault();
      this.shortcutsDialogOpen.set(true);
      return;
    }
  }

  private isTypingTarget(target: EventTarget | null): boolean {
    const el = target as HTMLElement | null;
    if (!el) {
      return false;
    }
    const tag = el.tagName;
    return tag === 'INPUT' || tag === 'TEXTAREA' || el.isContentEditable;
  }

  // ---------------------------------------------------------------------
  // Command palette
  // ---------------------------------------------------------------------

  closeCommandPalette(): void {
    this.commandPaletteOpen.set(false);
  }

  onPaletteFocusComposer(): void {
    this.commandPaletteOpen.set(false);
    setTimeout(() => this.composer?.focus());
  }

  // ---------------------------------------------------------------------
  // Onboarding hints
  // ---------------------------------------------------------------------

  dismissHints(): void {
    this.hintsDismissed.set(true);
    try {
      localStorage.setItem(HINTS_DISMISSED_STORAGE_KEY, '1');
    } catch {
      // Private-browsing — the hint just reappears next visit, harmless.
    }
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

  openEditPanel(): void {
    this.editPanelOpen.set(true);
  }

  closeEditPanel(): void {
    this.editPanelOpen.set(false);
  }

  /** From the edit panel's "no repository attached" state — send them to attach one. */
  onEditPanelAttachRepo(): void {
    this.editPanelOpen.set(false);
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

  toggleSidebarCollapsed(): void {
    const next = !this.sidebarCollapsed();
    this.sidebarCollapsed.set(next);
    try {
      localStorage.setItem(SIDEBAR_COLLAPSED_STORAGE_KEY, next ? '1' : '0');
    } catch {
      // Storage unavailable — the collapsed state just won't survive a reload.
    }
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

  private readStoredSidebarCollapsed(): boolean {
    try {
      return localStorage.getItem(SIDEBAR_COLLAPSED_STORAGE_KEY) === '1';
    } catch {
      return false;
    }
  }

  private readStoredHintsDismissed(): boolean {
    try {
      return localStorage.getItem(HINTS_DISMISSED_STORAGE_KEY) === '1';
    } catch {
      return false;
    }
  }
}
