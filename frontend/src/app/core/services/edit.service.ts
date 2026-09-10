import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  ApplyEditsRequest,
  ApplyEditsResponse,
  EditPlanRequest,
  EditPlanResponse,
} from '../models/edit.model';

/**
 * Thin wrapper around the two code-editing endpoints. Like
 * {@link ConversationService} and {@link IndexingService} it holds no
 * application state — the edit lifecycle (idle / planning / reviewing /
 * applying / applied / conflict / error) lives in
 * features/chat/services/edit-state.service.ts. That keeps this trivially
 * testable in isolation with HttpTestingController.
 *
 * Both requests are ordinary JSON POSTs, so unlike the chat stream they go
 * through Angular's HttpClient (and the `/api` → apiBaseUrl interceptor).
 * `planEdits` is slow — the backend runs a reasoning model, 10–40s — but that
 * is fine here: HttpClient returns a cold Observable and the caller simply
 * flips a signal while it is in flight.
 */
@Injectable({ providedIn: 'root' })
export class EditService {
  private readonly http = inject(HttpClient);
  private readonly basePath = '/api/v1/edits';

  /** POST /api/v1/edits/plan — reason about an instruction; writes nothing. */
  planEdits(repositoryRoot: string, instruction: string): Observable<EditPlanResponse> {
    const body: EditPlanRequest = { repositoryRoot, instruction };
    return this.http.post<EditPlanResponse>(`${this.basePath}/plan`, body);
  }

  /**
   * POST /api/v1/edits/apply — write the approved edits to disk.
   *
   * A 409 response (a conflict, e.g. the file changed on disk since planning)
   * surfaces as an `HttpErrorResponse` whose `.error` is an
   * {@link ApplyEditsResponse} with `applied: false` and a populated
   * `problems` array. The caller is responsible for treating that as an
   * expected outcome rather than a generic failure.
   */
  applyEdits(request: ApplyEditsRequest): Observable<ApplyEditsResponse> {
    return this.http.post<ApplyEditsResponse>(`${this.basePath}/apply`, request);
  }
}
