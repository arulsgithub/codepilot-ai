import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { AuthService } from '../../../../core/services/auth.service';

/**
 * Full-screen gate shown whenever `AuthService.currentUser()` is `null`. It
 * is deliberately the ONLY thing rendered in that state — no sidebar, no
 * composer, no suggestion chips — because none of that app chrome means
 * anything without a signed-in user to scope it to.
 *
 * `connectionError()` is shown here too (rather than needing its own
 * screen): a visitor who can't reach the backend at all should see THAT,
 * not just a sign-in button that will fail the same way GitHub's redirect
 * lands them back here.
 */
@Component({
  selector: 'app-sign-in-screen',
  standalone: true,
  imports: [],
  templateUrl: './sign-in-screen.component.html',
  styleUrl: './sign-in-screen.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SignInScreenComponent {
  private readonly auth = inject(AuthService);

  readonly connectionError = this.auth.connectionError;

  signIn(): void {
    this.auth.signIn();
  }
}
