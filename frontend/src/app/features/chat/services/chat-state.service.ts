import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ConversationService } from '../../../core/services/conversation.service';
import { MessageService } from '../../../core/services/message.service';
import { ChatService, HttpStreamError } from '../../../core/services/chat.service';
import { Conversation } from '../../../core/models/conversation.model';
import { ChatMessageViewModel } from '../../../core/models/message.model';
import { ChatState } from '../../../core/models/chat.model';

/**
 * Single owner of all chat/conversation UI state, built on Angular Signals.
 *
 * Why one service instead of NgRx: Phase 1's state graph is small and
 * mostly linear (conversations -> selected conversation -> its messages ->
 * one in-flight stream), so a store adds ceremony without adding safety
 * here. Every piece of mutable state below is a signal owned exclusively by
 * this service — components only ever read it or call methods on it, never
 * mutate it directly. If a future phase needs cross-cutting state
 * (multi-tab sync, undo/redo, etc.) that's the point to reconsider NgRx.
 */
@Injectable({ providedIn: 'root' })
export class ChatStateService {
  private readonly conversationService = inject(ConversationService);
  private readonly messageService = inject(MessageService);
  private readonly chatService = inject(ChatService);

  // ---- Conversations ----
  private readonly _conversations = signal<Conversation[]>([]);
  readonly conversations = this._conversations.asReadonly();

  private readonly _conversationsLoading = signal(false);
  readonly conversationsLoading = this._conversationsLoading.asReadonly();

  private readonly _selectedConversation = signal<Conversation | null>(null);
  readonly selectedConversation = this._selectedConversation.asReadonly();

  // ---- Messages for the selected conversation ----
  private readonly _messages = signal<ChatMessageViewModel[]>([]);
  readonly messages = this._messages.asReadonly();

  private readonly _messagesLoading = signal(false);
  readonly messagesLoading = this._messagesLoading.asReadonly();

  // ---- Streaming ----
  private readonly _chatState = signal<ChatState>('idle');
  readonly chatState = this._chatState.asReadonly();
  readonly isStreaming = computed(
    () => this._chatState() === 'sending' || this._chatState() === 'streaming'
  );

  private readonly _error = signal<string | null>(null);
  readonly error = this._error.asReadonly();

  /** Global connectivity error (e.g. backend unreachable), shown app-wide. */
  private readonly _connectionError = signal<string | null>(null);
  readonly connectionError = this._connectionError.asReadonly();

  /** The failed turn, kept so retry is idempotent and does not duplicate its user message. */
  private lastFailedTurn: { conversationId: string; message: string; requestId: string } | null = null;
  private activeSubscription: { unsubscribe: () => void } | null = null;
  private messageLoadGeneration = 0;

  // ---------------------------------------------------------------------
  // Conversations
  // ---------------------------------------------------------------------

  loadConversations(): void {
    this._conversationsLoading.set(true);
    this._connectionError.set(null);
    this.conversationService.listConversations().subscribe({
      next: (conversations) => {
        this._conversations.set(conversations);
        this._conversationsLoading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this._conversationsLoading.set(false);
        this._connectionError.set(this.describeConnectionError(err));
      },
    });
  }

  createConversation(title = 'New Conversation', onCreated?: (conversation: Conversation) => void): void {
    this._connectionError.set(null);
    this.conversationService.createConversation(title).subscribe({
      next: (conversation) => {
        this._conversations.update((list) => [conversation, ...list]);
        this.selectConversation(conversation);
        onCreated?.(conversation);
      },
      error: (err: HttpErrorResponse) => {
        this._connectionError.set(this.describeConnectionError(err));
      },
    });
  }

  selectConversation(conversation: Conversation): void {
    this.cancelActiveStream();
    this._selectedConversation.set(conversation);
    this._messages.set([]);
    this._chatState.set('idle');
    this._error.set(null);
    this.loadMessages(conversation.id);
  }

  deleteConversation(conversationId: string): void {
    this.conversationService.deleteConversation(conversationId).subscribe({
      next: () => {
        this._conversations.update((list) => list.filter((c) => c.id !== conversationId));
        if (this._selectedConversation()?.id === conversationId) {
          this.cancelActiveStream();
          this._selectedConversation.set(null);
          this._messages.set([]);
        }
      },
      error: (err: HttpErrorResponse) => {
        this._connectionError.set(this.describeConnectionError(err));
      },
    });
  }

  private loadMessages(conversationId: string): void {
    const generation = ++this.messageLoadGeneration;
    this._messagesLoading.set(true);
    this.messageService.getMessages(conversationId).subscribe({
      next: (messages) => {
        if (generation !== this.messageLoadGeneration || this._selectedConversation()?.id !== conversationId) {
          return;
        }
        this._messages.set(
          messages
            .slice()
            .sort((a, b) => a.sequenceNumber - b.sequenceNumber)
            .map((m) => ({
              clientId: m.id,
              id: m.id,
              role: m.role,
              content: m.content,
              createdAt: m.createdAt,
            }))
        );
        this._messagesLoading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        if (generation !== this.messageLoadGeneration || this._selectedConversation()?.id !== conversationId) {
          return;
        }
        this._messagesLoading.set(false);
        this._connectionError.set(this.describeConnectionError(err));
      },
    });
  }

  // ---------------------------------------------------------------------
  // Sending / streaming
  // ---------------------------------------------------------------------

