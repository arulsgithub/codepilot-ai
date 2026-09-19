import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ConversationService } from '../../../core/services/conversation.service';
import { MessageService } from '../../../core/services/message.service';
import { ChatService, HttpStreamError } from '../../../core/services/chat.service';
import { IndexingService } from '../../../core/services/indexing.service';
import { RepositoryService } from '../../../core/services/repository.service';
import { Conversation } from '../../../core/models/conversation.model';
import { ChatMessageViewModel } from '../../../core/models/message.model';
import { ChatRequest, ChatState, ModeSelection, SourceReference } from '../../../core/models/chat.model';
import { IndexingStatus } from '../../../core/models/indexing.model';

const REPOSITORY_STORAGE_KEY = 'codepilot-repository-root';
const MODE_STORAGE_KEY = 'codepilot-chat-mode';
const MODE_VALUES: ModeSelection[] = ['AUTO', 'FAST', 'CODE', 'REASONING'];

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
  private readonly indexingService = inject(IndexingService);
  private readonly repositories = inject(RepositoryService);

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

  // ---- Repository attachment (RAG) ----
  //
  // Scope decision: the attached repository is GLOBAL (one at a time), not
  // per-conversation. Tradeoff: per-conversation would let each chat target
  // a different repo and would line up neatly with the fact that sources are
  // not persisted per message — but it needs either a client-side
  // conversationId->path map with its own persistence, or backend support we
  // are told not to add. Global is the simpler correct option for now: the
  // path is restored from localStorage on load, applies to whichever
  // conversation is active, and can be detached at any time.
  //
  // Two ways to attach exist side by side: the legacy "type an absolute path"
  // flow (`_repositoryRoot`, below) and a repository selected from the
  // registry (RepositoryService). A registry selection takes precedence and
  // the two are kept mutually exclusive by `clearLegacyAttachment()` /
  // `detachRepository()`, so the effective repository is never ambiguous.
  private readonly _repositoryRoot = signal<string | null>(this.readStoredRepositoryRoot());

  /** Effective repository path: the selected registry repository's, else the legacy attachment. */
  readonly repositoryRoot = computed(
    () => this.repositories.selectedRepository()?.localPath ?? this._repositoryRoot()
  );
  readonly repositoryAttached = computed(() => !!this.repositoryRoot());

  private readonly _indexingStatus = signal<IndexingStatus>(
    this._repositoryRoot() ? 'success' : 'idle'
  );
  /** Legacy path indexing OR a registry repository being (re)indexed — either way the header spinner shows. */
  readonly indexingStatus = computed<IndexingStatus>(() =>
    this.repositories.isIndexing() ? 'indexing' : this._indexingStatus()
  );

  /** chunksIndexed from the most recent successful indexing run, if any. */
  private readonly _indexedChunkCount = signal<number | null>(null);
  readonly indexedChunkCount = this._indexedChunkCount.asReadonly();

  private readonly _indexingError = signal<string | null>(null);
  readonly indexingError = this._indexingError.asReadonly();

  // ---- Model mode ----
  private readonly _chatMode = signal<ModeSelection>(this.readStoredChatMode());
  readonly chatMode = this._chatMode.asReadonly();

  /** The failed turn, kept so retry is idempotent and does not duplicate its user message. */
  private lastFailedTurn: { conversationId: string; message: string; requestId: string } | null = null;
  private activeSubscription: { unsubscribe: () => void } | null = null;
  private messageLoadGeneration = 0;
  /** clientId of the assistant placeholder tokens are currently streaming into. */
  private streamingAssistantClientId: string | null = null;

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
        this.activateConversation(conversation);
        onCreated?.(conversation);
      },
      error: (err: HttpErrorResponse) => {
        this._connectionError.set(this.describeConnectionError(err));
      },
    });
  }

  /**
   * Handles the "New Chat" button. If the user is already sitting in a
   * freshly created conversation they have not sent anything in yet, that
   * empty conversation is reused instead of POSTing another throwaway
   * "New Conversation" row every click.
   */
  startNewConversation(): void {
    const current = this._selectedConversation();
    const currentIsEmpty =
      !!current &&
      this._messages().length === 0 &&
      !this.isStreaming() &&
      !this._messagesLoading();
    if (currentIsEmpty) {
      return;
    }
    this.createConversation();
  }

  /** Renames a conversation (PATCH) and reflects it in the sidebar + header. */
  renameConversation(conversationId: string, title: string): void {
    const trimmed = title.trim();
    if (!trimmed) {
      return;
    }
    this.conversationService.renameConversation(conversationId, trimmed).subscribe({
      next: (updated) => {
        this._conversations.update((list) =>
          list.map((c) => (c.id === updated.id ? updated : c))
        );
        if (this._selectedConversation()?.id === updated.id) {
          this._selectedConversation.set(updated);
        }
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

  /**
   * Makes `conversation` the selected one WITHOUT fetching message history.
   *
   * Used immediately after creating a conversation: it has no persisted
   * messages yet, and a GET /messages here would race the send that
   * follows on its heels — the GET resolves with an empty list and calls
   * `_messages.set([])`, wiping the optimistic user bubble and the
   * streaming assistant placeholder, so the reply renders into nothing.
   *
   * Bumping `messageLoadGeneration` also invalidates any history fetch
   * still in flight from a previous `selectConversation()` call.
   */
  private activateConversation(conversation: Conversation): void {
    this.cancelActiveStream();
    this.messageLoadGeneration++;
    this._selectedConversation.set(conversation);
    this._messages.set([]);
    this._messagesLoading.set(false);
    this._chatState.set('idle');
    this._error.set(null);
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
  // Repository attachment + model mode
  // ---------------------------------------------------------------------

  /**
   * Indexes a local repository path and, on success, attaches it so
   * subsequent chat turns are grounded in it. The request can take
   * 30-90 seconds; `indexingStatus()` drives the UI in the meantime and the
   * app stays fully interactive because this is just a cold Observable.
   *
   * A failed run leaves any previously attached repository untouched.
   */
  indexRepository(path: string): void {
    const trimmed = path.trim();
    if (!trimmed || this._indexingStatus() === 'indexing') {
      return;
    }
    this._indexingStatus.set('indexing');
    this._indexingError.set(null);

    this.indexingService.indexRepository(trimmed).subscribe({
      next: (result) => {
        this._repositoryRoot.set(result.repositoryRoot);
        this._indexedChunkCount.set(result.chunksIndexed);
        this._indexingStatus.set('success');
        this.persistRepositoryRoot(result.repositoryRoot);
        // A path the user just attached explicitly must not be shadowed by an
        // older registry selection.
        this.repositories.select(null);
      },
      error: (err: HttpErrorResponse) => {
        this._indexingStatus.set('error');
        this._indexingError.set(this.describeIndexingError(err));
      },
    });
  }

  /** Detaches the repository (legacy attachment AND registry selection) so chat runs without RAG. */
  detachRepository(): void {
    this.clearLegacyAttachment();
    this.repositories.select(null);
  }

  /**
   * Forgets only the legacy path attachment. Called when the user picks a
   * registry repository (or "none") so an old attachment cannot silently
   * reappear when that selection is later cleared.
   */
  clearLegacyAttachment(): void {
    this._repositoryRoot.set(null);
    this._indexedChunkCount.set(null);
    this._indexingError.set(null);
    this._indexingStatus.set('idle');
    this.persistRepositoryRoot(null);
  }

  /** Clears only a failed/finished indexing message, keeping any attachment. */
  dismissIndexingStatus(): void {
    if (this._indexingStatus() === 'indexing') {
      return;
    }
    this._indexingError.set(null);
    this._indexingStatus.set(this._repositoryRoot() ? 'success' : 'idle');
  }

  setChatMode(mode: ModeSelection): void {
    this._chatMode.set(mode);
    try {
      localStorage.setItem(MODE_STORAGE_KEY, mode);
    } catch {
      // Private-browsing / storage-disabled — mode just won't persist.
    }
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
    this.streamingAssistantClientId = assistantClientId;
    newMessages.push({
      clientId: assistantClientId,
      role: 'ASSISTANT',
      content: '',
      createdAt: new Date().toISOString(),
      isStreaming: true,
    });

    this._messages.update((list) => [...list, ...newMessages]);

    this.activeSubscription = this.chatService
      .streamChat(this.buildChatRequest(conversationId, text, requestId))
      .subscribe({
        next: (event) => {
          switch (event.type) {
            case 'START':
              this._chatState.set('streaming');
              break;
            case 'SOURCES':
              // Emitted once, before the first token, only when the answer
              // used repository context. Attach to the in-flight assistant
              // message; nothing else about the pipeline changes.
              this.attachSources(assistantClientId, event.sources ?? []);
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

  /**
   * User-initiated interrupt of the in-flight response (the Stop button).
   *
   * Unsubscribing aborts the underlying fetch (see ChatService), which
   * closes the backend connection. Unlike `cancelActiveStream()` — an
   * internal transition that always discards the in-progress messages —
   * this keeps whatever partial text already streamed in, just finalized
   * and no longer marked as streaming. The partial reply is NOT persisted
   * server-side (the abort skips the backend's save step), so it will be
   * gone on the next history load; that is the intended "stop = discard"
   * behavior for Phase 1.
   */
  stopStreaming(): void {
    if (!this.isStreaming()) {
      return;
    }
    this.activeSubscription?.unsubscribe();
    this.activeSubscription = null;

    const assistantClientId = this.streamingAssistantClientId;
    if (assistantClientId) {
      this.finalizeAssistantMessage(assistantClientId);
      // If Stop was hit before a single token arrived, drop the empty bubble.
      this._messages.update((list) =>
        list.filter((m) => !(m.clientId === assistantClientId && m.content.length === 0))
      );
    }
    this.streamingAssistantClientId = null;
    this.lastFailedTurn = null;
    this._chatState.set('completed');
  }

  cancelActiveStream(): void {
    this.activeSubscription?.unsubscribe();
    this.activeSubscription = null;
    this.streamingAssistantClientId = null;
    if (this.isStreaming()) {
      this._chatState.set('idle');
    }
  }

  /**
   * Builds the stream request body. `repositoryId`/`repositoryRoot` and `mode`
   * are only added when they carry meaning — with no repository attached and
   * mode "Auto" the body is `{ conversationId, message, requestId }`,
   * byte-for-byte identical to Phase 1. A selected registry repository sends
   * both its id and its path (see ChatRequest.repositoryId for why).
   */
  private buildChatRequest(
    conversationId: string,
    message: string,
    requestId: string
  ): ChatRequest {
    const request: ChatRequest = { conversationId, message, requestId };
    const selected = this.repositories.selectedRepository();
    if (selected) {
      request.repositoryId = selected.id;
      request.repositoryRoot = selected.localPath;
    } else {
      const legacyRoot = this._repositoryRoot();
      if (legacyRoot) {
        request.repositoryRoot = legacyRoot;
      }
    }
    const mode = this._chatMode();
    if (mode !== 'AUTO') {
      request.mode = mode;
    }
    return request;
  }

  private attachSources(assistantClientId: string, sources: SourceReference[]): void {
    this._messages.update((list) =>
      list.map((m) => (m.clientId === assistantClientId ? { ...m, sources } : m))
    );
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

  private describeIndexingError(err: HttpErrorResponse): string {
    if (err.status === 0) {
      return 'Unable to reach the CodePilot backend. Please make sure it is running.';
    }
    if (err.status === 400 || err.status === 404) {
      return 'That path could not be indexed. Check that it points to a folder on the machine running the backend.';
    }
    return 'Indexing failed. Please try again.';
  }

  private readStoredRepositoryRoot(): string | null {
    try {
      const stored = localStorage.getItem(REPOSITORY_STORAGE_KEY);
      return stored && stored.trim() ? stored : null;
    } catch {
      return null;
    }
  }

  private persistRepositoryRoot(path: string | null): void {
    try {
      if (path) {
        localStorage.setItem(REPOSITORY_STORAGE_KEY, path);
      } else {
        localStorage.removeItem(REPOSITORY_STORAGE_KEY);
      }
    } catch {
      // Storage unavailable — attachment just won't survive a reload.
    }
  }

  private readStoredChatMode(): ModeSelection {
    try {
      const stored = localStorage.getItem(MODE_STORAGE_KEY) as ModeSelection | null;
      return stored && MODE_VALUES.includes(stored) ? stored : 'AUTO';
    } catch {
      return 'AUTO';
    }
  }
}
