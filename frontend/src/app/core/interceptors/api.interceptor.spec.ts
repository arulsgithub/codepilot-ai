import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { apiInterceptor } from './api.interceptor';
import { AuthService } from '../services/auth.service';

describe('apiInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let authService: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([apiInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService);
  });

  afterEach(() => httpMock.verify());

  it('rewrites a relative /api/... URL to the configured base and sends credentials', () => {
    http.get('/api/v1/conversations').subscribe();

    const req = httpMock.expectOne('/api/v1/conversations');
    expect(req.request.withCredentials).toBeTrue();
    req.flush([]);
  });

  it('leaves a non-/api URL untouched', () => {
    http.get('/assets/thing.json').subscribe();

    httpMock.expectOne('/assets/thing.json').flush({});
  });

  it('clears the session on a 401 from any request, then still propagates the error', () => {
    let caught: unknown;
    http.get('/api/v1/conversations').subscribe({ error: (err) => (caught = err) });

    httpMock.expectOne('/api/v1/conversations').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(authService.currentUser()).toBeNull();
    expect(caught).toBeTruthy();
  });

  it('does not touch the session on a non-401 error', () => {
    http.get('/api/v1/conversations').subscribe({ error: () => undefined });

    httpMock.expectOne('/api/v1/conversations').flush(null, { status: 500, statusText: 'Server Error' });

    // Untouched means still its default - 'loading' - since nothing set it.
    expect(authService.currentUser()).toBe('loading');
  });
});
