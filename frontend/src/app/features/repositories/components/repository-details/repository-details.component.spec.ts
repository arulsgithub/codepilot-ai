import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { RepositoryDetailsComponent } from './repository-details.component';
import { RepositoryService } from '../../../../core/services/repository.service';
import { Repository } from '../../../../core/models/repository.model';

const STORAGE_KEY = 'codepilot-selected-repository-id';
const HOUR = 3_600_000;

function hoursAgo(hours: number): string {
  // A 30s margin keeps "N hours ago" stable: the component snapshots `now` when it is
  // constructed, a few ms before this timestamp is created, so an exact offset can
  // round down to "59 minutes ago".
  return new Date(Date.now() - hours * HOUR - 30_000).toISOString();
}

function githubRepo(overrides: Partial<Repository> = {}): Repository {
  return {
    id: 'r-gh',
    name: 'spring-petclinic',
    sourceType: 'GITHUB',
    remoteUrl: 'https://github.com/spring-projects/spring-petclinic',
    branch: 'main',
    localPath: 'E:\\workspace\\very\\long\\path\\to\\spring-petclinic',
    lastSyncedCommit: 'abcdef1234567890abcdef',
    lastSyncedAt: hoursAgo(2),
    lastIndexedAt: hoursAgo(30),
    indexStale: true,
    ...overrides,
  };
}

function localRepo(overrides: Partial<Repository> = {}): Repository {
  return {
    id: 'r-local',
    name: 'codepilot-ai',
    sourceType: 'LOCAL',
    remoteUrl: null,
    branch: null,
    localPath: 'E:\\code\\codepilot-ai',
    lastSyncedCommit: null,
    lastSyncedAt: null,
    lastIndexedAt: hoursAgo(1),
    indexStale: false,
    ...overrides,
  };
}

