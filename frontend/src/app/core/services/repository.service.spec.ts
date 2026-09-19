import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { RepositoryService } from './repository.service';
import { Repository } from '../models/repository.model';

const STORAGE_KEY = 'codepilot-selected-repository-id';

const GITHUB_REPO: Repository = {
  id: 'r-github',
  name: 'spring-petclinic',
  sourceType: 'GITHUB',
  remoteUrl: 'https://github.com/spring-projects/spring-petclinic',
  branch: 'main',
  localPath: 'E:\\workspace\\spring-petclinic',
  lastSyncedCommit: 'abcdef1234567890',
  lastSyncedAt: '2026-09-19T10:00:00Z',
  lastIndexedAt: '2026-09-18T10:00:00Z',
  indexStale: true,
};

const LOCAL_REPO: Repository = {
  id: 'r-local',
  name: 'codepilot-ai',
  sourceType: 'LOCAL',
  remoteUrl: null,
  branch: null,
  localPath: 'E:\\AI_Projects_OPCode\\codepilot-ai',
  lastSyncedCommit: null,
  lastSyncedAt: null,
  lastIndexedAt: '2026-09-19T09:00:00Z',
  indexStale: false,
};

/** The backend's error envelope — its `message` is written for humans on purpose. */
function apiError(message: string, status: number) {
  return {
    body: { timestamp: '2026-09-19T00:00:00Z', status, code: 'X', message, path: '/api/v1/x' },
    init: { status, statusText: 'Error' },
  };
}

