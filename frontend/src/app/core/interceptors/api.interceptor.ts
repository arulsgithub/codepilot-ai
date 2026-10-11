import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from '../services/auth.service';

/**
 * Rewrites any request made to a relative `/api/...` URL so it points at
 * `environment.apiBaseUrl`, and reacts to a 401 from ANY request by clearing
 * the signed-in session.
 *
 * The URL rewrite is what lets every service in the app call
 * `this.http.get('/api/v1/conversations')` without ever hardcoding
 * `http://localhost:8080` — the base URL lives in exactly one place
 * (src/environments/environment*.ts).
 *
 * The 401 handling is what keeps a session timeout from ever reaching a
 * feature service as "something went wrong": `AuthService.clearSession()`
 * flips `currentUser` to `null`, which `AppComponent` reads to swap the
 * whole app over to the sign-in screen. Individual services still get the
 * error on their own subscription afterwards (so their own idle/error state
 * resets correctly) but by then nothing of theirs is being rendered any
 * more, so it never becomes a visible generic error banner.
 *
 * Note: this interceptor only applies to Angular's HttpClient. The raw
 * `fetch()` call used for SSE streaming (see chat.service.ts) is NOT routed
 * through HttpClient — by design, since HttpClient cannot stream a POST
 * response body incrementally — so it reads `environment.apiBaseUrl`
 * directly, and ChatStateService calls `AuthService.clearSession()` itself
 * when that stream's own 401 handling (`UnauthenticatedError`) surfaces.
 */
export const apiInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const outgoing = req.url.startsWith('/api')
    ? req.clone({ url: `${environment.apiBaseUrl}${req.url}`, withCredentials: true })
    : req;

  return next(outgoing).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse && err.status === 401) {
        authService.clearSession();
      }
      return throwError(() => err);
    })
  );
};
