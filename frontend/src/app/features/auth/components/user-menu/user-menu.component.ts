import { ChangeDetectionStrategy, Component, ElementRef, HostListener, computed, inject, signal } from '@angular/core';
import { AuthService } from '../../../../core/services/auth.service';
import { CurrentUser } from '../../../../core/models/auth.model';

/**
 * Header control shown once signed in: the user's avatar + GitHub login,
 * with a dropdown whose only entry is Sign out.
 *
 * Injects AuthService directly rather than taking the user as an `@Input` —
 * this mirrors how RepositorySelectorComponent/RepositoryDetailsComponent
 * read RepositoryService directly, since "who is signed in" is app-wide
 * state with exactly one source of truth, not something worth threading
 * through ChatHeaderComponent's inputs.
 *
 * Renders nothing if `currentUser()` is not a real user — `AppComponent`
 * only mounts the header while signed in anyway, but this keeps the
 * component safe to use standalone (e.g. in tests) without that guarantee.
 */
@Component({
  selector: 'app-user-menu',
  standalone: true,
  imports: [],
  templateUrl: './user-menu.component.html',
  styleUrl: './user-menu.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserMenuComponent {
  private readonly auth = inject(AuthService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  /** Narrowed to an actual user — 'loading' and null both render nothing. */
  readonly user = computed<CurrentUser | null>(() => {
    const value = this.auth.currentUser();
    return value === 'loading' ? null : value;
  });
  readonly open = signal(false);

  initial(user: CurrentUser): string {
    return (user.name ?? user.login).charAt(0).toUpperCase();
  }

  toggle(): void {
    this.open.update((value) => !value);
  }

  signOut(): void {
    this.open.set(false);
    this.auth.signOut();
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
