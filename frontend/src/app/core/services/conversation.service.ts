import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Conversation, CreateConversationRequest } from '../models/conversation.model';

/**
 * Thin wrapper around the conversation CRUD endpoints. Deliberately has no
 * knowledge of application state (signals) — that lives in
 * features/chat/services/chat-state.service.ts. This keeps the HTTP layer
 * trivially testable in isolation with HttpTestingController.
 */
@Injectable({ providedIn: 'root' })
export class ConversationService {
  private readonly http = inject(HttpClient);
  private readonly basePath = '/api/v1/conversations';

  /** POST /api/v1/conversations */
  createConversation(title: string): Observable<Conversation> {
    const body: CreateConversationRequest = { title };
    return this.http.post<Conversation>(this.basePath, body);
  }

  /** GET /api/v1/conversations */
  listConversations(): Observable<Conversation[]> {
    return this.http.get<Conversation[]>(this.basePath);
  }

  /** GET /api/v1/conversations/{id} */
  getConversation(conversationId: string): Observable<Conversation> {
    return this.http.get<Conversation>(`${this.basePath}/${conversationId}`);
  }

  /** DELETE /api/v1/conversations/{id} */
  deleteConversation(conversationId: string): Observable<void> {
    return this.http.delete<void>(`${this.basePath}/${conversationId}`);
  }
}
