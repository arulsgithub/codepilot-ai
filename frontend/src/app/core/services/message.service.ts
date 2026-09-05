import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Message } from '../models/message.model';

/**
 * Read-only access to persisted message history.
 *
 * Deliberately does NOT expose a "send message" method: per the backend
 * contract, sending a message is done exclusively via
 * `POST /api/v1/chat/stream` (see chat.service.ts), which both persists the
 * user message and streams the assistant reply. Calling a separate
 * "create message" endpoint here would double-write the user's message —
 * see the "Prevent Duplicate Messages" requirement in the project brief.
 */
@Injectable({ providedIn: 'root' })
export class MessageService {
  private readonly http = inject(HttpClient);

  /** GET /api/v1/conversations/{conversationId}/messages */
  getMessages(conversationId: string): Observable<Message[]> {
    return this.http.get<Message[]>(`/api/v1/conversations/${conversationId}/messages`);
  }
}
