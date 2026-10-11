import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AddRepositoryDialogComponent } from './add-repository-dialog.component';
import { RepositoryService } from '../../../../core/services/repository.service';
import { Repository } from '../../../../core/models/repository.model';

const STORAGE_KEY = 'codepilot-selected-repository-id';

const CREATED: Repository = {
  id: 'r-new',
  name: 'thing',
  sourceType: 'GITHUB',
  remoteUrl: 'https://github.com/o/thing',
  branch: 'main',
  localPath: 'E:\\ws\\thing',
  lastSyncedCommit: 'abc',
  lastSyncedAt: '2026-09-19T10:00:00Z',
  lastIndexedAt: null,
  indexStale: true,
};

describe('AddRepositoryDialogComponent', () => {
  let fixture: ComponentFixture<AddRepositoryDialogComponent>;
  let component: AddRepositoryDialogComponent;
  let service: RepositoryService;
  let http: HttpTestingController;
  let closed: number;

  beforeEach(async () => {
    localStorage.removeItem(STORAGE_KEY);
    await TestBed.configureTestingModule({
      imports: [AddRepositoryDialogComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(AddRepositoryDialogComponent);
    component = fixture.componentInstance;
    service = TestBed.inject(RepositoryService);
    http = TestBed.inject(HttpTestingController);
    closed = 0;
    component.closeRequested.subscribe(() => closed++);
  });

  afterEach(() => {
    http.verify();
    localStorage.removeItem(STORAGE_KEY);
  });

  /** Settle ngModel's async initial write so typed values are not overwritten by it. */
  async function settle(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  async function openDialog(): Promise<void> {
    fixture.componentRef.setInput('open', true);
    await settle();
  }

  const q = <T extends HTMLElement = HTMLElement>(selector: string): T | null =>
    fixture.debugElement.query(By.css(selector))?.nativeElement ?? null;
  const text = (selector: string): string => (q(selector)?.textContent ?? '').replace(/\s+/g, ' ').trim();

  async function type(selector: string, value: string): Promise<void> {
    const input = q<HTMLInputElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await settle();
  }

  const submitButton = () => q<HTMLButtonElement>('button[type="submit"]')!;

  async function switchToLocal(): Promise<void> {
    q<HTMLButtonElement>('#tab-local')!.click();
    await settle();
  }

  it('renders nothing while closed', () => {
    fixture.detectChanges();

    expect(q('.dialog')).toBeNull();
  });

  it('opens on the GitHub tab, listed before Local folder', async () => {
    await openDialog();

    expect(q('.dialog')).not.toBeNull();
    expect(q('#tab-github')!.getAttribute('aria-selected')).toBe('true');
    expect(q('#github-url')).not.toBeNull();
    const tabs = fixture.debugElement.queryAll(By.css('.tab'));
    expect(tabs[0].nativeElement.id).toBe('tab-github');
    expect(tabs[1].nativeElement.id).toBe('tab-local');
  });

  describe('Local folder tab', () => {
    beforeEach(async () => {
      await openDialog();
      await switchToLocal();
    });

    it('has the path input and an optional name', () => {
      expect(q('#local-path')).not.toBeNull();
      expect(q('#local-name')).not.toBeNull();
      expect(text('#panel-local')).toContain('optional');
    });

    it('cannot submit until an absolute path is entered', async () => {
      expect(submitButton().disabled).toBeTrue();

      await type('#local-path', '   ');
      expect(submitButton().disabled).toBeTrue();

      await type('#local-path', 'E:\\code\\thing');
      expect(submitButton().disabled).toBeFalse();
    });

    it('POSTs the path (and name when given), then closes on success', async () => {
      await type('#local-path', 'E:\\code\\thing');
      await type('#local-name', 'My Thing');

      submitButton().click();
      const req = http.expectOne('/api/v1/repositories/local');
      expect(req.request.body).toEqual({ rootPath: 'E:\\code\\thing', name: 'My Thing' });
      req.flush({ ...CREATED, sourceType: 'LOCAL' });

      expect(closed).toBe(1);
      expect(service.selectedRepositoryId()).toBe('r-new');
    });

    it('stays open and shows the backend message inline when the folder is rejected', async () => {
      await type('#local-path', 'E:\\nope');
      submitButton().click();
      http
        .expectOne('/api/v1/repositories/local')
        .flush({ message: 'Not a directory: E:\\nope' }, { status: 404, statusText: 'Not Found' });
      await settle();

      expect(closed).toBe(0);
      expect(q('.dialog')).not.toBeNull();
      expect(text('.repo-alert--error')).toContain('Not a directory: E:\\nope');
      // Form is usable again so the user can correct the path.
      expect(q<HTMLFieldSetElement>('.fieldset')!.disabled).toBeFalse();
      expect(q<HTMLInputElement>('#local-path')!.value).toBe('E:\\nope');
    });
  });

  describe('GitHub tab', () => {
    beforeEach(openDialog);

    it('shows the helper text explaining private repositories work via the user\'s GitHub account', () => {
      expect(q('#tab-github')!.getAttribute('aria-selected')).toBe('true');
      expect(text('#panel-github')).toContain(
        'Cloned using your GitHub account, so your private repositories work too.'
      );
    });

    it('placeholders explain the branch and name defaults', () => {
      expect(q<HTMLInputElement>('#github-branch')!.placeholder).toContain(
        "repository's default branch"
      );
      expect(q<HTMLInputElement>('#github-name')!.placeholder).toContain("repository's name");
    });

    it('has URL, optional branch and optional name fields', () => {
      expect(q('#github-url')).not.toBeNull();
      expect(q('#github-branch')).not.toBeNull();
      expect(q('#github-name')).not.toBeNull();
    });

    it('rejects a non-https URL with an inline error and keeps submit disabled', async () => {
      await type('#github-url', 'http://github.com/o/r');

      expect(text('.field-error')).toContain('must start with https://');
      expect(q('#github-url')!.getAttribute('aria-invalid')).toBe('true');
      expect(submitButton().disabled).toBeTrue();

      await type('#github-url', 'git@github.com:o/r.git');
      expect(submitButton().disabled).toBeTrue();
    });

    it('accepts an https URL', async () => {
      await type('#github-url', 'https://github.com/o/r');

      expect(q('.field-error')).toBeNull();
      expect(submitButton().disabled).toBeFalse();
    });

    it('says it is cloning and may take a minute, locks the form and cannot be dismissed mid-clone', async () => {
      await type('#github-url', 'https://github.com/o/r');
      await type('#github-branch', 'develop');

      submitButton().click();
      await settle();

      const req = http.expectOne('/api/v1/repositories/github');
      expect(req.request.body).toEqual({ remoteUrl: 'https://github.com/o/r', branch: 'develop' });

      expect(text('.progress')).toContain('Cloning repository');
      expect(text('.progress')).toContain('can take a minute');
      expect(q('.repo-progress')).not.toBeNull();
      expect(q<HTMLFieldSetElement>('.fieldset')!.disabled).toBeTrue();
      expect(submitButton().disabled).toBeTrue();
      expect(submitButton().textContent).toContain('Cloning…');
      expect(q<HTMLButtonElement>('.dialog-close')!.disabled).toBeTrue();
      expect(q<HTMLButtonElement>('.actions .repo-btn--secondary')!.disabled).toBeTrue();

      // Backdrop click / Escape must not abandon a running clone.
      q('.dialog-scrim')!.click();
      component.onEscape();
      expect(closed).toBe(0);

      req.flush(CREATED);
    });

    it('closes and selects the new repository once the clone succeeds', async () => {
      await type('#github-url', 'https://github.com/o/r');
      submitButton().click();
      http.expectOne('/api/v1/repositories/github').flush(CREATED);

      expect(closed).toBe(1);
      expect(service.selectedRepository()).toEqual(CREATED);
    });

    it("shows the backend's authentication message verbatim, keeps the dialog open and re-enables the form", async () => {
      const message =
        'Git clone failed: repository requires authentication. Set the GITHUB_TOKEN environment variable';
      await type('#github-url', 'https://github.com/o/private');

      submitButton().click();
      http
        .expectOne('/api/v1/repositories/github')
        .flush({ message }, { status: 502, statusText: 'Bad Gateway' });
      await settle();

      expect(closed).toBe(0);
      expect(q('.dialog')).not.toBeNull();
      expect(text('.repo-alert--error')).toContain(message);
      expect(text('.repo-alert--error')).not.toContain('something went wrong');
      expect(q<HTMLFieldSetElement>('.fieldset')!.disabled).toBeFalse();
      expect(submitButton().disabled).toBeFalse();
      expect(q<HTMLInputElement>('#github-url')!.value).toBe('https://github.com/o/private');
    });
  });

  it('a failure on one tab does not leak onto the other', async () => {
    await openDialog();
    await switchToLocal();
    await type('#local-path', 'E:\\nope');
    submitButton().click();
    http
      .expectOne('/api/v1/repositories/local')
      .flush({ message: 'Not a directory: E:\\nope' }, { status: 404, statusText: 'Not Found' });
    await settle();
    expect(q('.repo-alert--error')).not.toBeNull();

    q<HTMLButtonElement>('#tab-github')!.click();
    await settle();

    expect(q('.repo-alert--error')).toBeNull();
  });

  it('Cancel closes an idle dialog, and reopening starts with a clean form, no old error, and back on GitHub', async () => {
    await openDialog();
    await switchToLocal();
    await type('#local-path', 'E:\\draft');

    q<HTMLButtonElement>('.actions .repo-btn--secondary')!.click();
    expect(closed).toBe(1);

    fixture.componentRef.setInput('open', false);
    await settle();
    fixture.componentRef.setInput('open', true);
    await settle();

    expect(q('#tab-github')!.getAttribute('aria-selected')).toBe('true');
    await switchToLocal();
    expect(q<HTMLInputElement>('#local-path')!.value).toBe('');
  });

  describe('local registration disabled on this deployment', () => {
    it('hides the Local folder tab for the rest of the session once the backend says so', async () => {
      await openDialog();
      await switchToLocal();
      await type('#local-path', 'E:\\code\\thing');
      submitButton().click();
      http
        .expectOne('/api/v1/repositories/local')
        .flush(
          { message: 'Registering local filesystem paths is disabled on this deployment.' },
          { status: 400, statusText: 'Bad Request' }
        );
      await settle();

      // The error is still shown as-is on the tab the user was on...
      expect(text('.repo-alert--error')).toContain(
        'Registering local filesystem paths is disabled on this deployment.'
      );
      // ...but the tab itself is gone from the tab bar from now on.
      expect(q('#tab-local')).toBeNull();

      q<HTMLButtonElement>('.dialog-close')!.click();
      fixture.componentRef.setInput('open', false);
      await settle();
      fixture.componentRef.setInput('open', true);
      await settle();

      expect(q('#tab-local')).toBeNull();
      expect(q('#tab-github')!.getAttribute('aria-selected')).toBe('true');
    });

    it('does not hide the tab for an unrelated 400', async () => {
      await openDialog();
      await switchToLocal();
      await type('#local-path', 'E:\\nope');
      submitButton().click();
      http
        .expectOne('/api/v1/repositories/local')
        .flush({ message: 'Not a directory: E:\\nope' }, { status: 400, statusText: 'Bad Request' });
      await settle();

      expect(q('#tab-local')).not.toBeNull();
    });
  });

  // Session expiry (401) is handled above this component: api.interceptor.ts
  // clears the session for every request before RepositoryService's own
  // error callback runs, and AppComponent reacts to that by swapping the
  // whole app away from this dialog to the sign-in screen. See
  // api.interceptor.spec.ts and app.component.spec.ts.
});
