import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  IndexRepositoryByIdRequest,
  IndexRepositoryRequest,
  IndexRepositoryResponse,
} from '../models/indexing.model';

/**
 * Thin wrapper around `POST /api/v1/indexing`. Like {@link ConversationService}
 * it holds no application state — the indexing lifecycle (idle / indexing /
 * success / error) lives in
 * features/chat/services/chat-state.service.ts — which keeps this trivially
 * testable with HttpTestingController.
 *
 * Note: the underlying request can take 30-90 seconds. That is fine here:
 * HttpClient returns a cold Observable, so the caller (ChatStateService)
 * simply flips a signal to `indexing` and the UI stays responsive until the
 * response resolves.
 */
@Injectable({ providedIn: 'root' })
export class IndexingService {
  private readonly http = inject(HttpClient);
  private readonly basePath = '/api/v1/indexing';

  /** POST /api/v1/indexing — scan, parse, embed and store a local repo. */
  indexRepository(repositoryRoot: string): Observable<IndexRepositoryResponse> {
    const body: IndexRepositoryRequest = { repositoryRoot };
    return this.http.post<IndexRepositoryResponse>(this.basePath, body);
  }

  /**
   * POST /api/v1/indexing by registry id. SLOW — minutes for a few hundred
   * files, because embeddings are generated locally. The backend also stamps
   * the repository's `lastIndexedAt`, so callers should re-fetch the
   * repository afterwards to clear its stale flag.
   */
  indexRepositoryById(repositoryId: string): Observable<IndexRepositoryResponse> {
    const body: IndexRepositoryByIdRequest = { repositoryId };
    return this.http.post<IndexRepositoryResponse>(this.basePath, body);
  }
}
