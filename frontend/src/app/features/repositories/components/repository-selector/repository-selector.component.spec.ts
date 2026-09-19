import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { RepositorySelectorComponent } from './repository-selector.component';
import { RepositoryService } from '../../../../core/services/repository.service';
import { Repository } from '../../../../core/models/repository.model';

const STORAGE_KEY = 'codepilot-selected-repository-id';

const GITHUB_STALE: Repository = {
  id: 'r-gh',
  name: 'spring-petclinic',
  sourceType: 'GITHUB',
  remoteUrl: 'https://github.com/spring-projects/spring-petclinic',
  branch: 'main',
  localPath: 'E:\\ws\\spring-petclinic',
  lastSyncedCommit: 'abcdef1234567890',
  lastSyncedAt: '2026-09-19T10:00:00Z',
  lastIndexedAt: '2026-09-18T10:00:00Z',
  indexStale: true,
};

const LOCAL_FRESH: Repository = {
  id: 'r-local',
  name: 'codepilot-ai',
  sourceType: 'LOCAL',
  remoteUrl: null,
  branch: null,
  localPath: 'E:\\code\\codepilot-ai',
  lastSyncedCommit: null,
  lastSyncedAt: null,
  lastIndexedAt: '2026-09-19T09:00:00Z',
  indexStale: false,
};

const LOCAL_NEVER_INDEXED: Repository = {
  ...LOCAL_FRESH,
  id: 'r-new',
  name: 'brand-new',
  lastIndexedAt: null,
  indexStale: true,
};