  /**
   * Sends a user message in the currently selected conversation and streams
   * the assistant's reply token-by-token into a temporary message.
   *
   * Flow (mirrors the state diagram in the project brief):
   *   idle -> sending -> streaming -> completed
   *                   \-> error        \-> error
   */
  sendMessage(text: string): void {
    const conversation = this._selectedConversation();
    if (!conversation) {
      return;
    }
    this.sendMessageToConversation(conversation.id, text);
  }

  sendMessageToConversation(conversationId: string, text: string): void {
    if (this.isStreaming() || !text.trim()) {
      return;
    }

    const requestId = crypto.randomUUID();
    this.lastFailedTurn = { conversationId, message: text, requestId };
    this.startStream(conversationId, text, requestId, true);
  }

  private startStream(
    conversationId: string,
    text: string,
    requestId: string,
    appendUserMessage: boolean
  ): void {
    this._error.set(null);
    this._chatState.set('sending');

    const newMessages: ChatMessageViewModel[] = [];
    if (appendUserMessage) {
      // Only the first attempt creates an optimistic user bubble. A retry
      // reuses the existing persisted user turn.
      newMessages.push({
        clientId: crypto.randomUUID(),
        role: 'USER',
        content: text,
        createdAt: new Date().toISOString(),
      });
    }

    // 2. Create a temporary assistant placeholder that tokens will stream into.
    const assistantClientId = crypto.randomUUID();
    newMessages.push({
      clientId: assistantClientId,
      role: 'ASSISTANT',
      content: '',
      createdAt: new Date().toISOString(),
      isStreaming: true,
    });

    this._messages.update((list) => [...list, ...newMessages]);

    this.activeSubscription = this.chatService
      .streamChat({ conversationId, message: text, requestId })
      .subscribe({
        next: (event) => {
          switch (event.type) {
            case 'START':
              this._chatState.set('streaming');
              break;
            case 'TOKEN':
              this._chatState.set('streaming');
              this.appendToken(assistantClientId, event.content ?? '');
              break;
            case 'COMPLETE':
              this.finalizeAssistantMessage(assistantClientId);
              this._chatState.set('completed');
              this.lastFailedTurn = null;
              this.refreshConversationMetadata(conversationId); // add this
              break;
            case 'ERROR':
              this.markAssistantMessageErrored(assistantClientId);
              this._chatState.set('error');
              this._error.set('CodePilot could not generate a response. Please try again.');
              break;
          }
        },
        error: (err) => {
          // Streaming failed partway through — keep whatever partial
          // content was already appended, just flag it as interrupted.
          this.markAssistantMessageErrored(assistantClientId);
          this._chatState.set('error');
          this._error.set(this.describeStreamError(err));
        },
        complete: () => {
          if (this._chatState() === 'streaming') {
            // Backend closed the stream without an explicit COMPLETE event.
            this.finalizeAssistantMessage(assistantClientId);
            this._chatState.set('completed');
          }
        },
      });
  }

  private refreshConversationMetadata(conversationId: string): void {
    this.conversationService.getConversation(conversationId).subscribe({
      next: (updated) => {
        this._conversations.update((list) =>
          list.map((c) => (c.id === updated.id ? updated : c))
        );

        if (this._selectedConversation()?.id === updated.id) {
          this._selectedConversation.set(updated);
        }
      },
      error: () => {
        // Non-blocking: message already completed; skip noisy UI error here.
      },
    });
  }

  /** Retries the last message that failed to send or stream. */
  retryLastMessage(): void {
    const turn = this.lastFailedTurn;
    if (!turn || this._selectedConversation()?.id !== turn.conversationId || this.isStreaming()) {
      return;
    }
    // Drop the errored assistant placeholder before retrying so we don't
    // accumulate duplicate partial responses.
    this._messages.update((list) => list.filter((m) => !(m.role === 'ASSISTANT' && m.isError)));
    this.startStream(turn.conversationId, turn.message, turn.requestId, false);
  }

  cancelActiveStream(): void {
    this.activeSubscription?.unsubscribe();
    this.activeSubscription = null;
    if (this.isStreaming()) {
      this._chatState.set('idle');
    }
  }

  private appendToken(assistantClientId: string, token: string): void {
    this._messages.update((list) =>
      list.map((m) => (m.clientId === assistantClientId ? { ...m, content: m.content + token } : m))
    );
  }

  private finalizeAssistantMessage(assistantClientId: string): void {
    this._messages.update((list) =>
      list.map((m) => (m.clientId === assistantClientId ? { ...m, isStreaming: false } : m))
    );
  }

  private markAssistantMessageErrored(assistantClientId: string): void {
    this._messages.update((list) =>
      list.map((m) =>
        m.clientId === assistantClientId ? { ...m, isStreaming: false, isError: true } : m
      )
    );
  }

  private describeConnectionError(err: HttpErrorResponse): string {
    if (err.status === 0) {
      return 'Unable to connect to CodePilot backend. Please make sure the backend is running.';
    }
    if (err.status === 404) {
      return 'Conversation not found. Refreshing your conversations.';
    }
    return 'Something went wrong talking to the CodePilot backend. Please try again.';
  }

  private describeStreamError(err: unknown): string {
    if (err instanceof HttpStreamError && err.status === 0) {
      return 'Unable to connect to CodePilot backend. Please make sure the backend is running.';
    }
    return 'CodePilot could not generate a response. Please try again.';
  }
}
