import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  EventEmitter,
  HostListener,
  Input,
  Output,
  ViewChild,
  computed,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RepositoryService } from '../../../../core/services/repository.service';

export type AddRepositoryTab = 'local' | 'github';

/**
 * Modal for registering a repository, from a local folder or a GitHub URL.
 *
 * While a request is running the whole form is locked and a progress state is
 * shown — for GitHub it says outright that the server is cloning and may take
 * a minute, because a silent 60s wait reads as a frozen app. The dialog cannot
 * be dismissed mid-request (the request would keep running with nothing on
 * screen to report its result).
 *
 * On failure the dialog STAYS OPEN and shows the backend's own message inline
 * (e.g. the "requires authentication. Set GITHUB_TOKEN ..." text) — never a
 * generic "something went wrong".
 */
@Component({
  selector: 'app-add-repository-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './add-repository-dialog.component.html',
  styleUrl: './add-repository-dialog.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AddRepositoryDialogComponent {
  readonly repositories = inject(RepositoryService);

  @Input() set open(value: boolean) {
    const wasOpen = this._open;
    this._open = value;
    if (value && !wasOpen) {
      this.resetForm();
      this.repositories.clearAddError();
      setTimeout(() => this.firstField?.nativeElement.focus());
    }
  }
  get open(): boolean {
    return this._open;
  }
  private _open = false;

  @Output() closeRequested = new EventEmitter<void>();

  @ViewChild('firstField') firstField?: ElementRef<HTMLInputElement>;

  readonly tab = signal<AddRepositoryTab>('local');
  readonly busy = computed(() => this.repositories.addStatus() === 'in-progress');
  readonly errorMessage = computed(() =>
    this.repositories.addStatus() === 'error' ? this.repositories.addError() : null
  );

  // Form fields (plain properties bound with ngModel).
  localPath = '';
  localName = '';
  githubUrl = '';
  githubBranch = '';
  githubName = '';

  /** True once the URL field has content that isn't an https:// URL. */
  get githubUrlInvalid(): boolean {
    const url = this.githubUrl.trim();
    return url.length > 0 && !url.startsWith('https://');
  }

  get canSubmit(): boolean {
    if (this.busy()) {
      return false;
    }
    return this.tab() === 'local'
      ? this.localPath.trim().length > 0
      : this.githubUrl.trim().length > 0 && !this.githubUrlInvalid;
  }

  selectTab(tab: AddRepositoryTab): void {
    if (this.busy() || this.tab() === tab) {
      return;
    }
    this.tab.set(tab);
    // A failure from the other tab's attempt does not describe this one.
    this.repositories.clearAddError();
    setTimeout(() => this.firstField?.nativeElement.focus());
  }

  submit(): void {
    if (!this.canSubmit) {
      return;
    }
    const onRegistered = () => {
      this.resetForm();
      this.closeRequested.emit();
    };

    if (this.tab() === 'local') {
      this.repositories.registerLocal(
        { rootPath: this.localPath, name: this.localName },
        onRegistered
      );
    } else {
      this.repositories.registerGithub(
        { remoteUrl: this.githubUrl, branch: this.githubBranch, name: this.githubName },
        onRegistered
      );
    }
  }

  close(): void {
    if (this.busy()) {
      return;
    }
    this.closeRequested.emit();
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this._open) {
      this.close();
    }
  }

  private resetForm(): void {
    this.localPath = '';
    this.localName = '';
    this.githubUrl = '';
    this.githubBranch = '';
    this.githubName = '';
  }
}
