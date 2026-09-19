import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RepositoryService } from '../../../../core/services/repository.service';

/**
 * Warns above the composer when the selected repository's index is out of
 * date. This matters more than it looks: a stale index yields confident
 * answers about OLD code, and nothing on screen looks wrong — so the warning
 * is loud, and it carries the fix (Re-index now) with it.
 *
 * Dismissal is remembered per "staleness episode" (repository + its
 * last-indexed/last-synced stamps), in memory only. Dismissing hides the
 * nag, but a fresh sync that makes the index stale again brings it back.
 *
 * Re-index is slow, so — same as the detail panel — it asks first. While a
 * run is going the banner shows an indeterminate progress bar, and a failure
 * stays visible here with the backend's message; the banner never closes on
 * an error it has not shown.
 */
@Component({
  selector: 'app-stale-index-banner',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './stale-index-banner.component.html',
  styleUrl: './stale-index-banner.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StaleIndexBannerComponent {
  readonly repositories = inject(RepositoryService);

  readonly repository = this.repositories.selectedRepository;

  private readonly dismissedKeys = signal<string[]>([]);
  private readonly confirmingFor = signal<string | null>(null);

  readonly confirming = computed(
    () => this.confirmingFor() !== null && this.confirmingFor() === this.repository()?.id
  );

  readonly indexing = computed(() => {
    const repo = this.repository();
    return !!repo && this.repositories.indexingId() === repo.id;
  });

  readonly otherIndexRunning = computed(() => this.repositories.isIndexing() && !this.indexing());

  readonly indexError = computed(() => {
    const error = this.repositories.indexError();
    return error && error.repositoryId === this.repository()?.id ? error.message : null;
  });

  /** Identifies one staleness episode, so a later, different one is not pre-dismissed. */
  private readonly episodeKey = computed(() => {
    const repo = this.repository();
    return repo ? `${repo.id}|${repo.lastIndexedAt ?? 'never'}|${repo.lastSyncedAt ?? 'never'}` : null;
  });

  readonly neverIndexed = computed(() => this.repository()?.lastIndexedAt === null);

  readonly visible = computed(() => {
    const repo = this.repository();
    if (!repo) {
      return false;
    }
    // Progress and failures are always shown, even if the nag was dismissed.
    if (this.indexing() || this.indexError()) {
      return true;
    }
    const key = this.episodeKey();
    return repo.indexStale && key !== null && !this.dismissedKeys().includes(key);
  });

  dismiss(): void {
    const key = this.episodeKey();
    if (key) {
      this.dismissedKeys.update((keys) => [...keys, key]);
    }
    this.confirmingFor.set(null);
    this.repositories.dismissIndexOutcome();
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
}
