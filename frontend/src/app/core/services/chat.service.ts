import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ChatRequest, StreamEvent } from '../models/chat.model';
import { SseParser } from '../../shared/utils/sse-parser';

/**
 * Streaming client for POST /api/v1/chat/stream.
 *
 * Angular's HttpClient cannot progressively expose a streaming response
 * body for a POST request the way the browser's native `EventSource` can
 * for GET — and the backend contract is intentionally POST-only (it needs a
 * JSON body). So this service bypasses HttpClient entirely and talks to
 * `fetch()` directly, reading the response body as a `ReadableStream` of
 * bytes, decoding it with `TextDecoder`, and feeding the decoded text into
 * `SseParser` to reassemble complete events even when the browser delivers
 * a JSON payload split across multiple `read()` calls.
 *
 * This class intentionally contains ZERO Angular component logic — it only
 * knows how to turn a conversationId + message into a stream of
 * StreamEvents. UI state (idle/sending/streaming/completed/error) is owned
 * by ChatStateService, which subscribes to this.
 */
@Injectable({ providedIn: 'root' })
export class ChatService {
  private readonly streamUrl = `${environment.apiBaseUrl}/api/v1/chat/stream`;

  /**
   * Opens a streaming chat request and emits each StreamEvent as it
   * arrives. The returned Observable:
   *  - completes normally after a COMPLETE event (or the stream ending),
   *  - errors if the network request fails, the response is non-OK, or the
   *    body stream itself errors out,
   *  - is cancellable: unsubscribing aborts the underlying fetch via
   *    AbortController, which stops the backend connection.
   */
  streamChat(request: ChatRequest): Observable<StreamEvent> {
    return new Observable<StreamEvent>((subscriber) => {
      const abortController = new AbortController();

      this.runStream(request, abortController.signal, subscriber).catch((error) => {
        // AbortError is the expected result of unsubscribing early — not a
        // real failure, so don't surface it to the UI as an error state.
        if (error instanceof DOMException && error.name === 'AbortError') {
          return;
        }
        subscriber.error(error);
      });

      return () => abortController.abort();
    });
  }

  private async runStream(
    request: ChatRequest,
    signal: AbortSignal,
    subscriber: { next: (event: StreamEvent) => void; complete: () => void }
  ): Promise<void> {
    const response = await fetch(this.streamUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
      },
      body: JSON.stringify(request),
      signal,
    });

    if (!response.ok) {
      throw new HttpStreamError(
        `Chat stream request failed with status ${response.status}`,
        response.status
      );
    }
    if (!response.body) {
      throw new Error('Chat stream response had no readable body.');
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder('utf-8');
    const parser = new SseParser();

    try {
      while (true) {
        const { value, done } = await reader.read();
        if (done) {
          break;
        }
        // { stream: true } tells TextDecoder to hold back any incomplete
        // multi-byte UTF-8 sequence at the end of this chunk rather than
        // corrupting it, and prepend it to the next decode() call.
        const decoded = decoder.decode(value, { stream: true });
        for (const event of parser.push(decoded)) {
          subscriber.next(event);
          if (event.type === 'COMPLETE' || event.type === 'ERROR') {
            reader.cancel().catch(() => undefined);
            subscriber.complete();
            return;
          }
        }
      }

      // Stream ended without an explicit COMPLETE/ERROR event — flush any
      // trailing buffered (but unterminated) record just in case.
      for (const event of parser.flush()) {
        subscriber.next(event);
      }
      subscriber.complete();
    } finally {
      reader.releaseLock();
    }
  }
}

/** Thrown when the backend responds to the stream request with a non-2xx status. */
export class HttpStreamError extends Error {
  constructor(
    message: string,
    public readonly status: number
  ) {
    super(message);
    this.name = 'HttpStreamError';
  }
}
