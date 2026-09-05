/** Request body for `POST /api/v1/chat/stream`. */
export interface ChatRequest {
  conversationId: string;
  message: string;
  /** Browser-generated idempotency key, reused if this turn is retried. */
  requestId: string;
}

/** The event "type" discriminator used by the backend's SSE stream. */
export type StreamEventType = 'START' | 'TOKEN' | 'COMPLETE' | 'ERROR';

/** A single parsed Server-Sent Event payload from `/api/v1/chat/stream`. */
export interface StreamEvent {
  type: StreamEventType;
  content: string | null;
}

/**
 * Explicit frontend state machine for a single in-flight (or just-finished)
 * chat turn. See core/services/chat.service.ts for the transition diagram.
 */
export type ChatState = 'idle' | 'sending' | 'streaming' | 'completed' | 'error';
