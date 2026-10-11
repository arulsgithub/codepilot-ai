import { Component, OnInit, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AuthService } from './core/services/auth.service';
import { SignInScreenComponent } from './features/auth/components/sign-in-screen/sign-in-screen.component';

/**
 * Root shell. Gates everything else in the app on `AuthService.currentUser()`:
 *
 *  - `'loading'` — the initial `GET /auth/me` hasn't resolved yet. Shows a
 *    minimal splash, never the chat UI and never an error (a slow network is
 *    not a failure).
 *  - `null` — confirmed signed out. Shows `SignInScreenComponent` and NOTHING
 *    else; there is no app chrome worth showing without a signed-in user.
 *  - a `CurrentUser` — renders the real app via the router.
 *
 * This is also where a 401 anywhere else in the app actually becomes visible
 * as "please sign in": `api.interceptor.ts` and `ChatStateService` both just
 * call `AuthService.clearSession()`, which flips `currentUser` back to
 * `null` and this component reacts by swapping away from whatever was
 * showing — a dialog mid-request included.
 */
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, SignInScreenComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
})
export class AppComponent implements OnInit {
  private readonly auth = inject(AuthService);

  readonly currentUser = this.auth.currentUser;

  ngOnInit(): void {
    this.auth.loadCurrentUser();
  }
}
