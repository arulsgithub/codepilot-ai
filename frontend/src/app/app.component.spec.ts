import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AppComponent } from './app.component';
import { AuthService } from './core/services/auth.service';
import { CurrentUser } from './core/models/auth.model';

const USER: CurrentUser = {
  id: 'u-1',
  login: 'octocat',
  name: 'The Octocat',
  avatarUrl: 'https://avatars.githubusercontent.com/u/1',
};

describe('AppComponent', () => {
  let fixture: ComponentFixture<AppComponent>;
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(AppComponent);
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  const q = (selector: string): HTMLElement | null =>
    fixture.debugElement.query(By.css(selector))?.nativeElement ?? null;

  it('calls loadCurrentUser on startup and shows a splash — not the app, not an error — while it resolves', () => {
    const loadSpy = spyOn(auth, 'loadCurrentUser').and.callThrough();
    fixture.detectChanges();

    expect(loadSpy).toHaveBeenCalled();
    expect(q('.splash')).not.toBeNull();
    expect(q('app-sign-in-screen')).toBeNull();
    expect(q('router-outlet')).toBeNull();

    http.expectOne('/api/v1/auth/me').flush(USER);
  });

  it('shows the sign-in screen once confirmed signed out', () => {
    fixture.detectChanges();
    http.expectOne('/api/v1/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    fixture.detectChanges();

    expect(q('.splash')).toBeNull();
    expect(q('app-sign-in-screen')).not.toBeNull();
    expect(q('router-outlet')).toBeNull();
  });

  it('renders the router outlet once signed in', () => {
    fixture.detectChanges();
    http.expectOne('/api/v1/auth/me').flush(USER);
    fixture.detectChanges();

    expect(q('.splash')).toBeNull();
    expect(q('app-sign-in-screen')).toBeNull();
    expect(q('router-outlet')).not.toBeNull();
  });

  it('swaps from the app back to the sign-in screen when the session is cleared later (a 401 elsewhere)', () => {
    fixture.detectChanges();
    http.expectOne('/api/v1/auth/me').flush(USER);
    fixture.detectChanges();
    expect(q('router-outlet')).not.toBeNull();

    auth.clearSession();
    fixture.detectChanges();

    expect(q('router-outlet')).toBeNull();
    expect(q('app-sign-in-screen')).not.toBeNull();
  });
});
