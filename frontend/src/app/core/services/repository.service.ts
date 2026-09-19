import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { IndexingService } from './indexing.service';
import {
  IndexResult,
  OperationStatus,
  RegisterGithubRepositoryRequest,
  RegisterLocalRepositoryRequest,
  Repository,
  RepositoryOperationError,
} from '../models/repository.model';
import { describeApiError } from '../../shared/utils/api-error';

const SELECTED_REPOSITORY_STORAGE_KEY = 'codepilot-selected-repository-id';

/**
 * Registry of repositories AND the app-wide "which repository am I working
 * in" state.
 *
 * One service owns both the HTTP wrapper and the signals because every
 * component (sidebar selector, detail panel, add dialog, stale banner, the
 * chat request builder) must observe the SAME list — an update made by any of
 * them (a sync, a re-index) has to show up in all of them at once.
 *
 * Every async action exposes an explicit idle / in-progress / error state.
 * Errors carry the backend's own human-readable `message` verbatim (see
 * {@link describeApiError}); nothing here fails silently.
 */
@Injectable({ providedIn: 'root' })
export class RepositoryService {
  private readonly http = inject(HttpClient);
  private readonly indexingService = inject(IndexingService);
  private readonly basePath = '/api/v1/repositories';

  // ---- The shared list ----
  private readonly _repositories = signal<Repository[]>([]);
  readonly repositories = this._repositories.asReadonly();

  private readonly _listStatus = signal<OperationStatus>('idle');
  readonly listStatus = this._listStatus.asReadonly();
  private readonly _listError = signal<string | null>(null);
  readonly listError = this._listError.asReadonly();

  // ---- Selection ----
  private readonly _selectedRepositoryId = signal<string | null>(this.readStoredSelection());
  readonly selectedRepositoryId = this._selectedRepositoryId.asReadonly();

  /**
   * The selected repository, or null. Derived from the list, so a restored id
   * that no longer exists resolves to null on its own; {@link load} also
   * clears the stale id from storage.
   */
  readonly selectedRepository = computed<Repository | null>(() => {
    const id = this._selectedRepositoryId();
    return id ? (this._repositories().find((r) => r.id === id) ?? null) : null;
  });

  // ---- Add (register / clone) ----
  private readonly _addStatus = signal<OperationStatus>('idle');
  readonly addStatus = this._addStatus.asReadonly();
  private readonly _addError = signal<string | null>(null);
  readonly addError = this._addError.asReadonly();

  // ---- Sync (fast) ----
  private readonly _syncingId = signal<string | null>(null);
  readonly syncingId = this._syncingId.asReadonly();
  private readonly _syncError = signal<RepositoryOperationError | null>(null);
  readonly syncError = this._syncError.asReadonly();

  // ---- Index (slow) ----
  private readonly _indexingId = signal<string | null>(null);
  readonly indexingId = this._indexingId.asReadonly();
  readonly isIndexing = computed(() => this._indexingId() !== null);
  private readonly _indexError = signal<RepositoryOperationError | null>(null);
  readonly indexError = this._indexError.asReadonly();
  private readonly _indexResult = signal<IndexResult | null>(null);
  readonly indexResult = this._indexResult.asReadonly();

  // ---------------------------------------------------------------------
  // List
  // ---------------------------------------------------------------------

  /** GET /api/v1/repositories, then re-validate the persisted selection. */
  load(): void {
    this._listStatus.set('in-progress');
    this._listError.set(null);
    this.http.get<Repository[]>(this.basePath).subscribe({
      next: (repositories) => {
        this._repositories.set(repositories);
        this._listStatus.set('idle');
        this.validateSelection();
      },
      error: (err: unknown) => {
        this._listStatus.set('error');
        this._listError.set(describeApiError(err, 'Could not load repositories'));
      },
    });
  }

  /** Select a repository (or `null` for none) and persist the choice. Unknown ids are ignored. */
  select(id: string | null): void {
    if (id !== null && !this._repositories().some((r) => r.id === id)) {
      return;
    }
    this._selectedRepositoryId.set(id);
    this.persistSelection(id);
  }

  // ---------------------------------------------------------------------
  // Register
  // ---------------------------------------------------------------------

  /** POST /api/v1/repositories/local — register a folder already on the backend machine. */
  registerLocal(
    request: RegisterLocalRepositoryRequest,
    onRegistered?: (repository: Repository) => void
  ): void {
    this.runRegister(
      this.http.post<Repository>(`${this.basePath}/local`, this.compact(request)),
      'Could not add that folder',
      onRegistered
    );
  }

  /**
   * POST /api/v1/repositories/github — clones server-side, so it can take
   * 10-60s on a large repository. `addStatus` stays `in-progress` for the whole
   * time so the dialog can show an honest progress state.
   */
  registerGithub(
    request: RegisterGithubRepositoryRequest,
    onRegistered?: (repository: Repository) => void
  ): void {
    this.runRegister(
      this.http.post<Repository>(`${this.basePath}/github`, this.compact(request)),
      'Could not clone that repository',
      onRegistered
    );
  }

  /** Clears a previous add failure, e.g. when the dialog is (re)opened. */
  clearAddError(): void {
    if (this._addStatus() === 'error') {
      this._addStatus.set('idle');
    }
    this._addError.set(null);
  }