describe('RepositorySelectorComponent', () => {
  let fixture: ComponentFixture<RepositorySelectorComponent>;
  let component: RepositorySelectorComponent;
  let service: RepositoryService;
  let http: HttpTestingController;

  beforeEach(async () => {
    localStorage.removeItem(STORAGE_KEY);
    await TestBed.configureTestingModule({
      imports: [RepositorySelectorComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(RepositorySelectorComponent);
    component = fixture.componentInstance;
    service = TestBed.inject(RepositoryService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.removeItem(STORAGE_KEY);
  });

  function loadRepos(repos: Repository[]): void {
    service.load();
    http.expectOne('/api/v1/repositories').flush(repos);
    fixture.detectChanges();
  }

  const el = (selector: string): HTMLElement | null =>
    fixture.debugElement.query(By.css(selector))?.nativeElement ?? null;

  const text = (selector: string): string => (el(selector)?.textContent ?? '').replace(/\s+/g, ' ').trim();

  function openMenu(): void {
    (el('.selector-trigger') as HTMLButtonElement).click();
    fixture.detectChanges();
  }

  function options(): HTMLElement[] {
    return fixture.debugElement.queryAll(By.css('.selector-option')).map((d) => d.nativeElement);
  }

  it('shows a loading state while the first fetch is in flight', () => {
    service.load();
    fixture.detectChanges();

    expect(text('.selector-loading')).toContain('Loading repositories');
    expect(el('.selector-trigger')).toBeNull();

    http.expectOne('/api/v1/repositories').flush([]);
  });

  it('shows the backend error with a Retry that reloads', () => {
    service.load();
    http
      .expectOne('/api/v1/repositories')
      .flush({ message: 'Database is unavailable' }, { status: 500, statusText: 'Error' });
    fixture.detectChanges();

    expect(text('.repo-alert--error')).toContain('Database is unavailable');

    (el('.selector-retry') as HTMLButtonElement).click();
    expect(service.listStatus()).toBe('in-progress');
    http.expectOne('/api/v1/repositories').flush([GITHUB_STALE]);
    fixture.detectChanges();

    expect(el('.repo-alert--error')).toBeNull();
    expect(el('.selector-trigger')).not.toBeNull();
  });

  it('makes the no-repository state explicit rather than showing an empty box', () => {
    loadRepos([GITHUB_STALE]);

    const trigger = el('.selector-trigger')!;
    expect(trigger.classList).toContain('selector-trigger--none');
    expect(text('.selector-trigger')).toContain('No repository');
    expect(text('.selector-trigger')).toContain('General chat — no code context');
  });

  it('lists every repository with its source badge, plus the explicit "No repository" choice', () => {
    loadRepos([GITHUB_STALE, LOCAL_FRESH]);
    openMenu();

    const rows = options();
    expect(rows.length).toBe(3);
    expect(rows[0].textContent).toContain('No repository');
    expect(rows[1].textContent).toContain('spring-petclinic');
    expect(rows[1].textContent).toContain('github');
    expect(rows[2].textContent).toContain('codepilot-ai');
    expect(rows[2].textContent).toContain('local');
  });

  it('flags a stale index only on the repositories that have one', () => {
    loadRepos([GITHUB_STALE, LOCAL_FRESH]);
    openMenu();

    const rows = options();
    expect(rows[1].querySelector('.repo-badge--stale')?.textContent).toContain('stale');
    expect(rows[2].querySelector('.repo-badge--stale')).toBeNull();
  });

  it('distinguishes "not indexed" from "stale"', () => {
    loadRepos([LOCAL_NEVER_INDEXED]);
    openMenu();

    expect(options()[1].querySelector('.repo-badge--stale')?.textContent).toContain('not indexed');
  });

  it('selecting a repository sets the app-wide selection, persists it and emits the id', () => {
    loadRepos([GITHUB_STALE, LOCAL_FRESH]);
    const emitted: (string | null)[] = [];
    component.repositorySelected.subscribe((id) => emitted.push(id));
    openMenu();

    options()[2].click();
    fixture.detectChanges();

    expect(service.selectedRepository()).toEqual(LOCAL_FRESH);
    expect(localStorage.getItem(STORAGE_KEY)).toBe('r-local');
    expect(emitted).toEqual(['r-local']);
    // The menu closes and the trigger now names the repo.
    expect(el('.selector-menu')).toBeNull();
    expect(text('.selector-trigger')).toContain('codepilot-ai');
  });

  it('choosing "No repository" clears the selection and emits null', () => {
    loadRepos([GITHUB_STALE]);
    service.select('r-gh');
    fixture.detectChanges();
    const emitted: (string | null)[] = [];
    component.repositorySelected.subscribe((id) => emitted.push(id));

    openMenu();
    options()[0].click();
    fixture.detectChanges();

    expect(service.selectedRepository()).toBeNull();
    expect(emitted).toEqual([null]);
    expect(el('.selector-trigger')!.classList).toContain('selector-trigger--none');
  });

  it('marks the current repository as selected in the menu', () => {
    loadRepos([GITHUB_STALE, LOCAL_FRESH]);
    service.select('r-gh');
    fixture.detectChanges();
    openMenu();

    const rows = options();
    expect(rows[1].getAttribute('aria-selected')).toBe('true');
    expect(rows[2].getAttribute('aria-selected')).toBe('false');
    expect(rows[0].getAttribute('aria-selected')).toBe('false');
  });

  it('shows an add hint when the registry is empty and emits addRequested from + Add', () => {
    loadRepos([]);
    let added = 0;
    component.addRequested.subscribe(() => added++);
    openMenu();

    expect(text('.selector-empty')).toContain('No repositories yet');

    (el('.selector-add') as HTMLButtonElement).click();
    expect(added).toBe(1);
  });

  it('shows activity on a row while that repository is being synced', () => {
    loadRepos([GITHUB_STALE]);
    service.sync('r-gh');
    fixture.detectChanges();
    openMenu();

    expect(options()[1].querySelector('.repo-spinner')).not.toBeNull();

    http.expectOne('/api/v1/repositories/r-gh/sync').flush(GITHUB_STALE);
  });

  it('closes the menu on Escape and on an outside click', () => {
    loadRepos([GITHUB_STALE]);
    openMenu();
    expect(el('.selector-menu')).not.toBeNull();

    fixture.debugElement.triggerEventHandler('keydown.escape', {});
    component.onEscape();
    fixture.detectChanges();
    expect(el('.selector-menu')).toBeNull();

    openMenu();
    document.body.click();
    fixture.detectChanges();
    expect(el('.selector-menu')).toBeNull();
  });
});
