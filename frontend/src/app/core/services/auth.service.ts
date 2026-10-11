import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { AuthStatus, CurrentUser } from '../models/auth.model';
import { describeApiError } from '../../shared/utils/api-error';

/**
 * Owns the app-wide "who is signed in" state. Every other feature — chat,
 * repositories, the header — reads `currentUser()` rather than calling
 * `/auth/me` itself, and the root `AppComponent` is the one place that
 * decides what to render for each of its three states (see AuthStatus).
 *
 * `clearSession()` is the single place a 401 anywhere in the app ends up:
 * `api.interceptor.ts` calls it for every HttpClient request, and
 * `ChatStateService` calls it for the one request that bypasses the
 * interceptor entirely (the raw `fetch()` SSE stream in ChatService). Either
 * way the effect is the same — `currentUser` becomes `null` and
 * `AppComponent` swaps to the sign-in screen — so a 401 is never rendered as
 * a scary "something went wrong" banner.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  private readonly _currentUser = signal<AuthStatus>('loading');
  readonly currentUser = this._currentUser.asReadonly();

  /**
   * Set only when the backend could not be reached at all (status 0) while
   * loading `/auth/me` — distinct from a plain 401, which is NOT an error
   * and clears this. The sign-in screen shows this alongside its button so
   * "I can't sign in" and "there is no backend" don't look identical.
   */
  private readonly _connectionError = signal<string | null>(null);
  readonly connectionError = this._connectionError.asReadonly();

  /** GET /api/v1/auth/me. Call once, on app startup, before anything else renders. */
  loadCurrentUser(): void {
    this._currentUser.set('loading');
    this._connectionError.set(null);
    this.http.get<CurrentUser>('/api/v1/auth/me').subscribe({
      next: (user) => this._currentUser.set(user),
      error: (err: unknown) => {
        if (err instanceof HttpErrorResponse && err.status === 401) {
          // Normal and expected for a first-time or logged-out visitor — not
          // an error, so no connectionError and no console noise.
          this._currentUser.set(null);
          return;
        }
        this._currentUser.set(null);
        this._connectionError.set(
          describeApiError(err, 'Unable to reach the CodePilot backend. Please make sure it is running.')
        );
      },
    });
  }

  /**
   * A full-page navigation to the backend's GitHub OAuth2 login endpoint —
   * deliberately NOT an XHR. OAuth is a redirect dance through github.com;
   * fetch/XHR cannot follow it (and would fail on CORS trying).
   */
  signIn(): void {
    this.navigateToSignIn();
  }

  /**
   * Isolated into its own one-line method so a test can stub the actual
   * navigation: assigning `window.location.href` for real would navigate the
   * test browser itself away.
   */
  private navigateToSignIn(): void {
    window.location.href = '/oauth2/authorization/github';
  }

  /** POST /api/v1/auth/logout, then show the sign-in screen regardless of the outcome. */
  signOut(): void {
    this.http.post<void>('/api/v1/auth/logout', {}).subscribe({
      next: () => this.clearSession(),
      // Logging out client-side is the point of this action; if the network
      // call itself failed there is nothing more useful to do than clear the
      // session anyway and let the user sign in again.
      error: () => this.clearSession(),
    });
  }

  /** Marks the session as gone. See the class doc for who calls this and why. */
  clearSession(): void {
    this._currentUser.set(null);
  }
}