  // ---------------------------------------------------------------------
  // Sync (GITHUB only, fast)
  // ---------------------------------------------------------------------

  /** POST /api/v1/repositories/{id}/sync — fetches new commits; does NOT re-index. */
  sync(repositoryId: string): void {
    if (this._syncingId() !== null || this._indexingId() === repositoryId) {
      return;
    }
    this._syncingId.set(repositoryId);
    this._syncError.set(null);
    this.http.post<Repository>(`${this.basePath}/${repositoryId}/sync`, {}).subscribe({
      next: (updated) => {
        this.upsert(updated);
        this._syncingId.set(null);
      },
      error: (err: unknown) => {
        this._syncingId.set(null);
        this._syncError.set({
          repositoryId,
          message: describeApiError(err, 'Sync failed'),
        });
      },
    });
  }

  // ---------------------------------------------------------------------
  // Index (slow — minutes)
  // ---------------------------------------------------------------------

  /**
   * POST /api/v1/indexing {repositoryId}. Runs for minutes; `indexingId`
   * identifies which repository is being indexed so the UI can show progress
   * anywhere without blocking. On success the repository is re-fetched so its
   * `lastIndexedAt` / `indexStale` reflect the backend, which is what clears
   * the stale banner.
   */
  index(repositoryId: string): void {
    if (this._indexingId() !== null || this._syncingId() === repositoryId) {
      return;
    }
    this._indexingId.set(repositoryId);
    this._indexError.set(null);
    this._indexResult.set(null);

    this.indexingService.indexRepositoryById(repositoryId).subscribe({
      next: (result) => {
        this._indexResult.set({ repositoryId, chunksIndexed: result.chunksIndexed });
        this.refresh(repositoryId, () => this._indexingId.set(null));
      },
      error: (err: unknown) => {
        this._indexingId.set(null);
        this._indexError.set({
          repositoryId,
          message: describeApiError(err, 'Indexing failed'),
        });
      },
    });
  }

  /** Dismisses the "Indexed N chunks" / failure note for the current repository. */
  dismissIndexOutcome(): void {
    this._indexResult.set(null);
    this._indexError.set(null);
  }

  // ---------------------------------------------------------------------
  // Internals
  // ---------------------------------------------------------------------

  /** GET /api/v1/repositories/{id} and replace that row. */
  private refresh(repositoryId: string, done: () => void): void {
    this.http.get<Repository>(`${this.basePath}/${repositoryId}`).subscribe({
      next: (fresh) => {
        this.upsert(fresh);
        done();
      },
      error: () => {
        // The index itself succeeded; only the re-fetch failed. Patch the row
        // locally so the stale banner does not keep nagging about an index we
        // know was just rebuilt.
        this._repositories.update((list) =>
          list.map((r) =>
            r.id === repositoryId
              ? { ...r, lastIndexedAt: new Date().toISOString(), indexStale: false }
              : r
          )
        );
        done();
      },
    });
  }

  private runRegister(
    request$: Observable<Repository>,
    fallbackMessage: string,
    onRegistered?: (repository: Repository) => void
  ): void {
    if (this._addStatus() === 'in-progress') {
      return;
    }
    this._addStatus.set('in-progress');
    this._addError.set(null);
    request$.subscribe({
      next: (repository) => {
        this.upsert(repository);
        this.select(repository.id);
        this._addStatus.set('idle');
        onRegistered?.(repository);
      },
      error: (err: unknown) => {
        this._addStatus.set('error');
        this._addError.set(describeApiError(err, fallbackMessage));
      },
    });
  }

  /** Insert-or-replace by id, keeping list order stable for existing rows. */
  private upsert(repository: Repository): void {
    this._repositories.update((list) =>
      list.some((r) => r.id === repository.id)
        ? list.map((r) => (r.id === repository.id ? repository : r))
        : [repository, ...list]
    );
  }

  /** Drop blank optional fields so the backend applies its own defaults. */
  private compact<T extends object>(request: T): Partial<T> {
    const out: Partial<T> = {};
    for (const [key, value] of Object.entries(request)) {
      const trimmed = typeof value === 'string' ? value.trim() : value;
      if (trimmed !== '' && trimmed !== undefined && trimmed !== null) {
        (out as Record<string, unknown>)[key] = trimmed;
      }
    }
    return out;
  }

  /** A repository may have been removed server-side since we last stored the id. */
  private validateSelection(): void {
    const id = this._selectedRepositoryId();
    if (id !== null && !this._repositories().some((r) => r.id === id)) {
      this._selectedRepositoryId.set(null);
      this.persistSelection(null);
    }
  }

  private readStoredSelection(): string | null {
    try {
      const stored = localStorage.getItem(SELECTED_REPOSITORY_STORAGE_KEY);
      return stored && stored.trim() ? stored : null;
    } catch {
      return null;
    }
  }

  private persistSelection(id: string | null): void {
    try {
      if (id) {
        localStorage.setItem(SELECTED_REPOSITORY_STORAGE_KEY, id);
      } else {
        localStorage.removeItem(SELECTED_REPOSITORY_STORAGE_KEY);
      }
    } catch {
      // Storage unavailable — the selection just won't survive a reload.
    }
  }
}
