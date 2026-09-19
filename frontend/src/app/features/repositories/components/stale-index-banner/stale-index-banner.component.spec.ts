import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { StaleIndexBannerComponent } from './stale-index-banner.component';
import { RepositoryService } from '../../../../core/services/repository.service';
import { Repository } from '../../../../core/models/repository.model';

const STORAGE_KEY = 'codepilot-selected-repository-id';

function repo(overrides: Partial<Repository> = {}): Repository {
  return {
    id: 'r1',
    name: 'spring-petclinic',
    sourceType: 'GITHUB',
    remoteUrl: 'https://github.com/o/r',
    branch: 'main',
    localPath: 'E:\\ws\\r',
    lastSyncedCommit: 'abc',
    lastSyncedAt: '2026-09-19T10:00:00Z',
    lastIndexedAt: '2026-09-18T10:00:00Z',
    indexStale: true,
    ...overrides,
  };
}

describe('StaleIndexBannerComponent', () => {
  let fixture: ComponentFixture<StaleIndexBannerComponent>;
  let service: RepositoryService;
  let http: HttpTestingController;

  beforeEach(async () => {
    localStorage.removeItem(STORAGE_KEY);
    await TestBed.configureTestingModule({
      imports: [StaleIndexBannerComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(StaleIndexBannerComponent);
    service = TestBed.inject(RepositoryService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.removeItem(STORAGE_KEY);
  });

  function selectRepo(r: Repository): void {
    service.load();
    http.expectOne('/api/v1/repositories').flush([r]);
    service.select(r.id);
    fixture.detectChanges();
  }

  const q = (selector: string): HTMLElement | null =>
    fixture.debugElement.query(By.css(selector))?.nativeElement ?? null;
  const text = (selector: string): string => (q(selector)?.textContent ?? '').replace(/\s+/g, ' ').trim();
  const button = (label: string): HTMLButtonElement | undefined =>
    (fixture.debugElement.queryAll(By.css('button')).map((d) => d.nativeElement) as HTMLButtonElement[]).find(
      (b) => b.textContent!.trim() === label
    );

  it('is hidden when nothing is selected', () => {
    fixture.detectChanges();

    expect(q('.banner')).toBeNull();
  });

  it('is hidden when the index is fresh', () => {
    selectRepo(repo({ indexStale: false }));

    expect(q('.banner')).toBeNull();
  });

  it('warns that answers may not reflect recent changes when the index is stale', () => {
    selectRepo(repo());

    expect(text('.banner')).toContain(
      "This repository's index is out of date. Answers may not reflect recent changes."
    );
    expect(button('Re-index now')).toBeDefined();
  });

  it('says so plainly when the repository has never been indexed', () => {
    selectRepo(repo({ lastIndexedAt: null }));

    expect(text('.banner')).toContain("hasn't been indexed yet");
    expect(text('.banner')).not.toContain('out of date');
  });

  it('can be dismissed', () => {
    selectRepo(repo());

    (q('.banner-dismiss') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(q('.banner')).toBeNull();
  });

  it('comes back when a later sync makes the index stale again (a new staleness episode)', () => {
    selectRepo(repo());
    (q('.banner-dismiss') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(q('.banner')).toBeNull();

    // Sync pulls new commits → different lastSyncedAt → a new episode.
    service.sync('r1');
    http.expectOne('/api/v1/repositories/r1/sync').flush(repo({ lastSyncedAt: '2026-09-19T11:30:00Z' }));
    fixture.detectChanges();

    expect(q('.banner')).not.toBeNull();
  });

  describe('Re-index now', () => {
    beforeEach(() => selectRepo(repo()));

    it('warns it takes several minutes and does not start until confirmed', () => {
      button('Re-index now')!.click();
      fixture.detectChanges();

      expect(text('.banner')).toContain('several minutes');
      http.expectNone('/api/v1/indexing');
    });

    it('cancelling returns to the warning without starting anything', () => {
      button('Re-index now')!.click();
      fixture.detectChanges();
      button('Cancel')!.click();
      fixture.detectChanges();

      expect(text('.banner')).toContain('out of date');
      http.expectNone('/api/v1/indexing');
    });

    it('shows indeterminate progress while running, even though nothing else is blocked', () => {
      button('Re-index now')!.click();
      fixture.detectChanges();
      button('Start re-index')!.click();
      fixture.detectChanges();

      const req = http.expectOne('/api/v1/indexing');
      expect(req.request.body).toEqual({ repositoryId: 'r1' });
      expect(text('.banner')).toContain('Re-indexing spring-petclinic');
      expect(q('.repo-progress')).not.toBeNull();
      expect(button('Re-index now')).toBeUndefined();

      req.flush({ repositoryRoot: 'x', chunksIndexed: 5 });
      http.expectOne('/api/v1/repositories/r1').flush(repo({ indexStale: false, lastIndexedAt: '2026-09-19T12:00:00Z' }));
    });

    it('disappears once the index is fresh again', () => {
      button('Re-index now')!.click();
      fixture.detectChanges();
      button('Start re-index')!.click();
      http.expectOne('/api/v1/indexing').flush({ repositoryRoot: 'x', chunksIndexed: 5 });
      http.expectOne('/api/v1/repositories/r1').flush(repo({ indexStale: false, lastIndexedAt: '2026-09-19T12:00:00Z' }));
      fixture.detectChanges();

      expect(q('.banner')).toBeNull();
    });

    it('keeps showing a failure with the backend message — and offers Try again — instead of vanishing', () => {
      button('Re-index now')!.click();
      fixture.detectChanges();
      button('Start re-index')!.click();
      http
        .expectOne('/api/v1/indexing')
        .flush({ message: 'Embedding model is not reachable' }, { status: 502, statusText: 'Bad Gateway' });
      fixture.detectChanges();

      expect(text('.banner')).toContain('Indexing failed');
      expect(text('.banner')).toContain('Embedding model is not reachable');
      expect(q('.banner')!.classList).toContain('banner--error');

      // "Try again" goes through the same warning, not straight to a 5-minute run.
      button('Try again')!.click();
      fixture.detectChanges();
      expect(text('.banner')).toContain('several minutes');
      http.expectNone('/api/v1/indexing');
    });

    it('disables Re-index now while another repository is being indexed', () => {
      service.load();
      http.expectOne('/api/v1/repositories').flush([repo(), repo({ id: 'r2', name: 'other' })]);
      service.index('r2');
      fixture.detectChanges();

      expect(button('Re-index now')!.disabled).toBeTrue();

      http.expectOne('/api/v1/indexing').flush({ repositoryRoot: 'x', chunksIndexed: 1 });
      http.expectOne('/api/v1/repositories/r2').flush(repo({ id: 'r2', name: 'other' }));
    });
  });
});
