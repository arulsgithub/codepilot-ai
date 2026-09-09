/**
 * Model mode the backend can run a chat turn in.
 *
 * The frontend only ever *sends* a subset of these:
 *  - `AUTO` is a UI-only sentinel that maps to "omit the field", letting the
 *    backend choose (RAG when a repository is attached, else CODE).
 *  - `RAG` is never sent explicitly — it is selected automatically by the
 *    backend when `repositoryRoot` is present.
 *  - `TITLE` is internal to the backend (conversation auto-naming) and is
 *    never exposed in the UI.
 */
export type ChatMode = 'FAST' | 'CODE' | 'RAG' | 'REASONING' | 'TITLE';

/** The mode options surfaced in the UI selector. `AUTO` => send `null`/omit. */
export type ModeSelection = 'AUTO' | 'FAST' | 'CODE' | 'REASONING';

/** Request body for `POST /api/v1/chat` and `POST /api/v1/chat/stream`. */
export interface ChatRequest {
  conversationId: string;
  message: string;
  /** Browser-generated idempotency key, reused if this turn is retried. */
  requestId: string;
  /**
   * Absolute path of an indexed local repository to ground the answer in.
   * Omitted entirely when no repository is attached so the request body is
   * byte-identical to Phase 1. A blank string is treated as `null` by the
   * backend, but we omit rather than send `""`.
   */
  repositoryRoot?: string;
  /**
   * Explicit model mode. Omitted when the user picks "Auto" so the backend
   * chooses. Never `RAG` or `TITLE` (see {@link ChatMode}).
   */
  mode?: Exclude<ChatMode, 'RAG' | 'TITLE'>;
}

/** The event "type" discriminator used by the backend's SSE stream. */
export type StreamEventType = 'START' | 'SOURCES' | 'TOKEN' | 'COMPLETE' | 'ERROR';

/**
 * A single chunk of indexed repository code the assistant's answer drew from.
 * Maps 1:1 to the objects in the non-streaming `sources` array and the
 * streaming `SOURCES` event.
 */
export interface SourceReference {
  /** Repo-relative path, e.g. `com/codepilot/chat/service/ChatService.java`. */
  filePath: string;
  /** Fully-qualified symbol, e.g. `com.codepilot...ChatService#sendMessage`. */
  qualifiedName: string;
  startLine: number;
  endLine: number;
}

/** A single parsed Server-Sent Event payload from `/api/v1/chat/stream`. */
export interface StreamEvent {
  type: StreamEventType;
  content: string | null;
  /**
   * Only present on a `SOURCES` event. Emitted once, immediately after
   * `START` and before the first `TOKEN`, and only when the answer used
   * repository context. Absent from every other event type.
   */
  sources?: SourceReference[];
}

/**
 * Explicit frontend state machine for a single in-flight (or just-finished)
 * chat turn. See core/services/chat.service.ts for the transition diagram.
 */
export type ChatState = 'idle' | 'sending' | 'streaming' | 'completed' | 'error';
