import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from './auth.service';
import { CurrentUser } from '../models/auth.model';

const USER: CurrentUser = {
  id: 'u-1',
  login: 'octocat',
  name: 'The Octocat',
  avatarUrl: 'https://avatars.githubusercontent.com/u/1',
};

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('starts in the loading state', () => {
    expect(service.currentUser()).toBe('loading');
  });

  describe('loadCurrentUser', () => {
    it('sets the signed-in user on 200', () => {
      service.loadCurrentUser();
      http.expectOne('/api/v1/auth/me').flush(USER);

      expect(service.currentUser()).toEqual(USER);
      expect(service.connectionError()).toBeNull();
    });

    it('sets null (not an error) on a 401 - a logged-out visitor is normal', () => {
      service.loadCurrentUser();
      http.expectOne('/api/v1/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });

      expect(service.currentUser()).toBeNull();
      expect(service.connectionError()).toBeNull();
    });

    it('sets null AND a connection error when the backend cannot be reached at all', () => {
      service.loadCurrentUser();
      http.expectOne('/api/v1/auth/me').error(new ProgressEvent('error'), { status: 0 });

      expect(service.currentUser()).toBeNull();
      expect(service.connectionError()).toContain('Unable to reach the CodePilot backend');
    });

    it('surfaces the backend message for an unexpected failure', () => {
      service.loadCurrentUser();
      http
        .expectOne('/api/v1/auth/me')
        .flush({ message: 'Database is unavailable' }, { status: 500, statusText: 'Server Error' });

      expect(service.currentUser()).toBeNull();
      expect(service.connectionError()).toBe('Database is unavailable');
    });

    it('resets to loading, clearing a previous connection error, each time it is called', () => {
      service.loadCurrentUser();
      http.expectOne('/api/v1/auth/me').error(new ProgressEvent('error'), { status: 0 });
      expect(service.connectionError()).toBeTruthy();

      service.loadCurrentUser();
      expect(service.currentUser()).toBe('loading');
      expect(service.connectionError()).toBeNull();
      http.expectOne('/api/v1/auth/me').flush(USER);
    });
  });

  describe('signOut', () => {
    it('POSTs /auth/logout and clears the session on success', () => {
      service.loadCurrentUser();
      http.expectOne('/api/v1/auth/me').flush(USER);

      service.signOut();
      const req = http.expectOne('/api/v1/auth/logout');
      expect(req.request.method).toBe('POST');
      req.flush(null, { status: 204, statusText: 'No Content' });

      expect(service.currentUser()).toBeNull();
    });

    it('still clears the session if the logout request itself fails', () => {
      service.loadCurrentUser();
      http.expectOne('/api/v1/auth/me').flush(USER);

      service.signOut();
      http
        .expectOne('/api/v1/auth/logout')
        .flush(null, { status: 500, statusText: 'Server Error' });

      expect(service.currentUser()).toBeNull();
    });
  });

  it('clearSession sets currentUser to null regardless of the prior state', () => {
    service.loadCurrentUser();
    http.expectOne('/api/v1/auth/me').flush(USER);

    service.clearSession();

    expect(service.currentUser()).toBeNull();
  });

  it('signIn triggers a full-page navigation to the GitHub OAuth2 endpoint, not an XHR', () => {
    // Actually assigning window.location.href would navigate this test's
    // browser tab away, so the navigation itself (a private one-line method)
    // is stubbed; what matters here is that signIn() never goes through
    // HttpClient.
    const navSpy = spyOn(service as unknown as { navigateToSignIn(): void }, 'navigateToSignIn');

    service.signIn();

    expect(navSpy).toHaveBeenCalled();
    http.expectNone(() => true);
  });
});
