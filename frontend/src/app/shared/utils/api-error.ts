import { HttpErrorResponse } from '@angular/common/http';

/** Shape of every error body the backend returns (`ApiErrorResponse`). */
export interface ApiErrorBody {
  timestamp?: string;
  status?: number;
  code?: string;
  message?: string;
  path?: string;
}

/**
 * Turns a failed HTTP call into text that is safe to show the user.
 *
 * The backend writes its `message` field for humans on purpose (e.g. "Git
 * clone failed: ... requires authentication. Set the GITHUB_TOKEN environment
 * variable ..."), so when one is present it is surfaced VERBATIM — never
 * replaced by a generic "something went wrong". Only when there is no usable
 * message (network down, non-JSON error page) does this fall back.
 */
export function describeApiError(err: unknown, fallback: string): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 0) {
      return 'Unable to reach the CodePilot backend. Please make sure it is running.';
    }
    const body = err.error as ApiErrorBody | string | null;
    if (body && typeof body === 'object' && typeof body.message === 'string' && body.message.trim()) {
      return body.message;
    }
    return `${fallback} (HTTP ${err.status})`;
  }
  if (err instanceof Error && err.message) {
    return err.message;
  }
  return fallback;
}