describe('RepositoryDetailsComponent', () => {
  let fixture: ComponentFixture<RepositoryDetailsComponent>;
  let service: RepositoryService;
  let http: HttpTestingController;

  beforeEach(async () => {
    localStorage.removeItem(STORAGE_KEY);
    await TestBed.configureTestingModule({
      imports: [RepositoryDetailsComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(RepositoryDetailsComponent);
    service = TestBed.inject(RepositoryService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.removeItem(STORAGE_KEY);
  });

  function selectRepo(repo: Repository): void {
    service.load();
    http.expectOne('/api/v1/repositories').flush([repo]);
    service.select(repo.id);
    fixture.detectChanges();
  }

  const q = (selector: string): HTMLElement | null =>
    fixture.debugElement.query(By.css(selector))?.nativeElement ?? null;
  const qa = (selector: string): HTMLElement[] =>
    fixture.debugElement.queryAll(By.css(selector)).map((d) => d.nativeElement);
  const text = (selector: string): string => (q(selector)?.textContent ?? '').replace(/\s+/g, ' ').trim();
  const button = (label: string): HTMLButtonElement | undefined =>
    (qa('button') as HTMLButtonElement[]).find((b) => b.textContent!.trim() === label);
  const rowValue = (label: string): HTMLElement | undefined =>
    qa('.details-row')
      .find((row) => row.querySelector('dt')!.textContent!.trim() === label)
      ?.querySelector('dd') as HTMLElement | undefined;

  it('renders nothing when no repository is selected', () => {
    fixture.detectChanges();

    expect(q('.details')).toBeNull();
  });

  describe('a GitHub repository', () => {
    beforeEach(() => selectRepo(githubRepo()));

    it('shows name, source type and branch', () => {
      expect(text('.details-title')).toBe('spring-petclinic');
      expect(text('.repo-badge')).toBe('github');
      expect(text('.details-branch')).toContain('main');
    });

    it('shows the local path in monospace, with the full value in a tooltip', () => {
      const path = rowValue('Path')!.querySelector('code')!;

      expect(path.classList).toContain('mono');
      expect(path.classList).toContain('truncate');
      expect(path.getAttribute('title')).toBe('E:\\workspace\\very\\long\\path\\to\\spring-petclinic');
    });

    it('shows the remote as a safe external link', () => {
      const link = rowValue('Remote')!.querySelector('a') as HTMLAnchorElement;

      expect(link.href).toBe('https://github.com/spring-projects/spring-petclinic');
      expect(link.target).toBe('_blank');
      expect(link.rel).toContain('noopener');
    });

    it('shows "Last synced" and "Last indexed" as relative times with the absolute time on hover', () => {
      const synced = rowValue('Last synced')!.querySelector('span')!;
      const indexed = rowValue('Last indexed')!.querySelector('span')!;

      expect(synced.textContent).toContain('2 hours ago');
      expect(synced.getAttribute('title')).toBeTruthy();
      expect(indexed.textContent).toContain('1 day ago');
      expect(indexed.getAttribute('title')).toBeTruthy();
    });

    it('shows the last synced commit as its first 8 characters, monospace', () => {
      const commit = rowValue('Commit')!.querySelector('code')!;

      expect(commit.textContent!.trim()).toBe('abcdef12');
      expect(commit.classList).toContain('mono');
    });

    it('offers Sync', () => {
      expect(button('Sync')).toBeDefined();
    });

    it('marks a stale index next to "Last indexed"', () => {
      expect(rowValue('Last indexed')!.textContent).toContain('stale');
    });
  });

  describe('a local repository', () => {
    beforeEach(() => selectRepo(localRepo()));

    it('hides Sync entirely — there is no remote to sync from', () => {
      expect(button('Sync')).toBeUndefined();
      expect(button('Syncing…')).toBeUndefined();
    });

    it('has no remote or "Last synced" rows, but still shows Last indexed and Re-index', () => {
      expect(rowValue('Remote')).toBeUndefined();
      expect(rowValue('Last synced')).toBeUndefined();
      expect(rowValue('Commit')).toBeUndefined();
      expect(rowValue('Last indexed')!.textContent).toContain('1 hour ago');
      expect(button('Re-index')).toBeDefined();
    });
  });

  it('says "never" (not a blank or NaN) when a repository has not been indexed', () => {
    selectRepo(localRepo({ lastIndexedAt: null, indexStale: true }));

    expect(rowValue('Last indexed')!.textContent).toContain('never');
    expect(rowValue('Last indexed')!.textContent).toContain('not indexed');
  });

  describe('Sync', () => {
    beforeEach(() => selectRepo(githubRepo()));

    it('shows a spinner and disables the button while running, then updates the row', () => {
      button('Sync')!.click();
      fixture.detectChanges();

      expect(button('Syncing…')!.disabled).toBeTrue();
      expect(q('.repo-spinner')).not.toBeNull();

      http
        .expectOne('/api/v1/repositories/r-gh/sync')
        .flush(githubRepo({ lastSyncedCommit: '1234567890abcdef' }));
      fixture.detectChanges();

      expect(button('Sync')!.disabled).toBeFalse();
      expect(rowValue('Commit')!.textContent!.trim()).toBe('12345678');
    });

    it('shows the backend message inline when sync fails', () => {
      button('Sync')!.click();
      http
        .expectOne('/api/v1/repositories/r-gh/sync')
        .flush({ message: 'Git fetch failed: could not resolve host' }, { status: 502, statusText: 'Bad Gateway' });
      fixture.detectChanges();

      expect(text('.repo-alert--error')).toContain('Git fetch failed: could not resolve host');
      expect(button('Sync')!.disabled).toBeFalse();
    });
  });

  describe('Re-index', () => {
    beforeEach(() => selectRepo(githubRepo()));

    it('warns that it takes several minutes and does NOT start until confirmed', () => {
      button('Re-index')!.click();
      fixture.detectChanges();

      expect(text('.details-confirm')).toContain('several minutes');
      http.expectNone('/api/v1/indexing');
      expect(service.isIndexing()).toBeFalse();
    });

    it('cancelling the warning starts nothing', () => {
      button('Re-index')!.click();
      fixture.detectChanges();

      button('Cancel')!.click();
      fixture.detectChanges();

      expect(q('.details-confirm')).toBeNull();
      http.expectNone('/api/v1/indexing');
    });

    it('confirming starts indexing, shows an indeterminate progress bar and locks the button', () => {
      button('Re-index')!.click();
      fixture.detectChanges();
      button('Start re-index')!.click();
      fixture.detectChanges();

      const req = http.expectOne('/api/v1/indexing');
      expect(req.request.body).toEqual({ repositoryId: 'r-gh' });
      expect(q('.repo-progress')).not.toBeNull();
      expect(text('.details-progress')).toContain('several minutes');
      expect(button('Indexing…')!.disabled).toBeTrue();
      // The rest of the app is not blocked: no modal/overlay is involved.
      expect(q('.details-confirm')).toBeNull();

      req.flush({ repositoryRoot: 'x', chunksIndexed: 1 });
      http.expectOne('/api/v1/repositories/r-gh').flush(githubRepo({ indexStale: false }));
    });

    it('reports the chunk count on success and clears the stale marker', () => {
      button('Re-index')!.click();
      fixture.detectChanges();
      button('Start re-index')!.click();
      http.expectOne('/api/v1/indexing').flush({ repositoryRoot: 'x', chunksIndexed: 412 });
      http
        .expectOne('/api/v1/repositories/r-gh')
        .flush(githubRepo({ lastIndexedAt: hoursAgo(0), indexStale: false }));
      fixture.detectChanges();

      expect(text('.repo-alert--ok')).toContain('Indexed 412 chunks');
      expect(rowValue('Last indexed')!.textContent).toContain('just now');
      expect(rowValue('Last indexed')!.textContent).not.toContain('stale');
      expect(button('Re-index')!.disabled).toBeFalse();
    });

    it('shows the backend message when indexing fails, and lets the user try again', () => {
      button('Re-index')!.click();
      fixture.detectChanges();
      button('Start re-index')!.click();
      http
        .expectOne('/api/v1/indexing')
        .flush({ message: 'Embedding model is not reachable' }, { status: 502, statusText: 'Bad Gateway' });
      fixture.detectChanges();

      expect(text('.repo-alert--error')).toContain('Embedding model is not reachable');
      expect(button('Re-index')!.disabled).toBeFalse();
    });

    it('disables Re-index (with an explanation) while a different repository is being indexed', () => {
      service.load();
      http.expectOne('/api/v1/repositories').flush([githubRepo(), localRepo()]);
      service.index('r-local');
      fixture.detectChanges();

      expect(button('Re-index')!.disabled).toBeTrue();
      expect(text('.details-note')).toContain('Another repository is being indexed');

      http.expectOne('/api/v1/indexing').flush({ repositoryRoot: 'x', chunksIndexed: 1 });
      http.expectOne('/api/v1/repositories/r-local').flush(localRepo());
    });
  });

  it('collapses and expands the details body', () => {
    selectRepo(githubRepo());
    expect(q('.details-body')).not.toBeNull();

    (q('.details-toggle') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(q('.details-body')).toBeNull();

    (q('.details-toggle') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(q('.details-body')).not.toBeNull();
  });
});
