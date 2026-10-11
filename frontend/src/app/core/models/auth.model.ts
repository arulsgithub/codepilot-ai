/**
 * Contract for the GitHub-OAuth session endpoints:
 *  - `GET  /api/v1/auth/me`      — who am I? 200 = signed in, 401 = not signed in
 *  - `POST /api/v1/auth/logout`  — end the session (204)
 *
 * Signing IN is not an endpoint the frontend calls with HttpClient at all — it is
 * a full-page navigation to `/oauth2/authorization/github`, which is Spring
 * Security's own redirect-to-GitHub entry point. See AuthService.signIn().
 */

/** The signed-in user, as returned by `GET /api/v1/auth/me`. */
export interface CurrentUser {
  id: string;
  login: string;
  name: string | null;
  avatarUrl: string | null;
}

/**
 * AuthService.currentUser's three states:
 *  - `'loading'` — the initial `/auth/me` check hasn't resolved yet
 *  - `null`      — confirmed signed out (a real 401, or sign-out)
 *  - `CurrentUser` — confirmed signed in
 *
 * There is deliberately no fourth "error" state: a backend that cannot be
 * reached at all is reported through `AuthService.connectionError` instead,
 * alongside `currentUser() === null`, so the sign-in screen can show both at
 * once rather than the app needing a whole separate error state to render.
 */
export type AuthStatus = CurrentUser | null | 'loading';
