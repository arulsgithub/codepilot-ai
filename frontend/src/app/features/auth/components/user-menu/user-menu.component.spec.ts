import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { UserMenuComponent } from './user-menu.component';
import { AuthService } from '../../../../core/services/auth.service';
import { CurrentUser } from '../../../../core/models/auth.model';

const USER: CurrentUser = {
  id: 'u-1',
  login: 'octocat',
  name: 'The Octocat',
  avatarUrl: 'https://avatars.githubusercontent.com/u/1',
};

describe('UserMenuComponent', () => {
  let fixture: ComponentFixture<UserMenuComponent>;
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UserMenuComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(UserMenuComponent);
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function signIn(user: CurrentUser = USER): void {
    auth.loadCurrentUser();
    http.expectOne('/api/v1/auth/me').flush(user);
    fixture.detectChanges();
  }

  const q = (selector: string): HTMLElement | null =>
    fixture.debugElement.query(By.css(selector))?.nativeElement ?? null;

  it('renders nothing while loading or signed out', () => {
    fixture.detectChanges();
    expect(q('.user-menu')).toBeNull();

    auth.clearSession();
    fixture.detectChanges();
    expect(q('.user-menu')).toBeNull();
  });

  it('shows the avatar image and login once signed in', () => {
    signIn();

    const avatar = q('.user-avatar') as HTMLImageElement;
    expect(avatar.src).toBe(USER.avatarUrl!);
    expect(avatar.alt).toBe('octocat');
    expect(q('.user-login')!.textContent).toContain('octocat');
  });

  it('falls back to a letter avatar when there is no avatarUrl', () => {
    signIn({ ...USER, avatarUrl: null });

    expect(q('.user-avatar--fallback')!.textContent!.trim()).toBe('T');
    expect(q('img.user-avatar')).toBeNull();
  });

  it('opens a dropdown with Sign out, closed by default', () => {
    signIn();
    expect(q('.user-dropdown')).toBeNull();

    (q('.user-trigger') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(q('.user-dropdown')).not.toBeNull();
    expect(q('.user-dropdown-item')!.textContent).toContain('Sign out');
  });

  it('Sign out calls AuthService.signOut() and closes the dropdown', () => {
    signIn();
    (q('.user-trigger') as HTMLButtonElement).click();
    fixture.detectChanges();
    const signOutSpy = spyOn(auth, 'signOut');

    (q('.user-dropdown-item') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(signOutSpy).toHaveBeenCalled();
    expect(q('.user-dropdown')).toBeNull();
  });

  it('Escape closes the dropdown', () => {
    signIn();
    (q('.user-trigger') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(q('.user-dropdown')).not.toBeNull();

    fixture.componentInstance.onEscape();
    fixture.detectChanges();

    expect(q('.user-dropdown')).toBeNull();
  });

  it('clicking outside closes the dropdown', () => {
    signIn();
    (q('.user-trigger') as HTMLButtonElement).click();
    fixture.detectChanges();

    document.body.click();
    fixture.detectChanges();

    expect(q('.user-dropdown')).toBeNull();
  });
});
