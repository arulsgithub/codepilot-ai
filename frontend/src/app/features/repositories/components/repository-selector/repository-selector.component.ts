import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  EventEmitter,
  HostListener,
  Output,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { RepositoryService } from '../../../../core/services/repository.service';
import { Repository, staleLabel } from '../../../../core/models/repository.model';

/**
 * Sidebar control for choosing which repository the app is working in.
 *
 * Choosing sets `RepositoryService.selectedRepository` — the app-wide
 * "current repo" — and chat requests then carry that repository. Choosing
 * "No repository" is an explicit, visible state: chat still works, but as
 * general Q&A with no code context.
 *
 * Reads the shared list straight from RepositoryService so a sync or re-index
 * done anywhere is reflected here immediately.
 */
@Component({
  selector: 'app-repository-selector',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './repository-selector.component.html',
  styleUrl: './repository-selector.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RepositorySelectorComponent {
  readonly repositories = inject(RepositoryService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  /** The user asked to register a new repository. */
  @Output() addRequested = new EventEmitter<void>();
  /** Fired after a choice is applied, with the new id (or null). Lets the page clear legacy attachment state. */
  @Output() repositorySelected = new EventEmitter<string | null>();

  readonly open = signal(false);

  readonly staleLabel = staleLabel;

  toggle(): void {
    this.open.update((value) => !value);
  }

  choose(id: string | null): void {
    this.repositories.select(id);
    this.repositorySelected.emit(id);
    this.open.set(false);
  }

  retry(): void {
    this.repositories.load();
  }

  /** True while this repository has a sync or an index running, so its row can show activity. */
  isBusy(repository: Repository): boolean {
    return (
      this.repositories.syncingId() === repository.id ||
      this.repositories.indexingId() === repository.id
    );
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (this.open() && !this.host.nativeElement.contains(event.target as Node)) {
      this.open.set(false);
    }
  }

  @HostListener('keydown.escape')
  onEscape(): void {
    this.open.set(false);
  }
}
