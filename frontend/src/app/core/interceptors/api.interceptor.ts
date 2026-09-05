import { HttpInterceptorFn } from '@angular/common/http';
import { environment } from '../../../environments/environment';

/**
 * Rewrites any request made to a relative `/api/...` URL so it points at
 * `environment.apiBaseUrl`. This is what lets every service in the app call
 * `this.http.get('/api/v1/conversations')` without ever hardcoding
 * `http://localhost:8080` — the base URL lives in exactly one place
 * (src/environments/environment*.ts).
 *
 * Note: this interceptor only applies to Angular's HttpClient. The raw
 * `fetch()` call used for SSE streaming (see chat.service.ts) is NOT routed
 * through HttpClient — by design, since HttpClient cannot stream a POST
 * response body incrementally — so it reads `environment.apiBaseUrl`
 * directly instead.
 */
export const apiInterceptor: HttpInterceptorFn = (req, next) => {
  if (req.url.startsWith('/api')) {
    return next(req.clone({ url: `${environment.apiBaseUrl}${req.url}` }));
  }
  return next(req);
};
