import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { SignInScreenComponent } from './sign-in-screen.component';
import { AuthService } from '../../../../core/services/auth.service';

describe('SignInScreenComponent', () => {
  let fixture: ComponentFixture<SignInScreenComponent>;
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SignInScreenComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(SignInScreenComponent);
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  const q = (selector: string): HTMLElement | null =>
    fixture.debugElement.query(By.css(selector))?.nativeElement ?? null;
  const text = (selector: string): string => (q(selector)?.textContent ?? '').replace(/\s+/g, ' ').trim();

  it('shows the CodePilot name and what the app does', () => {
    fixture.detectChanges();

    expect(text('h1')).toBe('CodePilot AI');
    expect(q('.sign-in-subtitle')).not.toBeNull();
  });

  it('shows a primary "Sign in with GitHub" button with an inline GitHub mark', () => {
    fixture.detectChanges();

    const button = q('.sign-in-button') as HTMLButtonElement;
    expect(button.textContent).toContain('Sign in with GitHub');
    expect(button.querySelector('svg')).not.toBeNull();
  });

  it('shows the repo-scope / encrypted-token fine print', () => {
    fixture.detectChanges();

    expect(text('.sign-in-fine-print')).toContain('repo');
    expect(text('.sign-in-fine-print')).toContain('encrypted');
  });

  it('calls AuthService.signIn() when clicked', () => {
    fixture.detectChanges();
    const signInSpy = spyOn(auth, 'signIn');

    (q('.sign-in-button') as HTMLButtonElement).click();

    expect(signInSpy).toHaveBeenCalled();
  });

  it('shows no sidebar, composer or suggestion chips', () => {
    fixture.detectChanges();

    expect(q('app-conversation-sidebar')).toBeNull();
    expect(q('app-message-composer')).toBeNull();
    expect(q('.prompt-chip')).toBeNull();
  });

  it('shows the backend connection error when the backend could not be reached', () => {
    auth.loadCurrentUser();
    http.expectOne('/api/v1/auth/me').error(new ProgressEvent('error'), { status: 0 });
    fixture.detectChanges();

    expect(text('.sign-in-connection-error')).toContain('Unable to reach the CodePilot backend');
  });

  it('shows no connection-error banner when there is none', () => {
    fixture.detectChanges();

    expect(q('.sign-in-connection-error')).toBeNull();
  });
});
