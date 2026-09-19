import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RepositoryService } from '../../../../core/services/repository.service';
import { formatAbsoluteTime, formatRelativeTime } from '../../../../shared/utils/relative-time';

/** How often relative times ("2 hours ago") are recomputed while the page sits open. */
const RELATIVE_TIME_TICK_MS = 60_000;

/**
 * Detail card for the currently selected repository, with the two actions
 * that operate on it:
 *
 *  - **Sync** (GITHUB only — a local folder has no remote): fast, fetches new
 *    commits, does not re-index.
 *  - **Re-index**: slow (minutes — embeddings are generated locally). It asks
 *    for confirmation first, then shows an indeterminate progress bar. It
 *    never blocks the rest of the UI; conversations stay readable.
 *
 * Both actions show idle / in-progress / error explicitly and surface the
 * backend's own error message.
 */
@Component({
  selector: 'app-repository-details',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './repository-details.component.html',
  styleUrl: './repository-details.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RepositoryDetailsComponent {
  readonly repositories = inject(RepositoryService);

  readonly repository = this.repositories.selectedRepository;

  /** Ticks so relative times stay fresh; read inside the template helpers below. */
  private readonly now = signal(Date.now());

  readonly collapsed = signal(false);

  /** The repository id the user is being asked to confirm a re-index for, if any. */
  private readonly confirmingFor = signal<string | null>(null);
  readonly confirmingReindex = computed(
    () => this.confirmingFor() !== null && this.confirmingFor() === this.repository()?.id
  );

  readonly isSyncing = computed(() => {
    const repo = this.repository();
    return !!repo && this.repositories.syncingId() === repo.id;
  });

  readonly isIndexing = computed(() => {
    const repo = this.repository();
    return !!repo && this.repositories.indexingId() === repo.id;
  });

  /** Another repository is being indexed — starting a second run would just compete for the embedder. */
  readonly otherIndexRunning = computed(
    () => this.repositories.isIndexing() && !this.isIndexing()
  );

  readonly syncError = computed(() => {
    const error = this.repositories.syncError();
    return error && error.repositoryId === this.repository()?.id ? error.message : null;
  });

  readonly indexError = computed(() => {
    const error = this.repositories.indexError();
    return error && error.repositoryId === this.repository()?.id ? error.message : null;
  });

  readonly indexResult = computed(() => {
    const result = this.repositories.indexResult();
    return result && result.repositoryId === this.repository()?.id ? result : null;
  });

  constructor() {
    const timer = setInterval(() => this.now.set(Date.now()), RELATIVE_TIME_TICK_MS);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }

  relative(iso: string | null): string {
    return formatRelativeTime(iso, this.now());
  }

  absolute(iso: string | null): string {
    return formatAbsoluteTime(iso);
  }

  shortCommit(commit: string | null): string {
    return commit ? commit.slice(0, 8) : '';
  }

  /** Only https URLs become links — the backend enforces this, the check is defence in depth. */
  isLinkable(url: string | null): url is string {
    return !!url && url.startsWith('https://');
  }

  toggleCollapsed(): void {
    this.collapsed.update((value) => !value);
  }

  sync(): void {
    const repo = this.repository();
    if (repo) {
      this.repositories.sync(repo.id);
    }
  }

  askReindex(): void {
    this.confirmingFor.set(this.repository()?.id ?? null);
  }

  cancelReindex(): void {
    this.confirmingFor.set(null);
  }

  confirmReindex(): void {
    const repo = this.repository();
    this.confirmingFor.set(null);
    if (repo) {
      this.repositories.index(repo.id);
    }
  }

  dismissIndexOutcome(): void {
    this.repositories.dismissIndexOutcome();
  }
}
