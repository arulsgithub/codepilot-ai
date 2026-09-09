import { SourceReference } from './chat.model';

/** The role a message was authored by, as defined by the backend. */
export type MessageRole = 'SYSTEM' | 'USER' | 'ASSISTANT';

/**
 * A persisted message as returned by
 * `GET /api/v1/conversations/{id}/messages`.
 */
export interface Message {
  id: string;
  conversationId: string;
  role: MessageRole;
  content: string;
  sequenceNumber: number;
  createdAt: string;
}

/**
 * A UI-only view model for a message being rendered in the chat area.
 *
 * This is intentionally separate from `Message` (the API model): a message
 * that is currently streaming has no `id`/`sequenceNumber`/`createdAt` yet,
 * and needs extra UI-only flags (`isStreaming`, `isError`) that the backend
 * never sends. Keeping the two interfaces separate means a backend schema
 * change never silently breaks chat rendering state, and vice versa.
 */
export interface ChatMessageViewModel {
  /** Client-generated id (e.g. via crypto.randomUUID()) used as a track-by key. */
  clientId: string;
  /** Present once the message has been persisted and confirmed by the backend. */
  id?: string;
  role: MessageRole;
  content: string;
  createdAt: string;
  /** True while tokens are still being appended to this message. */
  isStreaming?: boolean;
  /** True if this message's generation ended in an error. */
  isError?: boolean;
  /**
   * Indexed code chunks this answer drew from, from the streaming `SOURCES`
   * event. Only ever set on ASSISTANT messages, and only for turns that used
   * an attached repository. Backend does NOT persist these, so messages
   * loaded from history or replayed after a dropped connection arrive
   * without them — that is expected and simply renders nothing.
   */
  sources?: SourceReference[];
}