describe('RepositoryService', () => {
  let service: RepositoryService;
  let http: HttpTestingController;

  function create(): void {
    service = TestBed.inject(RepositoryService);
    http = TestBed.inject(HttpTestingController);
  }

  function loadList(repos: Repository[]): void {
    service.load();
    http.expectOne('/api/v1/repositories').flush(repos);
  }

  beforeEach(() => {
    localStorage.removeItem(STORAGE_KEY);
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
  });

  afterEach(() => {
    http?.verify();
    localStorage.removeItem(STORAGE_KEY);
  });

  describe('loading the list', () => {
    it('GETs /api/v1/repositories into the shared signal, moving idle → in-progress → idle', () => {
      create();
      expect(service.listStatus()).toBe('idle');

      service.load();
      expect(service.listStatus()).toBe('in-progress');
      http.expectOne('/api/v1/repositories').flush([GITHUB_REPO, LOCAL_REPO]);

      expect(service.listStatus()).toBe('idle');
      expect(service.repositories()).toEqual([GITHUB_REPO, LOCAL_REPO]);
    });

    it('surfaces the backend message when loading fails', () => {
      create();
      service.load();
      const { body, init } = apiError('Database is unavailable', 500);
      http.expectOne('/api/v1/repositories').flush(body, init);

      expect(service.listStatus()).toBe('error');
      expect(service.listError()).toBe('Database is unavailable');
    });

    it('reports an unreachable backend distinctly from an API error', () => {
      create();
      service.load();
      http.expectOne('/api/v1/repositories').error(new ProgressEvent('error'), { status: 0 });

      expect(service.listStatus()).toBe('error');
      expect(service.listError()).toContain('Unable to reach the CodePilot backend');
    });
  });

  describe('selection', () => {
    it('starts with no selection', () => {
      create();
      expect(service.selectedRepository()).toBeNull();
      expect(service.selectedRepositoryId()).toBeNull();
    });

    it('selects a known repository and persists the id', () => {
      create();
      loadList([GITHUB_REPO, LOCAL_REPO]);

      service.select('r-local');

      expect(service.selectedRepository()).toEqual(LOCAL_REPO);
      expect(localStorage.getItem(STORAGE_KEY)).toBe('r-local');
    });

    it('ignores an id that is not in the list', () => {
      create();
      loadList([GITHUB_REPO]);

      service.select('does-not-exist');

      expect(service.selectedRepositoryId()).toBeNull();
      expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
    });

    it('clears the selection and the stored id when set to null', () => {
      create();
      loadList([GITHUB_REPO]);
      service.select('r-github');

      service.select(null);

      expect(service.selectedRepository()).toBeNull();
      expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
    });

    it('restores a persisted selection once the list loads', () => {
      localStorage.setItem(STORAGE_KEY, 'r-github');
      create();
      // Before the list arrives the id is remembered but nothing resolves yet.
      expect(service.selectedRepository()).toBeNull();

      loadList([GITHUB_REPO, LOCAL_REPO]);

      expect(service.selectedRepository()).toEqual(GITHUB_REPO);
    });

    it('falls back to none — and forgets the id — when the stored repository no longer exists', () => {
      localStorage.setItem(STORAGE_KEY, 'deleted-on-server');
      create();

      loadList([GITHUB_REPO, LOCAL_REPO]);

      expect(service.selectedRepositoryId()).toBeNull();
      expect(service.selectedRepository()).toBeNull();
      expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
    });
  });

  describe('registering repositories', () => {
    it('POSTs a local folder, dropping the blank optional name', () => {
      create();
      service.registerLocal({ rootPath: '  E:\\code\\thing  ', name: '   ' });

      const req = http.expectOne('/api/v1/repositories/local');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ rootPath: 'E:\\code\\thing' });
      req.flush(LOCAL_REPO);
    });

    it('POSTs a GitHub URL with branch and name when given, omitting blanks', () => {
      create();
      service.registerGithub({
        remoteUrl: 'https://github.com/o/r',
        branch: 'develop',
        name: '',
      });

      const req = http.expectOne('/api/v1/repositories/github');
      expect(req.request.body).toEqual({ remoteUrl: 'https://github.com/o/r', branch: 'develop' });
      req.flush(GITHUB_REPO);
    });

    it('is in-progress for the whole (slow) clone, then adds and selects the new repository', () => {
      create();
      const onRegistered = jasmine.createSpy('onRegistered');

      service.registerGithub({ remoteUrl: 'https://github.com/o/r' }, onRegistered);
      expect(service.addStatus()).toBe('in-progress');
      expect(onRegistered).not.toHaveBeenCalled();

      http.expectOne('/api/v1/repositories/github').flush(GITHUB_REPO);

      expect(service.addStatus()).toBe('idle');
      expect(service.repositories()).toEqual([GITHUB_REPO]);
      expect(service.selectedRepository()).toEqual(GITHUB_REPO);
      expect(onRegistered).toHaveBeenCalledWith(GITHUB_REPO);
    });

    it('shows the backend message VERBATIM on failure and does not fire the success callback', () => {
      create();
      const onRegistered = jasmine.createSpy('onRegistered');
      const message =
        'Git clone failed: repository requires authentication. Set the GITHUB_TOKEN environment variable';

      service.registerGithub({ remoteUrl: 'https://github.com/o/private' }, onRegistered);
      const { body, init } = apiError(message, 502);
      http.expectOne('/api/v1/repositories/github').flush(body, init);

      expect(service.addStatus()).toBe('error');
      expect(service.addError()).toBe(message);
      expect(service.repositories()).toEqual([]);
      expect(onRegistered).not.toHaveBeenCalled();
    });

    it('does not start a second registration while one is running', () => {
      create();
      service.registerLocal({ rootPath: 'E:\\a' });
      service.registerLocal({ rootPath: 'E:\\b' });

      const requests = http.match('/api/v1/repositories/local');
      expect(requests.length).toBe(1);
      requests[0].flush(LOCAL_REPO);
    });

    it('re-registering an existing repository replaces its row instead of duplicating it', () => {
      create();
      loadList([GITHUB_REPO]);

      service.registerGithub({ remoteUrl: GITHUB_REPO.remoteUrl! });
      http.expectOne('/api/v1/repositories/github').flush({ ...GITHUB_REPO, name: 'renamed' });

      expect(service.repositories().length).toBe(1);
      expect(service.repositories()[0].name).toBe('renamed');
    });

    it('clearAddError resets a failed add back to idle', () => {
      create();
      service.registerLocal({ rootPath: 'E:\\nope' });
      const { body, init } = apiError('Not a directory: E:\\nope', 404);
      http.expectOne('/api/v1/repositories/local').flush(body, init);
      expect(service.addStatus()).toBe('error');

      service.clearAddError();

      expect(service.addStatus()).toBe('idle');
      expect(service.addError()).toBeNull();
    });
  });

  describe('sync', () => {
    it('POSTs /sync, shows in-progress, then replaces the row with the response', () => {
      create();
      loadList([GITHUB_REPO]);

      service.sync('r-github');
      expect(service.syncingId()).toBe('r-github');

      const req = http.expectOne('/api/v1/repositories/r-github/sync');
      expect(req.request.method).toBe('POST');
      req.flush({ ...GITHUB_REPO, lastSyncedCommit: '9999999999999999' });

      expect(service.syncingId()).toBeNull();
      expect(service.repositories()[0].lastSyncedCommit).toBe('9999999999999999');
    });

    it('keeps the backend message and the repository id when a sync fails', () => {
      create();
      loadList([GITHUB_REPO]);

      service.sync('r-github');
      const { body, init } = apiError('Git fetch failed: could not resolve host', 502);
      http.expectOne('/api/v1/repositories/r-github/sync').flush(body, init);

      expect(service.syncingId()).toBeNull();
      expect(service.syncError()).toEqual({
        repositoryId: 'r-github',
        message: 'Git fetch failed: could not resolve host',
      });
    });

    it('clears a previous sync error when syncing again', () => {
      create();
      loadList([GITHUB_REPO]);
      service.sync('r-github');
      const { body, init } = apiError('boom', 502);
      http.expectOne('/api/v1/repositories/r-github/sync').flush(body, init);

      service.sync('r-github');

      expect(service.syncError()).toBeNull();
      http.expectOne('/api/v1/repositories/r-github/sync').flush(GITHUB_REPO);
    });
  });

  describe('indexing', () => {
    it('POSTs {repositoryId}, then re-fetches the repository so the stale flag clears', () => {
      create();
      loadList([GITHUB_REPO]);

      service.index('r-github');
      expect(service.indexingId()).toBe('r-github');
      expect(service.isIndexing()).toBeTrue();

      const indexReq = http.expectOne('/api/v1/indexing');
      expect(indexReq.request.method).toBe('POST');
      expect(indexReq.request.body).toEqual({ repositoryId: 'r-github' });
      indexReq.flush({ repositoryRoot: GITHUB_REPO.localPath, chunksIndexed: 412 });

      // Still "indexing" until the refreshed row lands — no flicker of a stale banner.
      expect(service.isIndexing()).toBeTrue();
      http
        .expectOne('/api/v1/repositories/r-github')
        .flush({ ...GITHUB_REPO, lastIndexedAt: '2026-09-19T12:00:00Z', indexStale: false });

      expect(service.isIndexing()).toBeFalse();
      expect(service.indexResult()).toEqual({ repositoryId: 'r-github', chunksIndexed: 412 });
      expect(service.repositories()[0].indexStale).toBeFalse();
    });

    it('still clears the stale flag locally if the post-index re-fetch fails', () => {
      create();
      loadList([GITHUB_REPO]);

      service.index('r-github');
      http.expectOne('/api/v1/indexing').flush({ repositoryRoot: 'x', chunksIndexed: 3 });
      http
        .expectOne('/api/v1/repositories/r-github')
        .flush(null, { status: 500, statusText: 'Server Error' });

      expect(service.isIndexing()).toBeFalse();
      expect(service.repositories()[0].indexStale).toBeFalse();
      expect(service.indexError()).toBeNull();
    });

    it('surfaces the backend message when indexing fails and leaves the repository stale', () => {
      create();
      loadList([GITHUB_REPO]);

      service.index('r-github');
      const { body, init } = apiError('Embedding model is not reachable at localhost:11434', 502);
      http.expectOne('/api/v1/indexing').flush(body, init);

      expect(service.isIndexing()).toBeFalse();
      expect(service.indexError()).toEqual({
        repositoryId: 'r-github',
        message: 'Embedding model is not reachable at localhost:11434',
      });
      expect(service.repositories()[0].indexStale).toBeTrue();
    });

    it('refuses to start a second index while one is running', () => {
      create();
      loadList([GITHUB_REPO, LOCAL_REPO]);

      service.index('r-github');
      service.index('r-local');

      expect(http.match('/api/v1/indexing').length).toBe(1);
      expect(service.indexingId()).toBe('r-github');
    });

    it('does not sync a repository while it is being indexed, or index while it is syncing', () => {
      create();
      loadList([GITHUB_REPO]);

      service.index('r-github');
      service.sync('r-github');
      expect(http.match('/api/v1/repositories/r-github/sync').length).toBe(0);
      http.expectOne('/api/v1/indexing').flush({ repositoryRoot: 'x', chunksIndexed: 1 });
      http.expectOne('/api/v1/repositories/r-github').flush(GITHUB_REPO);

      service.sync('r-github');
      service.index('r-github');
      expect(http.match('/api/v1/indexing').length).toBe(0);
      http.expectOne('/api/v1/repositories/r-github/sync').flush(GITHUB_REPO);
    });

    it('dismissIndexOutcome clears both the result and the error', () => {
      create();
      loadList([GITHUB_REPO]);
      service.index('r-github');
      const { body, init } = apiError('nope', 502);
      http.expectOne('/api/v1/indexing').flush(body, init);

      service.dismissIndexOutcome();

      expect(service.indexError()).toBeNull();
      expect(service.indexResult()).toBeNull();
    });
  });
});
